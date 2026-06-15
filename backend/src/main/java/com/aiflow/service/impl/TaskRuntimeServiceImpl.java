package com.aiflow.service.impl;

import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.RuleEvaluatorService;
import com.aiflow.service.TaskRuntimeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务运行时服务实现 — 严格遵循审批链路。
 *
 * <p>审批链路（不可跳跃）：</p>
 * <ol>
 *   <li>查询 Flowable Task</li>
 *   <li>查询业务 ProcessInstance，校验状态</li>
 *   <li>保存 FormSubmission（历史表单永久保留，禁止删除）</li>
 *   <li>更新 Flowable Variables（合并 allFormData）</li>
 *   <li>TaskService.complete(taskId, variables)</li>
 *   <li>查询下一任务</li>
 *   <li>刷新 ProcessInstance 状态（currentNodeKey/Name/BusinessType 或标记 completed）</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskRuntimeServiceImpl implements TaskRuntimeService {

    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_SUBMITTED = "submitted";

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final ObjectMapper objectMapper;

    @Override
    public TaskDTO completeTask(String taskId, TaskCompleteRequest request) {
        // ================================================================
        // 1. 查询 Flowable Task
        // ================================================================
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已完成。");
        }

        // ================================================================
        // 2. 查询业务 ProcessInstance
        // ================================================================
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(request.getInstanceId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));

        if (!STATUS_RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException(
                    "当前实例状态为【" + instance.getStatus() + "】，仅 running 状态可完成任务。");
        }

        if (!task.getProcessInstanceId().equals(instance.getFlowableProcessInstanceId())) {
            throw new IllegalArgumentException("任务不属于该流程实例。");
        }

        // ================================================================
        // 3. 保存 FormSubmission（历史保留 — upsert by processInstanceId + nodeKey）
        // ================================================================
        LocalDateTime now = LocalDateTime.now();
        String formDataJson = toJson(request.getFormData());
        String businessType = resolveBusinessType(instance.getTemplateId(), request.getNodeKey());

        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instance.getId(), request.getNodeKey(), 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instance.getId())
                        .templateId(instance.getTemplateId())
                        .nodeKey(request.getNodeKey())
                        .createdAt(now)
                        .deleted(0)
                        .build());

        submission.setNodeName(task.getName());
        submission.setBusinessType(businessType);
        submission.setFormId(request.getFormId());
        submission.setFormDataJson(formDataJson);
        submission.setStatus(STATUS_SUBMITTED);
        submission.setUpdatedAt(now);
        formSubmissionRepository.save(submission);

        // ================================================================
        // 4. 更新 Flowable Variables（合并 allFormData）
        // ================================================================
        Map<String, Object> variables = new HashMap<>();
        try {
            Map<String, Object> existingVars = runtimeService.getVariables(task.getProcessInstanceId());
            @SuppressWarnings("unchecked")
            Map<String, Object> allFormData = (Map<String, Object>) existingVars.getOrDefault(
                    "allFormData", new HashMap<>());
            allFormData.put(request.getNodeKey(), request.getFormData());
            variables.put("allFormData", allFormData);
        } catch (Exception ex) {
            // 首次添加时兜底
            Map<String, Object> allFormData = new HashMap<>();
            allFormData.put(request.getNodeKey(), request.getFormData());
            variables.put("allFormData", allFormData);
        }

        // ================================================================
        // 5. TaskService.complete(taskId, variables)
        // ================================================================
        try {
            taskService.complete(taskId, variables);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "任务完成失败：" + safeMessage(ex), ex);
        }

        // ================================================================
        // 6-7. 查询下一任务并执行审批规则自动流转
        // ================================================================
        Task nextTask = ruleEvaluatorService.evaluateAndAutoComplete(instance);

        // 构建返回结果：下一任务或 null
        if (nextTask != null) {
            return toTaskDTO(nextTask, instance);
        }
        return null;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private TaskDTO toTaskDTO(Task task, ProcessInstance instance) {
        Long formId = resolveFormId(instance.getTemplateId(), task.getTaskDefinitionKey());
        return TaskDTO.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .flowableProcessInstanceId(task.getProcessInstanceId())
                .businessInstanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .assignee(task.getAssignee())
                .createTime(task.getCreateTime() != null
                        ? task.getCreateTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .dueDate(task.getDueDate() != null
                        ? task.getDueDate().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .status("active")
                .formId(formId)
                .build();
    }

    private Long resolveFormId(Long templateId, String taskDefinitionKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getFormBindConfig())) {
            return null;
        }
        try {
            Map<String, Map<String, Object>> bindConfig = objectMapper.readValue(
                    template.getFormBindConfig(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
            Map<String, Object> nodeBinding = bindConfig.get(taskDefinitionKey);
            if (nodeBinding != null && nodeBinding.get("formId") != null) {
                Object formIdObj = nodeBinding.get("formId");
                if (formIdObj instanceof Number) {
                    return ((Number) formIdObj).longValue();
                }
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }

    /**
     * 从模板 nodeConfig 中解析 businessType。
     * nodeConfig 格式：
     * [{"nodeKey":"StartEvent_1","nodeName":"开始","businessType":"start"},
     *  {"nodeKey":"UserTask_ManagerApprove","nodeName":"经理审批","businessType":"approval"}]
     */
    private String resolveBusinessType(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return null;
        }
        try {
            List<Map<String, Object>> nodes = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            for (Map<String, Object> node : nodes) {
                if (nodeKey.equals(node.get("nodeKey"))) {
                    Object bt = node.get("businessType");
                    return bt != null ? bt.toString() : null;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new IllegalStateException("表单数据序列化失败。", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
