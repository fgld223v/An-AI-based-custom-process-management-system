package com.aiflow.service.impl;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessInstanceService;
import com.aiflow.service.RuleEvaluatorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword) {
        List<ProcessInstance> instances = processInstanceRepository
                .listInstances(templateId, normalize(status), normalize(keyword));

        // 非超管只能看到自己发起的实例
        if (!SecurityUtils.isSuperAdmin()) {
            Long currentUserId = SecurityUtils.currentUserId();
            instances = instances.stream()
                    .filter(i -> i.getApplicantId() != null && i.getApplicantId().equals(currentUserId))
                    .toList();
        }

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

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = ProcessInstance.builder()
                .instanceCode("PI_" + now.format(CODE_TIME_FORMATTER))
                .templateId(template.getId())
                .formId(request.getFormId())
                .applicantId(1L)
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
        if (STATUS_SUBMITTED.equals(instance.getStatus()) || STATUS_RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException("当前实例已提交或已启动流程，仅支持查看，不支持继续保存。");
        }
        if (!request.getTemplateId().equals(instance.getTemplateId())) {
            throw new IllegalArgumentException("templateId does not match current process instance");
        }

        LocalDateTime now = LocalDateTime.now();
        FormSubmission submission = saveSubmission(instance, request.getTemplateId(), request.getNodeKey(),
                request.getNodeName(), request.getBusinessType(), request.getFormId(), request.getFormDataJson(),
                normalizeStatus(request.getStatus(), STATUS_DRAFT), now);

        instance.setFormId(request.getFormId());
        instance.setFormData(request.getFormDataJson());
        instance.setCurrentNodeKey(request.getNodeKey());
        instance.setCurrentNodeName(request.getNodeName());
        instance.setCurrentBusinessType(request.getBusinessType());
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);

        return toDto(submission);
    }

    @Override
    public ProcessInstanceDTO submitInstance(Long id) {
        // 委托 FlowableRuntimeService 完成全部校验与启动
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

    /**
     * 从 formBindConfig 中根据 taskDefinitionKey 解析 formId。
     * formBindConfig 格式：{"StartEvent_1":{"formId":1},"UserTask_ManagerApprove":{"formId":2}}
     */
    private Long resolveFormId(String formBindConfigJson, String taskDefinitionKey) {
        if (!hasText(formBindConfigJson)) {
            return null;
        }
        try {
            Map<String, Map<String, Object>> bindConfig = objectMapper.readValue(
                    formBindConfigJson,
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
            Map<String, Object> nodeBinding = bindConfig.get(taskDefinitionKey);
            if (nodeBinding != null && nodeBinding.get("formId") != null) {
                Object formIdObj = nodeBinding.get("formId");
                if (formIdObj instanceof Number) {
                    return ((Number) formIdObj).longValue();
                }
            }
            return null;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "解析 formBindConfig 失败，taskDefinitionKey: " + taskDefinitionKey, ex);
        }
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
        return processInstanceRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));
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
}
