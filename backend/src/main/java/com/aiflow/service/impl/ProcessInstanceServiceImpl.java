package com.aiflow.service.impl;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.ProcessDiagramDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.dto.TimelineDTO;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessInstanceService;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.ProcessTimelineService;
import com.aiflow.service.RuleEvaluatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 流程实例服务实现。
 *
 * <p>核心职责：流程实例（申请单）的全生命周期管理，包括：</p>
 * <ul>
 *   <li>创建草稿 — 用户填写表单后保存为 draft 状态</li>
 *   <li>节点表单保存 — 支持 draft 阶段和 running 阶段的表单数据保存</li>
 *   <li>提交启动 — 调用 FlowableRuntimeService 启动 Flowable 流程实例</li>
 *   <li>运行时状态查询 — 通过 Flowable TaskQuery 查询当前任务和表单</li>
 *   <li>表单提交记录 — upsert 方式按 nodeKey 唯一保存 FormSubmission</li>
 * </ul>
 *
 * <p>权限控制：仅申请人可查看/操作自己的流程实例；
 * 当前审批人也可查看该实例的表单数据（用于审批参考）。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_RUNNING = "running";
    /** 实例编码时间戳格式：yyyyMMddHHmmssSSS（精确到毫秒） */
    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ProcessAuthorizationService processAuthorizationService;
    private final FlowableRuntimeService flowableRuntimeService;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final FormBindConfigParser formBindConfigParser;
    private final ProcessTimelineService processTimelineService;

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword) {
        List<ProcessInstance> instances = processInstanceRepository
                .listInstances(templateId, normalize(status), normalize(keyword));

        // “我的申请”接口只返回当前用户发起的实例。
        Long currentUserId = requireCurrentUserId();
        instances = instances.stream()
                .filter(i -> i.getApplicantId() != null && i.getApplicantId().equals(currentUserId))
                .toList();

        return instances.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstanceDTO getInstance(Long id) {
        return toDto(getRequiredInstance(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessDiagramDTO getDiagram(Long processInstanceId) {
        ProcessInstance instance = getRequiredInstance(processInstanceId);
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("process template not found"));
        return ProcessDiagramDTO.builder()
                .templateId(template.getId())
                .templateName(template.getTemplateName())
                .bpmnXml(template.getBpmnXml())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormSubmissionDTO> listSubmissions(Long processInstanceId) {
        requireId(processInstanceId, "processInstanceId must not be null");
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(processInstanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));

        // 允许申请人 或 当前任务的处理人（审批人）查看表单数据
        Long currentUserId = SecurityUtils.currentUserId();
        boolean isApplicant = currentUserId != null
                && instance.getApplicantId() != null
                && instance.getApplicantId().equals(currentUserId);
        boolean isCurrentApprover = currentUserId != null
                && isCurrentTaskAssignee(instance, currentUserId);

        if (!isApplicant && !isCurrentApprover) {
            throw new AccessDeniedException("no permission to access this process instance");
        }

        return formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(processInstanceId, 0)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** 检查当前用户是否是该流程实例的当前任务处理人 */
    private boolean isCurrentTaskAssignee(ProcessInstance instance, Long currentUserId) {
        if (!hasText(instance.getFlowableProcessInstanceId())) return false;
        try {
            String assignee = String.valueOf(currentUserId);
            long count = taskService.createTaskQuery()
                    .processInstanceId(instance.getFlowableProcessInstanceId())
                    .taskAssignee(assignee)
                    .active()
                    .count();
            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建流程实例草稿 — 用户发起新申请的第一步。
     * 创建 ProcessInstance（status=draft）并保存起始节点的表单提交记录。
     */
    @Override
    public ProcessInstanceDTO createDraft(StartProcessPreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireId(request.getTemplateId(), "templateId must not be null");
        requireId(request.getFormId(), "formId must not be null");
        requireText(request.getInstanceTitle(), "instanceTitle must not be blank");
        requireText(request.getStartNodeKey(), "startNodeKey must not be blank");

        ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(request.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("process template not found"));
        processAuthorizationService.assertCanStart(template);

        Long currentUserId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = ProcessInstance.builder()
                .instanceCode("PI_" + now.format(CODE_TIME_FORMATTER))
                .templateId(template.getId())
                .formId(request.getFormId())
                .applicantId(currentUserId)
                .bizTypeId(template.getBizTypeId())
                .title(request.getInstanceTitle().trim())
                .status(STATUS_DRAFT)
                .formData(request.getFormDataJson())
                .currentNodeKey(request.getStartNodeKey())
                .currentNodeName(request.getStartNodeName())
                .currentBusinessType(request.getBusinessType())
                .flowableDefinitionId(template.getFlowableProcessDefinitionId())
                .flowableDeploymentId(template.getFlowableDeploymentId())
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        ProcessInstance saved = processInstanceRepository.save(instance);

        saveSubmission(saved, template.getId(), request.getStartNodeKey(), request.getStartNodeName(),
                request.getBusinessType(), request.getFormId(), request.getFormDataJson(), STATUS_DRAFT, now);

        return toDto(saved);
    }

    /**
     * 保存节点表单数据 — 支持 draft 和 running 两种状态。
     *
     * <p>状态约束：</p>
     * <ul>
     *   <li>running 状态下仅允许保存 form_fill 节点的表单（流程中间节点的数据采集）</li>
     *   <li>start 节点和其他类型仅允许在 draft 阶段保存</li>
     *   <li>running 状态下表单保存但不更新实例元数据（由 Flowable 任务完成时更新）</li>
     * </ul>
     * <p>保存策略：按 nodeKey 唯一 upsert（已存在则更新，不存在则创建）。</p>
     */
    @Override
    public FormSubmissionDTO saveNodeForm(SaveNodeFormRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireId(request.getProcessInstanceId(), "processInstanceId must not be null");
        requireId(request.getTemplateId(), "templateId must not be null");
        requireId(request.getFormId(), "formId must not be null");
        requireText(request.getNodeKey(), "nodeKey must not be blank");

        ProcessInstance instance = getRequiredInstance(request.getProcessInstanceId());
        boolean isRunning = STATUS_SUBMITTED.equals(instance.getStatus()) || STATUS_RUNNING.equals(instance.getStatus());

        // running 状态下只允许保存 form_fill 节点的表单（流程中间节点的数据采集），
        // start 节点和其他类型仅允许在 draft 阶段保存。
        if (isRunning && !"form_fill".equals(request.getBusinessType())) {
            throw new IllegalStateException("当前实例已启动流程，仅支持填写表单节点（form_fill），不支持修改启动配置。");
        }
        if (!request.getTemplateId().equals(instance.getTemplateId())) {
            throw new IllegalArgumentException("templateId does not match current process instance");
        }

        LocalDateTime now = LocalDateTime.now();
        // running 状态下表单数据标记为 submitted（已提交），draft 阶段仍保持 draft
        String submissionStatus = isRunning ? STATUS_SUBMITTED : normalizeStatus(request.getStatus(), STATUS_DRAFT);
        FormSubmission submission = saveSubmission(instance, request.getTemplateId(), request.getNodeKey(),
                request.getNodeName(), request.getBusinessType(), request.getFormId(), request.getFormDataJson(),
                submissionStatus, now);

        // 仅 draft 阶段更新实例元数据，running 阶段不覆盖（由 Flowable 任务完成时更新）
        if (!isRunning) {
            instance.setFormId(request.getFormId());
            instance.setFormData(request.getFormDataJson());
            instance.setCurrentNodeKey(request.getNodeKey());
            instance.setCurrentNodeName(request.getNodeName());
            instance.setCurrentBusinessType(request.getBusinessType());
            instance.setUpdatedAt(now);
            processInstanceRepository.save(instance);
        }

        return toDto(submission);
    }

    /**
     * 提交（启动）流程实例 — 从 draft 状态转为 running。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>委托 FlowableRuntimeService 完成全部校验与 Flowable 流程启动</li>
     *   <li>将所有 FormSubmission 状态更新为 submitted</li>
     *   <li>检查 Flowable 流程是否已立即结束（如排他网关条件直接路由到 EndEvent）</li>
     *   <li>若无活跃任务则标记为 completed；否则调用规则引擎评估自动完成</li>
     * </ol>
     */
    @Override
    public ProcessInstanceDTO submitInstance(Long id) {
        // 委托 FlowableRuntimeService 完成全部校验与启动
        getRequiredInstance(id);
        flowableRuntimeService.startProcess(id);

        // 回写后重新查询，获取最新的 Flowable 关联信息
        ProcessInstance instance = getRequiredInstance(id);

        // 将所有 FormSubmission 状态更新为 submitted
        LocalDateTime now = LocalDateTime.now();
        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(id, 0);
        for (FormSubmission submission : submissions) {
            submission.setStatus(STATUS_SUBMITTED);
            submission.setUpdatedAt(now);
        }
        formSubmissionRepository.saveAll(submissions);

        // 检查 Flowable 流程是否已立即结束（如排他网关条件直接路由到 EndEvent）
        if (hasText(instance.getFlowableProcessInstanceId())) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(instance.getFlowableProcessInstanceId())
                    .active()
                    .orderByTaskCreateTime().asc()
                    .list();
            if (activeTasks.isEmpty()) {
                instance.setStatus("completed");
                instance.setEndedAt(now);
                instance.setCurrentNodeKey(null);
                instance.setCurrentNodeName(null);
                instance.setCurrentBusinessType(null);
                instance.setUpdatedAt(now);
                processInstanceRepository.save(instance);
            } else {
                ruleEvaluatorService.evaluateAndAutoComplete(instance);
            }
        }

        instance = getRequiredInstance(id);
        return toDto(instance);
    }

    @Override
    @Transactional(readOnly = true)
    public RuntimeStateDTO getRuntimeState(Long processInstanceId) {
        // 1. 查询业务 ProcessInstance
        ProcessInstance instance = getRequiredInstance(processInstanceId);

        String flowableProcessInstanceId = instance.getFlowableProcessInstanceId();
        if (!hasText(flowableProcessInstanceId)) {
            throw new IllegalStateException("流程实例尚未启动，无法获取运行时状态。");
        }

        // 2. 使用 Flowable TaskQuery 查询当前任务
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(flowableProcessInstanceId)
                .active()
                .orderByTaskCreateTime().asc()
                .list();

        if (activeTasks.isEmpty()) {
            // 流程已结束 — 返回 completed=true 而非抛异常
            return RuntimeStateDTO.builder()
                    .businessInstanceId(instance.getId())
                    .flowableProcessInstanceId(flowableProcessInstanceId)
                    .completed(true)
                    .build();
        }
        Task task = activeTasks.get(0);

        // 3. 查询 ProcessTemplate，解析 formBindConfig 获取当前节点的 formId
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在。"));

        Long formId = resolveFormId(template.getFormBindConfig(), task.getTaskDefinitionKey());

        return RuntimeStateDTO.builder()
                .businessInstanceId(instance.getId())
                .flowableProcessInstanceId(flowableProcessInstanceId)
                .currentTaskKey(task.getTaskDefinitionKey())
                .currentTaskName(task.getName())
                .formId(formId)
                .completed(false)
                .build();
    }

    private Long resolveFormId(String formBindConfigJson, String taskDefinitionKey) {
        return formBindConfigParser.resolveFormId(formBindConfigJson, taskDefinitionKey);
    }

    private FormSubmission saveSubmission(ProcessInstance instance,
                                          Long templateId,
                                          String nodeKey,
                                          String nodeName,
                                          String businessType,
                                          Long formId,
                                          String formDataJson,
                                          String status,
                                          LocalDateTime now) {
        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instance.getId(), nodeKey, 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instance.getId())
                        .templateId(templateId)
                        .nodeKey(nodeKey)
                        .createdAt(now)
                        .deleted(0)
                        .build());

        submission.setTemplateId(templateId);
        submission.setNodeName(nodeName);
        submission.setBusinessType(businessType);
        submission.setFormId(formId);
        submission.setFormDataJson(formDataJson);
        submission.setStatus(status);
        submission.setUpdatedAt(now);
        return formSubmissionRepository.save(submission);
    }

    private ProcessInstance getRequiredInstance(Long id) {
        requireId(id, "id must not be null");
        ProcessInstance instance = processInstanceRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));
        ensureCurrentUserApplicant(instance);
        return instance;
    }

    private Long requireCurrentUserId() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("current user is required");
        }
        return currentUserId;
    }

    private void ensureCurrentUserApplicant(ProcessInstance instance) {
        Long currentUserId = requireCurrentUserId();
        if (instance.getApplicantId() == null || !instance.getApplicantId().equals(currentUserId)) {
            throw new AccessDeniedException("no permission to access this process instance");
        }
    }

    private ProcessInstanceDTO toDto(ProcessInstance entity) {
        return ProcessInstanceDTO.builder()
                .id(entity.getId())
                .templateId(entity.getTemplateId())
                .instanceCode(entity.getInstanceCode())
                .instanceTitle(entity.getTitle())
                .status(entity.getStatus())
                .currentNodeKey(entity.getCurrentNodeKey())
                .currentNodeName(entity.getCurrentNodeName())
                .currentBusinessType(entity.getCurrentBusinessType())
                .flowableProcessInstanceId(entity.getFlowableProcessInstanceId())
                .flowableDefinitionId(entity.getFlowableDefinitionId())
                .flowableDeploymentId(entity.getFlowableDeploymentId())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private FormSubmissionDTO toDto(FormSubmission entity) {
        return FormSubmissionDTO.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .templateId(entity.getTemplateId())
                .nodeKey(entity.getNodeKey())
                .nodeName(entity.getNodeName())
                .businessType(entity.getBusinessType())
                .formId(entity.getFormId())
                .formDataJson(entity.getFormDataJson())
                .status(entity.getStatus())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private String normalizeStatus(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public TimelineDTO getTimeline(Long processInstanceId) {
        getRequiredInstance(processInstanceId);
        return processTimelineService.buildTimeline(processInstanceId);
    }
}
