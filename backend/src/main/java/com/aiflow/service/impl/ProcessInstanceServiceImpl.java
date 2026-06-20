package com.aiflow.service.impl;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
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
import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.service.ApproverResolverService;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessInstanceService;
import com.aiflow.service.RuleEvaluatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_RUNNING = "running";
    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FlowableRuntimeService flowableRuntimeService;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final ApproverResolverService approverResolverService;
    private final SysUserMapper sysUserMapper;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final FormBindConfigParser formBindConfigParser;
    private final EntityManager entityManager;
    private final NodeConfigParser nodeConfigParser;

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
    public List<FormSubmissionDTO> listSubmissions(Long processInstanceId) {
        getRequiredInstance(processInstanceId);
        return formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(processInstanceId, 0)
                .stream()
                .map(this::toDto)
                .toList();
    }

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
            Task activeTask = taskService.createTaskQuery()
                    .processInstanceId(instance.getFlowableProcessInstanceId())
                    .singleResult();
            if (activeTask == null) {
                instance.setStatus("completed");
                instance.setEndedAt(now);
                instance.setCurrentNodeKey(null);
                instance.setCurrentNodeName(null);
                instance.setCurrentBusinessType(null);
                instance.setUpdatedAt(now);
                processInstanceRepository.save(instance);
            } else {
                ruleEvaluatorService.evaluateAndAutoComplete(instance);

                // 如果第一个任务是审批节点（无 form_fill 前置），立即分配审批人
                if (activeTask.getAssignee() == null) {
                    ProcessTemplate tpl = processTemplateRepository
                            .findByIdAndDeleted(instance.getTemplateId(), 0).orElse(null);
                    if (tpl != null) {
                        String businessType = nodeConfigParser.getStringField(
                                tpl.getNodeConfig(), activeTask.getTaskDefinitionKey(), "businessType");
                        if ("approval".equals(businessType)) {
                            assignFirstApprovalTask(activeTask, tpl, instance);
                        }
                    }
                }
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
        Task task = taskService.createTaskQuery()
                .processInstanceId(flowableProcessInstanceId)
                .singleResult();

        if (task == null) {
            // 流程已结束 — 返回 completed=true 而非抛异常
            return RuntimeStateDTO.builder()
                    .businessInstanceId(instance.getId())
                    .flowableProcessInstanceId(flowableProcessInstanceId)
                    .completed(true)
                    .build();
        }

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

    /**
     * 流程启动后第一个任务即为审批节点时，解析并分配审批人。
     */
    private void assignFirstApprovalTask(Task task, ProcessTemplate template, ProcessInstance instance) {
        String nodeKey = task.getTaskDefinitionKey();
        String strategy = nodeConfigParser.getStringField(template.getNodeConfig(), nodeKey, "assignStrategy");
        String assignValue = nodeConfigParser.getStringField(template.getNodeConfig(), nodeKey, "assignValue");
        if (strategy == null || strategy.isBlank()) {
            strategy = "DEPARTMENT_MANAGER"; // 兜底
        }
        List<Long> approverIds = approverResolverService.resolveApprovers(
                instance.getId(), nodeKey, strategy, assignValue);
        if (!approverIds.isEmpty()) {
            UserEntity approver = sysUserMapper.selectById(approverIds.get(0));
            if (approver != null) {
                taskService.setAssignee(task.getId(), String.valueOf(approver.getId()));
                log.info("启动时分配审批人：task={}, assignee={}(id={})",
                        task.getName(), approver.getNickname(), approver.getId());
            }
        }
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
        ProcessInstance instance = getRequiredInstance(processInstanceId);
        List<TimelineDTO.TimelineNode> nodes = new ArrayList<>();

        // 1. 发起节点
        nodes.add(TimelineDTO.TimelineNode.builder()
                .type("start")
                .nodeName("发起申请")
                .operatorName("申请人")
                .time(fmt(instance.getStartedAt() != null ? instance.getStartedAt() : instance.getCreatedAt()))
                .duration(null)
                .action("发起流程")
                .comment(null)
                .build());

        LocalDateTime previousTime = instance.getStartedAt() != null
                ? instance.getStartedAt() : instance.getCreatedAt();

        // 2. 审批节点
        @SuppressWarnings("unchecked")
        List<Object[]> records = entityManager
                .createNativeQuery("""
                        SELECT ar.node_key, ar.action, ar.comment_text, ar.operated_at,
                               COALESCE(su.nickname, CONCAT('用户#', ar.approver_id)) AS approver_name
                        FROM approval_record ar
                        LEFT JOIN sys_user su ON ar.approver_id = su.id
                        WHERE ar.instance_id = :instanceId
                        ORDER BY ar.operated_at ASC
                        """)
                .setParameter("instanceId", processInstanceId)
                .getResultList();

        String actionLabel;
        for (Object[] r : records) {
            String action = (String) r[1];
            switch (action) {
                case "approve": actionLabel = "通过"; break;
                case "reject": actionLabel = "驳回"; break;
                case "supplement": actionLabel = "补充材料"; break;
                case "delegate": actionLabel = "转交"; break;
                case "transfer": actionLabel = "移交"; break;
                default: actionLabel = action;
            }

            LocalDateTime operatedAt = ((Timestamp) r[3]).toLocalDateTime();
            String duration = calcDuration(previousTime, operatedAt);
            previousTime = operatedAt;

            nodes.add(TimelineDTO.TimelineNode.builder()
                    .type("approval")
                    .nodeName((String) r[0])
                    .operatorName((String) r[4])
                    .time(fmt(operatedAt))
                    .duration(duration)
                    .action(actionLabel)
                    .comment((String) r[2])
                    .build());
        }

        // 3. 结束节点
        if (instance.getEndedAt() != null) {
            nodes.add(TimelineDTO.TimelineNode.builder()
                    .type("end")
                    .nodeName("流程完成")
                    .operatorName("系统")
                    .time(fmt(instance.getEndedAt()))
                    .duration(calcDuration(previousTime, instance.getEndedAt()))
                    .action("流程结束".equals(instance.getStatus()) || "completed".equals(instance.getStatus()) ? "办结" : "终止")
                    .comment(null)
                    .build());
        }

        return TimelineDTO.builder().nodes(nodes).build();
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
    }

    private String calcDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        Duration d = Duration.between(from, to);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 0) {
            return minutes > 0 ? hours + "h" + minutes + "m" : hours + "h";
        }
        return minutes + "m";
    }
}
