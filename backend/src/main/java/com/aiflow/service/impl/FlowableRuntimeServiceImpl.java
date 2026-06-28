package com.aiflow.service.impl;

import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessAuthorizationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 运行时服务实现。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>查询业务 ProcessInstance</li>
 *   <li>校验状态必须为 draft</li>
 *   <li>校验 flowableProcessInstanceId 为空（禁止重复启动）</li>
 *   <li>查询对应 ProcessTemplate，获取 flowableDefinitionId / flowableDeploymentId</li>
 *   <li>聚合 FormSubmission，构建 Variables</li>
 *   <li>调用 Flowable RuntimeService.startProcessInstanceById(...)</li>
 *   <li>回写 flowableProcessInstanceId / flowableDefinitionId / flowableDeploymentId</li>
 *   <li>更新状态为 running，记录 startedAt</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowableRuntimeServiceImpl implements FlowableRuntimeService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ObjectMapper objectMapper;
    private final NodeConfigParser nodeConfigParser;
    private final ProcessAuthorizationService processAuthorizationService;

    /**
     * 启动流程实例 — 从业务层到 Flowable 引擎的核心桥接方法。
     *
     * <p>完整执行流程（9 步）：</p>
     * <ol>
     *   <li>查询业务 ProcessInstance</li>
     *   <li>校验状态必须为 draft（禁止重复启动或从其他状态启动）</li>
     *   <li>校验 flowableProcessInstanceId 为空（禁止重复启动）</li>
     *   <li>查询对应 ProcessTemplate，获取 Flowable 部署信息</li>
     *   <li>校验模板已部署且版本匹配</li>
     *   <li>聚合所有 FormSubmission 数据</li>
     *   <li>构建 Flowable 流程变量（含 allFormData、initiator、startFormData 等）</li>
     *   <li>调用 Flowable RuntimeService.startProcessInstanceById() 启动流程</li>
     *   <li>回写 flowableProcessInstanceId / flowableDefinitionId / flowableDeploymentId，状态更新为 running</li>
     *   <li>自动完成 form_fill 任务 — 将启动表单数据注入流程，使网关条件表达式可引用</li>
     * </ol>
     *
     * @param processInstanceId 业务 ProcessInstance 主键
     * @throws IllegalArgumentException 实例或模板不存在
     * @throws IllegalStateException    状态异常、已启动、模板未发布/未部署、版本不匹配
     */
    @Override
    public void startProcess(Long processInstanceId) {
        // 1. 查询业务 ProcessInstance
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(processInstanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));

        // 2. 校验状态必须为 draft
        if (!"draft".equals(instance.getStatus())) {
            throw new IllegalStateException(
                    "当前实例状态为【" + instance.getStatus() + "】，仅 draft 状态可启动流程。");
        }

        // 3. 校验 flowableProcessInstanceId 为空，禁止重复启动
        if (hasText(instance.getFlowableProcessInstanceId())) {
            throw new IllegalStateException(
                    "当前实例已启动流程引擎（Flowable ID: " + instance.getFlowableProcessInstanceId() + "），不能重复启动。");
        }

        // 4. 查询对应 ProcessTemplate
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在。"));

        processAuthorizationService.assertCanStart(template);

        String definitionId = template.getFlowableProcessDefinitionId();
        String deploymentId = template.getFlowableDeploymentId();
        if (!hasText(definitionId) || !hasText(deploymentId)) {
            throw new IllegalStateException(
                    "当前流程模板尚未部署到 Flowable（缺少 flowableDefinitionId 或 flowableDeploymentId），请先发布模板。");
        }
        if (!definitionId.equals(instance.getFlowableDefinitionId())
                || !deploymentId.equals(instance.getFlowableDeploymentId())) {
            throw new IllegalStateException("流程发布版本已变化，请重新创建申请草稿后再提交。");
        }

        // 5. 聚合 FormSubmission — 获取该实例所有节点的表单提交记录
        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(instance.getId(), 0);
        if (submissions.isEmpty()) {
            throw new IllegalStateException("当前流程实例没有表单提交数据，无法启动流程。");
        }

        // 6. 构建 Variables
        Map<String, Object> variables = buildVariables(instance, submissions);

        // 7. 调用 Flowable RuntimeService 启动流程实例
        org.flowable.engine.runtime.ProcessInstance flowableInstance;
        try {
            flowableInstance = runtimeService.startProcessInstanceById(
                    definitionId,
                    String.valueOf(instance.getId()),
                    variables);
        } catch (FlowableException ex) {
            throw new IllegalStateException("Flowable 流程实例启动失败：" + safeMessage(ex), ex);
        }

        // 8. 回写 Flowable 关联信息 + 更新状态
        LocalDateTime now = LocalDateTime.now();
        instance.setFlowableProcessInstanceId(flowableInstance.getId());
        instance.setFlowableDefinitionId(definitionId);
        instance.setFlowableDeploymentId(deploymentId);
        instance.setStatus("running");
        instance.setStartedAt(now);
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);

        // 9. 自动完成 form_fill 任务 — 如果用户在启动前已填写 start 节点表单，
        //    则将表单数据作为 form_fill 任务的变量提交流程，
        //    使表单字段被提升为 Flowable 顶层变量（排他网关条件表达式如 ${amount > 5000} 依赖顶层变量）
        if (hasStartFormData(submissions)) {
            autoCompleteFormFillTasks(flowableInstance.getId(), template, submissions);
        }
    }

    /** 检查是否存在有效的启动表单数据 */
    private boolean hasStartFormData(List<FormSubmission> submissions) {
        return submissions.stream().anyMatch(
                s -> "start".equalsIgnoreCase(s.getBusinessType())
                     && hasText(s.getFormDataJson()));
    }

    /**
     * 自动完成所有 form_fill 任务，将启动表单数据注入为流程变量。
     *
     * <p>背景：用户在启动流程前已在 start 节点填写表单，但 BPMN 中通常还有独立的
     * form_fill 节点（如"填写请假申请"）。此方法自动完成这些 form_fill 任务，
     * 避免用户重复填写，同时将表单字段提升为 Flowable 顶层变量供网关条件使用。</p>
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>提取 start 节点的表单数据</li>
     *   <li>查询当前流程实例的所有活跃任务</li>
     *   <li>遍历任务，对 businessType=form_fill 的任务执行自动完成</li>
     *   <li>完成时合并 allFormData 并将表单字段提升为顶层变量</li>
     * </ol>
     */
    private void autoCompleteFormFillTasks(String flowableProcessInstanceId,
                                           ProcessTemplate template,
                                           List<FormSubmission> submissions) {
        // 提取 start 节点的表单数据作为 form_fill 的输入
        Map<String, Object> startFormData = extractStartFormData(submissions);
        if (startFormData.isEmpty()) return;

        // 查询当前流程实例的所有活跃任务，按创建时间升序
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(flowableProcessInstanceId)
                .active()
                .orderByTaskCreateTime().asc()
                .list();

        for (Task task : activeTasks) {
            // 根据模板 nodeConfig 解析当前任务的业务类型，仅处理 form_fill 任务
            String businessType = resolveBusinessType(template, task.getTaskDefinitionKey());
            if (!"form_fill".equalsIgnoreCase(businessType)) continue;

            // 构建完成变量：合并已有 allFormData + 将表单字段提升为顶层变量
            Map<String, Object> variables = buildFormFillCompleteVariables(
                    flowableProcessInstanceId, task.getTaskDefinitionKey(), startFormData);

            // 完成该 form_fill 任务，使流程推进到下一节点
            taskService.complete(task.getId(), variables);
            log.info("已自动完成 form_fill 任务 {}（nodeKey={}），注入 {} 个表单变量",
                    task.getId(), task.getTaskDefinitionKey(), startFormData.size());
        }
    }

    /** 从 submissions 中提取启动表单数据 */
    private Map<String, Object> extractStartFormData(List<FormSubmission> submissions) {
        for (FormSubmission s : submissions) {
            if ("start".equalsIgnoreCase(s.getBusinessType()) && hasText(s.getFormDataJson())) {
                return parseFormData(s);
            }
        }
        return Map.of();
    }

    /**
     * 构建 form_fill 任务完成时的变量，确保表单字段被提升为顶层 Flowable 变量。
     *
     * <p>双层变量结构：</p>
     * <ul>
     *   <li>allFormData[nodeKey] = formData — 节点级嵌套存储，供后续节点查询历史表单</li>
     *   <li>顶层字段 — 将 formData 中的每个字段直接提升为顶层变量，
     *       使 BPMN 排他网关条件表达式（如 ${amount > 5000}）能直接引用</li>
     * </ul>
     */
    private Map<String, Object> buildFormFillCompleteVariables(String flowableProcessInstanceId,
                                                                String nodeKey,
                                                                Map<String, Object> formData) {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> allFormData = new LinkedHashMap<>();

        // 保留 Flowable 中已有的 allFormData，避免覆盖其他节点已提交的表单
        try {
            Map<String, Object> existingVars = runtimeService.getVariables(flowableProcessInstanceId);
            Object existing = existingVars.get("allFormData");
            if (existing instanceof Map<?, ?> map) {
                map.forEach((key, value) -> allFormData.put(String.valueOf(key), value));
            }
        } catch (Exception ignored) {}

        // 将当前节点表单数据存入 allFormData[nodeKey]
        allFormData.put(nodeKey, formData);
        variables.put("allFormData", allFormData);

        // 将表单字段提升为顶层 Flowable 变量，使网关条件表达式（如 ${amount > 5000}）能正确引用
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = normalizeVariableValue(entry.getValue());
            if (value != null) {
                variables.put(entry.getKey(), value);
            }
        }

        return variables;
    }

    /** 从模板 nodeConfig 解析节点 businessType */
    private String resolveBusinessType(ProcessTemplate template, String nodeKey) {
        if (!hasText(template.getNodeConfig())) return null;
        Map<String, Object> node = nodeConfigParser.findNode(template.getNodeConfig(), nodeKey);
        return node != null ? stringValue(node.get("businessType")) : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    // ========================================================================
    // 流程变量构建 — 将业务数据转换为 Flowable 可识别的变量结构
    // ========================================================================

    /**
     * 构建启动变量，结构：
     * <pre>
     * {
     *   "businessInstanceId": 1,
     *   "templateId": 100,
     *   "instanceCode": "PI_20260101120000001",
     *   "instanceTitle": "请假申请-张三",
     *   "allFormData": {
     *     "StartEvent_1": { "leaveReason": "事假", "leaveDays": 2 },
     *     "UserTask_ManagerApprove": { "approvalResult": "agree" }
     *   },
     *   "startFormData": { "leaveReason": "事假", "leaveDays": 2 }
     * }
     * </pre>
     */
    private Map<String, Object> buildVariables(ProcessInstance instance, List<FormSubmission> submissions) {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> allFormData = new LinkedHashMap<>();
        Map<String, Object> startFormData = null;

        for (FormSubmission submission : submissions) {
            Map<String, Object> data = parseFormData(submission);
            allFormData.put(submission.getNodeKey(), data);

            // 取第一个 start 类型节点的数据作为 startFormData
            if (startFormData == null && "start".equalsIgnoreCase(submission.getBusinessType())) {
                startFormData = data;
            }
        }

        // 如果没有明确的 start 类型节点，取最后一条提交作为兜底
        if (startFormData == null && !submissions.isEmpty()) {
            startFormData = parseFormData(submissions.get(submissions.size() - 1));
        }

        variables.put("businessInstanceId", instance.getId());
        variables.put("templateId", instance.getTemplateId());
        variables.put("instanceCode", instance.getInstanceCode());
        variables.put("instanceTitle", instance.getTitle());
        variables.put("allFormData", allFormData);
        variables.put("startFormData", startFormData == null ? Map.of() : startFormData);

        // 设置流程发起人（initiator），使 form_fill 节点能自动分配给申请人
        String applicantId = String.valueOf(instance.getApplicantId());
        variables.put("initiator", applicantId);
        variables.put("applicantId", applicantId);

        // 将 startFormData 中的字段提升为顶层流程变量，
        // 使 BPMN 排他网关条件表达式可直接引用（如 ${amount > 5000}）
        if (startFormData != null) {
            for (Map.Entry<String, Object> entry : startFormData.entrySet()) {
                Object value = normalizeVariableValue(entry.getValue());
                if (value != null) {
                    variables.put(entry.getKey(), value);
                }
            }
        }

        // 如果启动时有有效的表单数据，标记为已预提交，
        // 配合 BPMN 中 form_fill 节点的 skipExpression 跳过重复填写
        boolean hasStartFormData = startFormData != null && !startFormData.isEmpty();
        variables.put("startFormSubmitted", hasStartFormData);

        return variables;
    }

    /**
     * 规范化流程变量值 — 将 JSON 解析产生的数值类型标准化，
     * 确保 JUEL 表达式（如 ${amount > 5000}）能正确比较。
     *
     * <p>处理规则：</p>
     * <ul>
     *   <li>Number/Boolean 类型直接返回（JSON 解析已产生正确类型）</li>
     *   <li>字符串尝试解析为 Long 或 Double，使网关条件比较能正确处理数值</li>
     *   <li>无法解析的字符串保持原样</li>
     *   <li>null 或其他类型返回 null（不注入为变量）</li>
     * </ul>
     */
    private Object normalizeVariableValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String s) {
            // 尝试解析为数字 — 含小数点解析为 Double，否则解析为 Long
            // 这样网关条件 ${amount > 5000} 中的 amount 是数值类型，能正确比较
            try {
                if (s.contains(".")) {
                    return Double.parseDouble(s);
                }
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        return null;
    }

    private Map<String, Object> parseFormData(FormSubmission submission) {
        if (!hasText(submission.getFormDataJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(submission.getFormDataJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            String nodeName = hasText(submission.getNodeName())
                    ? submission.getNodeName() : submission.getNodeKey();
            throw new IllegalStateException(
                    "节点【" + nodeName + "】表单数据 JSON 解析失败，无法启动流程。", ex);
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
