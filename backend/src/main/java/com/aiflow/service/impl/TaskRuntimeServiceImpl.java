package com.aiflow.service.impl;

import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;
import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApproverResolverService;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ApprovalVariableService;
import com.aiflow.service.RuleEvaluatorService;
import com.aiflow.service.TaskRuntimeService;
import com.aiflow.service.TaskAuthorizationService;
import com.aiflow.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final ApproverResolverService approverResolverService;
    private final SysUserMapper sysUserMapper;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final ObjectMapper objectMapper;
    private final NodeConfigParser nodeConfigParser;
    private final TaskAuthorizationService taskAuthorizationService;
    private final ApprovalVariableService approvalVariableService;
    private final ApprovalRecordService approvalRecordService;

    @Override
    public TaskDTO completeTask(String taskId, TaskCompleteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getInstanceId() == null) {
            throw new IllegalArgumentException("instanceId must not be null");
        }
        if (!hasText(request.getNodeKey())) {
            throw new IllegalArgumentException("nodeKey must not be blank");
        }
        // ================================================================
        // 1. 查询 Flowable Task
        // ================================================================
        Task queriedTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (queriedTask == null) {
            throw new IllegalArgumentException("任务不存在或已完成。");
        }
        taskAuthorizationService.assertCanView(queriedTask);

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

        if (!queriedTask.getProcessInstanceId().equals(instance.getFlowableProcessInstanceId())) {
            throw new IllegalArgumentException("任务不属于该流程实例。");
        }
        if (!queriedTask.getTaskDefinitionKey().equals(request.getNodeKey())) {
            throw new IllegalArgumentException("nodeKey 与当前任务节点不一致。");
        }

        String trustedNodeKey = queriedTask.getTaskDefinitionKey();
        Long boundFormId = resolveFormId(instance.getTemplateId(), trustedNodeKey);
        if (request.getFormId() != null && !request.getFormId().equals(boundFormId)) {
            throw new IllegalArgumentException("formId 与当前任务绑定表单不一致。");
        }
        Task task = taskAuthorizationService.requireOperableTask(queriedTask);

        // ================================================================
        // 3. 保存 FormSubmission（历史保留 — upsert by processInstanceId + nodeKey）
        // ================================================================
        LocalDateTime now = LocalDateTime.now();
        String formDataJson = toJson(request.getFormData());
        String businessType = resolveBusinessType(instance.getTemplateId(), trustedNodeKey);

        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instance.getId(), trustedNodeKey, 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instance.getId())
                        .templateId(instance.getTemplateId())
                        .nodeKey(trustedNodeKey)
                        .createdAt(now)
                        .deleted(0)
                        .build());

        submission.setNodeName(task.getName());
        submission.setBusinessType(businessType);
        submission.setFormId(boundFormId);
        submission.setFormDataJson(formDataJson);
        submission.setStatus(STATUS_SUBMITTED);
        submission.setUpdatedAt(now);
        formSubmissionRepository.save(submission);

        // ================================================================
        // 4. 更新 Flowable Variables（合并 allFormData）
        // ================================================================
        boolean approvalNode = "approval".equalsIgnoreCase(businessType);
        String approvalComment = null;
        Map<String, Object> variables;
        if (approvalNode) {
            String approvalResult = mapText(request.getFormData(), "approvalResult");
            if ("reject".equalsIgnoreCase(approvalResult)) {
                throw new IllegalArgumentException("驳回审批必须使用 reject 接口。");
            }
            approvalComment = firstMapText(request.getFormData(), "approvalComment", "approvalOpinion", "comment");
            variables = approvalVariableService.build(
                    task.getProcessInstanceId(), trustedNodeKey, approvalResult,
                    approvalComment, false, null, now);
            if (hasText(approvalComment)) {
                taskService.addComment(taskId, task.getProcessInstanceId(), approvalComment);
            }
        } else {
            variables = mergeNodeFormVariables(task.getProcessInstanceId(), trustedNodeKey, request.getFormData());
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
        if (approvalNode) {
            approvalRecordService.record(instance.getId(), taskId, trustedNodeKey,
                    SecurityUtils.currentUserId(), "approve", approvalComment, now);
        }

        // ================================================================
        // 6-7. 查询下一任务并执行审批规则自动流转
        // ================================================================
        // 判断当前任务是否为多实例（会签/或签）
        boolean isMultiInstance = isMultiInstanceNode(
                instance.getTemplateId(), task.getTaskDefinitionKey());
        Task nextTask = ruleEvaluatorService.evaluateAndAutoComplete(instance);

        // ================================================================
        // 8. 审批人解析与分配（下一个 UserTask 自动分配 assignee）
        // ================================================================
        if (nextTask != null) {
            // 多实例场景：如果下一任务是同节点多实例的另一个副本，
            // 跳过手动分配（由 MultiInstanceAssigneeListener 处理）
            boolean isNextTaskSameMultiInstance = isMultiInstance
                    && nextTask.getTaskDefinitionKey().equals(task.getTaskDefinitionKey());

            if (!isNextTaskSameMultiInstance && !hasText(nextTask.getAssignee())) {
                String strategy = resolveAssignStrategy(instance.getTemplateId(), nextTask.getTaskDefinitionKey());
                String assignValue = resolveAssignValue(instance.getTemplateId(), nextTask.getTaskDefinitionKey());

                // 检查下一个节点是否也是多实例（会签/或签）
                boolean nextIsMultiInstance = isMultiInstanceNode(
                        instance.getTemplateId(), nextTask.getTaskDefinitionKey());

                if (nextIsMultiInstance) {
                    // 下一个节点是多实例 → 由 MultiInstanceAssigneeListener 分配
                    // 不在此处手动分配
                } else {
                    // 对于审批节点，如果未配置 assignStrategy，默认使用 DEPARTMENT_MANAGER
                    if (strategy == null || strategy.isBlank()) {
                        String nextBusinessType = resolveBusinessType(
                                instance.getTemplateId(), nextTask.getTaskDefinitionKey());
                        if ("approval".equals(nextBusinessType)) {
                            strategy = "DEPARTMENT_MANAGER";
                            log.info("审批节点 {} 未配置审批策略，默认使用 DEPARTMENT_MANAGER",
                                    nextTask.getTaskDefinitionKey());
                        }
                    }

                    if (strategy != null && !strategy.isBlank()) {
                        List<Long> approverIds = approverResolverService.resolveApprovers(
                                instance.getId(), nextTask.getTaskDefinitionKey(), strategy, assignValue);
                        if (!approverIds.isEmpty()) {
                            UserEntity approver = sysUserMapper.selectById(approverIds.get(0));
                            if (approver != null) {
                                taskService.setAssignee(nextTask.getId(), String.valueOf(approver.getId()));
                                log.info("已为审批任务 {} 分配审批人: {} (id={})",
                                        nextTask.getName(), approver.getNickname(), approver.getId());
                            }
                        } else {
                            log.warn("审批节点 {} 的审批人解析为空，流程可能卡死",
                                    nextTask.getTaskDefinitionKey());
                        }
                    }
                }
            }
        }

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
        if (template == null) {
            return null;
        }
        if (hasText(template.getFormBindConfig())) {
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
                // Invalid binding is rejected at publish time; use the template fallback here.
            }
        }
        return template.getFormId();
    }

    /**
     * 从模板 nodeConfig 中解析 businessType。
     */
    private String resolveBusinessType(Long templateId, String nodeKey) {
        return nodeConfigParser.getStringField(getNodeConfigJson(templateId), nodeKey, "businessType");
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

    /**
     * 从 nodeConfig 读取审批节点的 assignStrategy。
     */
    private String resolveAssignStrategy(Long templateId, String taskDefinitionKey) {
        return nodeConfigParser.getStringField(getNodeConfigJson(templateId), taskDefinitionKey, "assignStrategy");
    }

    /**
     * 从 nodeConfig 读取审批节点的 assignValue。
     */
    private String resolveAssignValue(Long templateId, String taskDefinitionKey) {
        return nodeConfigParser.getStringField(getNodeConfigJson(templateId), taskDefinitionKey, "assignValue");
    }

    /**
     * 从模板获取 nodeConfig JSON 字符串。
     */
    private String getNodeConfigJson(Long templateId) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0).orElse(null);
        return template != null && hasText(template.getNodeConfig()) ? template.getNodeConfig() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    // ========================================================================
    // 多实例判断
    // ========================================================================

    /**
     * 判断指定节点是否为多实例（会签/或签）。
     */
    private boolean isMultiInstanceNode(Long templateId, String nodeKey) {
        String mode = nodeConfigParser.getStringField(getNodeConfigJson(templateId), nodeKey, "approvalMode");
        return "ALL".equalsIgnoreCase(mode) || "ANY".equalsIgnoreCase(mode);
    }

    // ========================================================================
    // 驳回/退回
    // ========================================================================

    @Override
    public void rejectTask(String taskId, Long instanceId, String rejectReason) {
        if (instanceId == null) throw new IllegalArgumentException("instanceId must not be null");
        if (!hasText(rejectReason)) throw new IllegalArgumentException("rejectReason must not be blank");
        // 1. 查询 Flowable Task
        Task queriedTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (queriedTask == null) {
            throw new IllegalArgumentException("任务不存在或已完成。");
        }
        taskAuthorizationService.assertCanView(queriedTask);

        // 2. 查询业务 ProcessInstance
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));
        if (!STATUS_RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException(
                    "当前实例状态为【" + instance.getStatus() + "】，仅 running 状态可驳回。");
        }
        if (!queriedTask.getProcessInstanceId().equals(instance.getFlowableProcessInstanceId())) {
            throw new IllegalArgumentException("任务不属于该流程实例。");
        }
        Task task = taskAuthorizationService.requireOperableTask(queriedTask);

        // 3. 保存驳回 FormSubmission（upsert by instanceId + nodeKey，永久保留）
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> rejectData = new HashMap<>();
        rejectData.put("rejectReason", rejectReason);
        rejectData.put("rejectedAt", now.toString());

        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instance.getId(), task.getTaskDefinitionKey(), 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instance.getId())
                        .templateId(instance.getTemplateId())
                        .nodeKey(task.getTaskDefinitionKey())
                        .createdAt(now)
                        .deleted(0)
                        .build());
        submission.setNodeName(task.getName());
        submission.setBusinessType("approval");
        submission.setFormId(resolveFormId(instance.getTemplateId(), task.getTaskDefinitionKey()));
        submission.setFormDataJson(toJson(rejectData));
        submission.setStatus("rejected");
        submission.setUpdatedAt(now);
        formSubmissionRepository.save(submission);

        // 4. 完成 Flowable 任务，由 BPMN 条件网关决定驳回后的真实去向。
        Map<String, Object> variables = approvalVariableService.build(
                task.getProcessInstanceId(), task.getTaskDefinitionKey(), "reject",
                rejectReason, false, null, now);
        try {
            taskService.addComment(taskId, task.getProcessInstanceId(), rejectReason.trim());
            taskService.complete(taskId, variables);
        } catch (Exception ex) {
            throw new IllegalStateException("驳回操作失败：" + safeMessage(ex), ex);
        }
        approvalRecordService.record(instance.getId(), taskId, task.getTaskDefinitionKey(),
                SecurityUtils.currentUserId(), "reject", rejectReason, now);

        // 5. 按 Flowable 的实际活动节点刷新业务实例，避免出现业务状态与引擎状态分裂。
        ruleEvaluatorService.evaluateAndAutoComplete(instance);
    }

    /**
     * 根据 nodeConfig 顺序找到当前节点的上一个可编辑节点 key。
     * 跳过网关等路由节点，回退到最近的 UserTask 或 StartEvent。
     */
    private String resolvePreviousNodeKey(Long templateId, String currentTaskKey) {
        String nodeConfigJson = getNodeConfigJson(templateId);
        if (nodeConfigJson == null) return "StartEvent_1";

        List<Map<String, Object>> nodes = nodeConfigParser.asOrderedList(nodeConfigJson);
        int currentIdx = -1;
        for (int i = 0; i < nodes.size(); i++) {
            String nk = stringValue(nodes.get(i).get("nodeKey"));
            String nid = stringValue(nodes.get(i).get("nodeId"));
            if (currentTaskKey.equals(nk) || currentTaskKey.equals(nid)) {
                currentIdx = i;
                break;
            }
        }
        for (int i = currentIdx - 1; i >= 0; i--) {
            String nk = stringValue(nodes.get(i).get("nodeKey"));
            if (nk != null && !nk.toLowerCase().contains("gateway")) {
                return nk;
            }
        }
        return "StartEvent_1";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Map<String, Object> mergeNodeFormVariables(String flowableProcessInstanceId,
                                                        String nodeKey,
                                                        Map<String, Object> formData) {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> allFormData = new HashMap<>();
        try {
            Map<String, Object> existingVars = runtimeService.getVariables(flowableProcessInstanceId);
            Object existing = existingVars.get("allFormData");
            if (existing instanceof Map<?, ?> map) {
                map.forEach((key, value) -> allFormData.put(String.valueOf(key), value));
            }
        } catch (Exception ignored) {
            // Start with an empty form variable map.
        }
        Map<String, Object> trustedFormData = formData == null ? Map.of() : formData;
        allFormData.put(nodeKey, trustedFormData);
        variables.put("allFormData", allFormData);
        trustedFormData.forEach((key, value) -> {
            if (hasText(key) && (value instanceof Number || value instanceof String || value instanceof Boolean)) {
                variables.put(key, value);
            }
        });
        return variables;
    }

    private String firstMapText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = mapText(values, key);
            if (hasText(value)) return value;
        }
        return null;
    }

    private String mapText(Map<String, Object> values, String key) {
        if (values == null || values.get(key) == null) return null;
        return values.get(key).toString().trim();
    }
}
