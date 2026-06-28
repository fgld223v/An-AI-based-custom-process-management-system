package com.aiflow.service.impl;

import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务运行时服务实现 — 严格遵循审批链路。
 *
 * <p>审批链路（不可跳跃）：</p>
 * <ol>
 *   <li>查询 Flowable Task（校验存在性与权限）</li>
 *   <li>查询业务 ProcessInstance，校验状态为 running</li>
 *   <li>保存 FormSubmission（历史表单永久保留，禁止删除）</li>
 *   <li>更新 Flowable Variables（合并 allFormData，审批节点设置审批变量）</li>
 *   <li>收集所有已提交表单字段为顶层变量（供网关条件表达式使用）</li>
 *   <li>预填充 BPMN 排他网关条件引用的缺失变量（防止 Unknown property 错误）</li>
 *   <li>TaskService.complete(taskId, variables) — 执行任务完成</li>
 *   <li>查询下一任务并执行审批规则自动流转（evaluateAndAutoComplete）</li>
 *   <li>刷新 ProcessInstance 状态（currentNodeKey/Name/BusinessType 或标记 completed）</li>
 * </ol>
 *
 * <p>驳回链路（独立处理）：校验实例状态 → 保存驳回 FormSubmission → 设置审批变量 reject
 * → complete 任务 → 记录审批轨迹 → 刷新实例状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskRuntimeServiceImpl implements TaskRuntimeService {

    /** 流程状态：运行中 */
    private static final String STATUS_RUNNING = "running";
    /** 流程状态：已完成 */
    private static final String STATUS_COMPLETED = "completed";
    /** 表单提交状态：已提交 */
    private static final String STATUS_SUBMITTED = "submitted";

    // Flowable 服务
    private final TaskService taskService;
    private final RuntimeService runtimeService;

    // 业务数据访问
    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;

    // 业务服务
    private final RuleEvaluatorService ruleEvaluatorService;
    private final ObjectMapper objectMapper;
    private final NodeConfigParser nodeConfigParser;
    private final TaskAuthorizationService taskAuthorizationService;
    private final ApprovalVariableService approvalVariableService;  // 审批变量构建器
    private final ApprovalRecordService approvalRecordService;       // 审批记录（审计追踪）

    /**
     * 完成任务 — 核心审批链路入口。
     *
     * <p>严格遵循 9 步审批链路，任何一步失败都会抛出异常并回滚事务：</p>
     * <ol>
     *   <li>参数校验（request / instanceId / nodeKey 非空）</li>
     *   <li>查询 Flowable Task，校验存在性与权限</li>
     *   <li>查询业务 ProcessInstance，校验状态为 running，校验 task 与 instance 关联</li>
     *   <li>保存 FormSubmission（upsert by instanceId + nodeKey，历史可追溯）</li>
     *   <li>构建 Flowable 变量：
     *       <ul><li>审批节点：设置 approvalResult / approvalComment / approved 等变量</li>
     *           <li>非审批节点：合并当前节点表单数据到 allFormData</li></ul></li>
     *   <li>收集所有已提交表单字段为顶层变量（供后续网关条件使用）</li>
     *   <li>预填充 BPMN 排他网关条件引用的缺失变量（防止 Unknown property 错误）</li>
     *   <li>taskService.complete(taskId, variables) — 执行 Flowable 任务完成</li>
     *   <li>记录审批轨迹 → 查询下一任务并自动流转 → 返回下一任务（或 null if completed）</li>
     * </ol>
     *
     * @param taskId  Flowable 任务 ID
     * @param request 任务完成请求（含 instanceId、nodeKey、formData）
     * @return 下一任务 DTO，如果流程已完成则返回 null
     */
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

        // 4b. 将所有已提交的表单字段提取为顶层变量
        Map<String, Object> formFieldVars = collectFormFieldVariables(instance.getId());
        variables.putAll(formFieldVars);

        // 4c. 预填充网关条件引用的变量 — 解析 BPMN XML 中的排他网关表达式，
        //     对 variables 中不存在的变量填入默认值 0，彻底杜绝
        //     "Unknown property used in expression" 错误。
        ensureGatewayVariables(instance.getTemplateId(), variables);

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
    // 驳回/退回
    // ========================================================================

    /**
     * 驳回任务 — 独立的审批驳回链路。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>查询 Flowable Task，校验存在性与权限</li>
     *   <li>查询业务 ProcessInstance，校验状态为 running</li>
     *   <li>保存驳回 FormSubmission（upsert by instanceId + nodeKey，永久保留）</li>
     *   <li>构建驳回变量（approvalResult=reject）、添加审批评论</li>
     *   <li>taskService.complete(taskId, variables) — 由 BPMN 条件网关决定驳回后的去向</li>
     *   <li>记录审批轨迹 → 刷新实例状态（确保业务状态与引擎状态同步）</li>
     * </ol>
     *
     * @param taskId       Flowable 任务 ID
     * @param instanceId   业务流程实例 ID
     * @param rejectReason 驳回原因（必填）
     */
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

    /**
     * 合并节点表单变量到 allFormData，同时提取简单类型字段为顶层变量。
     *
     * <p>allFormData 是一个嵌套 Map（key=nodeKey, value=该节点提交的表单数据），
     * 用于跨节点共享表单数据。同时将表单中的 Number/String/Boolean 字段提取为
     * Flowable 顶层变量，方便 BPMN 条件表达式直接引用。</p>
     */
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

    /** 匹配 BPMN 排他网关条件表达式中的变量引用，如 ${amount > 5000} → amount */
    private static final Pattern GATEWAY_VAR_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z_]\\w*)");

    /**
     * 解析模板 BPMN XML 中所有排他网关的条件表达式，提取引用的变量名，
     * 对 variables 中不存在的变量填入默认值 0，彻底防止
     * "Unknown property used in expression" 错误。
     */
    private void ensureGatewayVariables(Long templateId, Map<String, Object> variables) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0).orElse(null);
        if (template == null || !hasText(template.getBpmnXml())) return;

        String bpmnXml = template.getBpmnXml();
        Set<String> neededVars = new HashSet<>();

        // 提取所有排他网关条件表达式中的变量名
        // 条件表达式格式：<bpmn:conditionExpression ...>${amount > 5000}</bpmn:conditionExpression>
        Matcher matcher = GATEWAY_VAR_PATTERN.matcher(bpmnXml);
        while (matcher.find()) {
            String varName = matcher.group(1);
            // 跳过 Flowable 内置变量和关键字
            if ("nrOfCompletedInstances".equals(varName)
                    || "nrOfInstances".equals(varName)
                    || "nrOfActiveInstances".equals(varName)
                    || "execution".equals(varName)
                    || "rejected".equals(varName)) continue;
            neededVars.add(varName);
        }

        // 对缺失的变量填充默认值 0
        for (String varName : neededVars) {
            if (!variables.containsKey(varName)) {
                variables.put(varName, 0L);
                log.info("网关变量 [{}] 不存在，自动填充默认值 0", varName);
            }
        }
    }

    /**
     * 从数据库收集该流程实例所有已提交的表单字段，作为 Flowable 顶层变量。
     *
     * <p>遍历所有 FormSubmission，跳过审批元数据（approvalResult/approvalComment 等），
     * 将业务表单字段提取为平铺的 Map，供 taskService.complete() 直接使用。</p>
     */
    private Map<String, Object> collectFormFieldVariables(Long businessInstanceId) {
        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(businessInstanceId, 0);

        Map<String, Object> fieldVars = new HashMap<>();
        for (FormSubmission submission : submissions) {
            if (!hasText(submission.getFormDataJson())) continue;
            try {
                Map<String, Object> data = objectMapper.readValue(
                        submission.getFormDataJson(),
                        new TypeReference<Map<String, Object>>() {});
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (!hasText(key)) continue;
                    if ("approvalResult".equals(key) || "approvalComment".equals(key)
                            || "approvalOpinion".equals(key) || "comment".equals(key)) continue;
                    if (value instanceof Number || value instanceof String || value instanceof Boolean) {
                        fieldVars.put(key, value);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 FormSubmission 表单数据失败，nodeKey={}", submission.getNodeKey(), e);
            }
        }

        if (!fieldVars.isEmpty()) {
            log.info("收集到 {} 个表单字段将注入 complete 变量：{}",
                    fieldVars.size(), fieldVars.keySet());
        }
        return fieldVars;
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
