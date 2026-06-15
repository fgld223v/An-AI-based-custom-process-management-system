package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.RuleEvaluatorService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEvaluatorServiceImpl implements RuleEvaluatorService {

    private static final int MAX_AUTO_COMPLETE_CHAIN = 10;
    private static final String STATUS_COMPLETED = "completed";
    private static final String AUTO_APPROVE_OPINION = "系统自动通过";

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;

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
        Map<String, Object> approvalData = new HashMap<>();
        approvalData.put("approvalResult", "agree");
        approvalData.put("approvalOpinion", AUTO_APPROVE_OPINION);
        approvalData.put("approved", true);
        approvalData.put("autoApproved", true);
        approvalData.put("autoApproveReason", rule.description());
        approvalData.put("autoApproveTime", now.toString());

        Map<String, Object> variables = new HashMap<>(approvalData);
        variables.put("ruleAutoApproved", true);
        variables.put("ruleAutoApproveTaskId", task.getId());
        variables.put("ruleAutoApproveTaskName", task.getName());

        try {
            Map<String, Object> existingVars = runtimeService.getVariables(task.getProcessInstanceId());
            @SuppressWarnings("unchecked")
            Map<String, Object> allFormData = (Map<String, Object>) existingVars.getOrDefault("allFormData", new HashMap<>());
            allFormData.put(task.getTaskDefinitionKey(), approvalData);
            variables.put("allFormData", allFormData);
        } catch (Exception ex) {
            Map<String, Object> allFormData = new HashMap<>();
            allFormData.put(task.getTaskDefinitionKey(), approvalData);
            variables.put("allFormData", allFormData);
        }

        taskService.addComment(task.getId(), task.getProcessInstanceId(), AUTO_APPROVE_OPINION);
        taskService.complete(task.getId(), variables);
        log.info("Auto approved task by rule. instanceId={}, taskId={}, rule={}",
                instance.getId(), task.getId(), rule.description());
    }

    private Task currentTask(ProcessInstance instance) {
        return taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableProcessInstanceId())
                .active()
                .singleResult();
    }

    private void refreshProcessInstanceState(ProcessInstance instance, Task currentTask, LocalDateTime now) {
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
        try {
            List<Map<String, Object>> nodes = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            for (Map<String, Object> node : nodes) {
                if (nodeKey.equals(String.valueOf(node.get("nodeId")))
                        || nodeKey.equals(String.valueOf(node.get("nodeKey")))) {
                    return node;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse nodeConfig for template {}", templateId, ex);
        }
        return Map.of();
    }

    private String resolveBusinessType(Long templateId, String nodeKey) {
        Object value = findNodeConfig(templateId, nodeKey).get("businessType");
        return value == null ? null : value.toString();
    }

    private ApprovalRule resolveApprovalRule(Map<String, Object> nodeConfig) {
        if (!"approval".equals(String.valueOf(nodeConfig.get("businessType")))) {
            return null;
        }

        Map<String, Object> config = asMap(nodeConfig.get("approvalRule"));
        if (config.isEmpty()) {
            config = asMap(nodeConfig.get("autoApproveRule"));
        }
        if (config.isEmpty()) {
            config = nodeConfig;
        }

        boolean enabled = booleanValue(firstNonNull(
                config.get("enabled"),
                config.get("autoApproveEnabled"),
                config.get("ruleEnabled")
        ));
        String action = stringValue(firstNonNull(
                config.get("action"),
                config.get("autoAction"),
                config.get("result")
        ));
        if (!enabled && !"approve".equalsIgnoreCase(action) && !"auto_approve".equalsIgnoreCase(action)) {
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

    private record ApprovalRule(String field, String operator, Object expected) {
        boolean matches(Map<String, Object> values) {
            Object actual = values.get(field);
            if (actual == null && "leaveDays".equals(field)) {
                actual = values.get("days");
            }
            if (actual == null) {
                return false;
            }
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
            String actualText = String.valueOf(actual);
            String expectedText = String.valueOf(expected);
            return switch (operator) {
                case "=", "==" -> actualText.equals(expectedText);
                case "!=", "<>" -> !actualText.equals(expectedText);
                default -> false;
            };
        }

        String description() {
            return "审批规则命中：" + field + " " + operator + " " + expected;
        }

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
