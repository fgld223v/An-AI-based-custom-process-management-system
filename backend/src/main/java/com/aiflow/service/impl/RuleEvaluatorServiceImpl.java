package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.RuleEvaluatorService;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ApprovalVariableService;
import com.aiflow.service.WorkflowNotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批规则评估器 — 任务完成后自动检查下一任务是否命中审批规则并自动完成。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li><b>规则评估</b> — 从节点配置中解析审批规则（阈值规则），如"金额 < 5000 自动通过"</li>
 *   <li><b>规则匹配</b> — 从流程变量和表单数据中收集字段值，与规则条件进行比对</li>
 *   <li><b>自动流转</b> — 命中规则的审批任务自动完成，支持连续自动完成（最多 10 步）</li>
 *   <li><b>状态刷新</b> — 每次流转后刷新 ProcessInstance 的当前节点信息</li>
 * </ul>
 *
 * <p>安全机制：自动完成链最大长度为 {@value #MAX_AUTO_COMPLETE_CHAIN}，
 * 防止无限循环。自动完成的审批记录审批人为 null（系统自动），审批意见为"系统自动通过"。</p>
 *
 * <p>规则配置字段（按优先级）：</p>
 * <ul>
 *   <li>field / conditionField / metric — 规则字段名</li>
 *   <li>operator / op — 比较操作符（<, <=, >, >=, =, !=）</li>
 *   <li>value / threshold / max / amountThreshold / daysThreshold — 阈值</li>
 *   <li>enabled / autoApproveEnabled / ruleEnabled — 是否启用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEvaluatorServiceImpl implements RuleEvaluatorService {

    /** 自动完成链最大长度，防止无限循环 */
    private static final int MAX_AUTO_COMPLETE_CHAIN = 10;
    /** 流程状态：已完成 */
    private static final String STATUS_COMPLETED = "completed";
    /** 自动审批意见 */
    private static final String AUTO_APPROVE_OPINION = "系统自动通过";

    // Flowable 服务
    private final TaskService taskService;
    private final RuntimeService runtimeService;

    // 数据访问
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;

    // 业务服务
    private final NodeConfigParser nodeConfigParser;
    private final ApprovalVariableService approvalVariableService;
    private final ApprovalRecordService approvalRecordService;
    private final WorkflowNotificationService workflowNotificationService;

    /**
     * 评估审批规则并自动完成命中规则的任务。
     *
     * <p>算法流程：</p>
     * <ol>
     *   <li>获取流程实例的当前活跃任务</li>
     *   <li>从节点配置中解析审批规则</li>
     *   <li>收集流程变量和表单数据，与规则条件比对</li>
     *   <li>命中规则：自动完成任务（审批人为系统，审批意见为"系统自动通过"）</li>
     *   <li>获取下一任务，重复步骤 2-4</li>
     *   <li>未命中规则或达到最大链长：停止并刷新实例状态</li>
     * </ol>
     *
     * <p>自动完成链上限为 {@value #MAX_AUTO_COMPLETE_CHAIN} 步，超过则记录警告日志。</p>
     *
     * @param instance 当前流程实例
     * @return 当前待处理的任务（未命中规则的任务），如果流程已结束则返回 null
     */
    @Override
    @Transactional
    public Task evaluateAndAutoComplete(ProcessInstance instance) {
        if (instance == null || !hasText(instance.getFlowableProcessInstanceId())) {
            return null;
        }

        Task currentTask = currentTask(instance);
        int completedCount = 0;
        while (currentTask != null && completedCount < MAX_AUTO_COMPLETE_CHAIN) {
            Map<String, Object> nodeConfig = findNodeConfig(instance.getTemplateId(), currentTask.getTaskDefinitionKey());
            ApprovalRule rule = resolveApprovalRule(nodeConfig);
            if (rule == null || !rule.matches(collectVariables(instance, currentTask))) {
                refreshProcessInstanceState(instance, currentTask, LocalDateTime.now());
                return currentTask;
            }

            autoCompleteTask(currentTask, instance, rule);
            completedCount++;
            currentTask = currentTask(instance);
        }

        if (completedCount >= MAX_AUTO_COMPLETE_CHAIN) {
            log.warn("Auto approval chain stopped after {} tasks, instanceId={}", MAX_AUTO_COMPLETE_CHAIN, instance.getId());
        }

        refreshProcessInstanceState(instance, currentTask, LocalDateTime.now());
        return currentTask;
    }

    private void autoCompleteTask(Task task, ProcessInstance instance, ApprovalRule rule) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> variables = approvalVariableService.build(
                task.getProcessInstanceId(), task.getTaskDefinitionKey(), "agree",
                AUTO_APPROVE_OPINION, true, rule.description(), now);
        variables.put("ruleAutoApproved", true);
        variables.put("ruleAutoApproveTaskId", task.getId());
        variables.put("ruleAutoApproveTaskName", task.getName());

        taskService.addComment(task.getId(), task.getProcessInstanceId(), AUTO_APPROVE_OPINION);
        taskService.complete(task.getId(), variables);
        approvalRecordService.record(instance.getId(), task.getId(), task.getTaskDefinitionKey(),
                null, "approve", AUTO_APPROVE_OPINION + "：" + rule.description(), now);
        log.info("Auto approved task by rule. instanceId={}, taskId={}, rule={}",
                instance.getId(), task.getId(), rule.description());
    }

    private Task currentTask(ProcessInstance instance) {
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableProcessInstanceId())
                .active()
                .orderByTaskCreateTime().asc()
                .list();
        return activeTasks.isEmpty() ? null : activeTasks.get(0);
    }

    private void refreshProcessInstanceState(ProcessInstance instance, Task currentTask, LocalDateTime now) {
        boolean newlyCompleted = currentTask == null && !STATUS_COMPLETED.equals(instance.getStatus());
        if (currentTask != null) {
            instance.setCurrentNodeKey(currentTask.getTaskDefinitionKey());
            instance.setCurrentNodeName(currentTask.getName());
            instance.setCurrentBusinessType(resolveBusinessType(instance.getTemplateId(), currentTask.getTaskDefinitionKey()));
        } else {
            instance.setStatus(STATUS_COMPLETED);
            instance.setEndedAt(now);
            instance.setCurrentNodeKey(null);
            instance.setCurrentNodeName(null);
            instance.setCurrentBusinessType(null);
        }
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);
        if (newlyCompleted) {
            workflowNotificationService.notifyProcessCompleted(instance);
        }
    }

    private Map<String, Object> collectVariables(ProcessInstance instance, Task task) {
        Map<String, Object> values = new HashMap<>();
        putJson(values, instance.getFormData());
        try {
            Map<String, Object> runtimeVars = runtimeService.getVariables(task.getProcessInstanceId());
            values.putAll(flatten(runtimeVars));
        } catch (Exception ignored) {
            // Missing runtime variables should not block manual approval.
        }
        return values;
    }

    private Map<String, Object> findNodeConfig(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(templateId, 0).orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return Map.of();
        }
        Map<String, Object> node = nodeConfigParser.findNode(template.getNodeConfig(), nodeKey);
        return node != null ? node : Map.of();
    }

    private String resolveBusinessType(Long templateId, String nodeKey) {
        Object value = findNodeConfig(templateId, nodeKey).get("businessType");
        return value == null ? null : value.toString();
    }

    /**
     * 从节点配置中解析审批规则。
     *
     * <p>规则提取优先级（多字段兼容）：</p>
     * <ol>
     *   <li>approvalRule 子对象 → autoApproveRule 子对象 → 节点配置根对象</li>
     *   <li>enabled 显式为 false 时规则直接失效</li>
     *   <li>field 字段支持多种命名：field / conditionField / metric</li>
     *   <li>阈值字段支持：value / threshold / max / amountThreshold / daysThreshold</li>
     *   <li>特殊后缀自动推断：daysThreshold → field="leaveDays"；amountThreshold → field="amount"</li>
     * </ol>
     *
     * @param nodeConfig 节点配置 Map
     * @return 解析出的审批规则，如果节点不是审批类型或规则无效则返回 null
     */
    private ApprovalRule resolveApprovalRule(Map<String, Object> nodeConfig) {
        // 仅审批节点才可能有审批规则
        if (!"approval".equals(String.valueOf(nodeConfig.get("businessType")))) {
            return null;
        }

        // 按优先级查找规则配置对象
        Map<String, Object> config = asMap(nodeConfig.get("approvalRule"));
        if (config.isEmpty()) {
            config = asMap(nodeConfig.get("autoApproveRule"));
        }
        if (config.isEmpty()) {
            config = nodeConfig;  // 回退到节点根配置
        }

        Object enabledValue = firstNonNull(
                config.get("enabled"),
                config.get("autoApproveEnabled"),
                config.get("ruleEnabled")
        );
        boolean enabled = booleanValue(enabledValue);
        String action = stringValue(firstNonNull(
                config.get("action"),
                config.get("autoAction"),
                config.get("result")
        ));
        // An explicit enabled=false always wins. The action field describes what an
        // enabled rule does; it must not silently enable the rule by itself.
        if (enabledValue != null && !enabled) {
            return null;
        }
        if (enabledValue == null
                && !"approve".equalsIgnoreCase(action)
                && !"auto_approve".equalsIgnoreCase(action)) {
            return null;
        }

        String field = stringValue(firstNonNull(
                config.get("field"),
                config.get("conditionField"),
                config.get("metric")
        ));
        String operator = stringValue(firstNonNull(
                config.get("operator"),
                config.get("op")
        ));
        Object expected = firstNonNull(
                config.get("value"),
                config.get("threshold"),
                config.get("max"),
                config.get("amountThreshold"),
                config.get("daysThreshold")
        );

        if (!hasText(field) && config.get("daysThreshold") != null) {
            field = "leaveDays";
            operator = hasText(operator) ? operator : "<";
            expected = config.get("daysThreshold");
        }
        if (!hasText(field) && config.get("amountThreshold") != null) {
            field = "amount";
            operator = hasText(operator) ? operator : "<=";
            expected = config.get("amountThreshold");
        }

        if (!hasText(field) || expected == null) {
            return null;
        }
        if (!hasText(operator)) {
            operator = "<=";
        }
        return new ApprovalRule(field, operator, expected);
    }

    private void putJson(Map<String, Object> target, String json) {
        if (!hasText(json)) {
            return;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            target.putAll(flatten(parsed));
        } catch (Exception ignored) {
            // Ignore invalid draft form JSON.
        }
    }

    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>();
        flattenInto("", source, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(String prefix, Map<String, Object> source, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = hasText(prefix) ? prefix + "." + entry.getKey() : entry.getKey();
            Object value = entry.getValue();
            result.put(key, value);
            result.putIfAbsent(entry.getKey(), value);
            if (value instanceof Map<?, ?> nested) {
                flattenInto(key, (Map<String, Object>) nested, result);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 审批规则记录 — 封装规则字段、操作符和期望值，提供匹配判断。
     *
     * @param field    规则字段名
     * @param operator 比较操作符（<, <=, >, >=, =, ==, !=, <>）
     * @param expected 期望值（阈值）
     */
    private record ApprovalRule(String field, String operator, Object expected) {

        /**
         * 判断规则是否命中。
         *
         * <p>匹配逻辑：</p>
         * <ol>
         *   <li>从变量集合中获取实际值</li>
         *   <li>优先尝试 BigDecimal 数值比较（支持整数、小数、字符串数字）</li>
         *   <li>数值比较失败则回退到字符串精确/不等比较</li>
         * </ol>
         */
        boolean matches(Map<String, Object> values) {
            // 获取实际值，leaveDays 字段兼容 "days" 别名
            Object actual = values.get(field);
            if (actual == null && "leaveDays".equals(field)) {
                actual = values.get("days");
            }
            if (actual == null) {
                return false;  // 变量不存在，规则不命中
            }
            // 尝试数值比较（优先）
            BigDecimal actualNumber = numberValue(actual);
            BigDecimal expectedNumber = numberValue(expected);
            if (actualNumber != null && expectedNumber != null) {
                int compare = actualNumber.compareTo(expectedNumber);
                return switch (operator) {
                    case "<" -> compare < 0;
                    case "<=" -> compare <= 0;
                    case ">" -> compare > 0;
                    case ">=" -> compare >= 0;
                    case "=", "==" -> compare == 0;
                    case "!=", "<>" -> compare != 0;
                    default -> false;
                };
            }
            // 回退：字符串比较
            String actualText = String.valueOf(actual);
            String expectedText = String.valueOf(expected);
            return switch (operator) {
                case "=", "==" -> actualText.equals(expectedText);
                case "!=", "<>" -> !actualText.equals(expectedText);
                default -> false;
            };
        }

        /** 规则命中描述（用于日志和审批记录） */
        String description() {
            return "审批规则命中：" + field + " " + operator + " " + expected;
        }

        /** 将任意值转换为 BigDecimal，用于数值比较 */
        private static BigDecimal numberValue(Object value) {
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            try {
                return new BigDecimal(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
