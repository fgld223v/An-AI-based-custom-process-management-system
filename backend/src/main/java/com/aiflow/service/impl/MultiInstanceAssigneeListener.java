package com.aiflow.service.impl;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApproverResolverService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多实例审批人分配监听器。
 *
 * <p>在 BPMN 多实例活动（会签/或签）的 {@code start} 事件触发时：</p>
 * <ol>
 *   <li>从流程变量中获取 businessInstanceId、templateId</li>
 *   <li>从模板 nodeConfig 中解析 assignStrategy / assignValue</li>
 *   <li>调用 {@link ApproverResolverService} 解析审批人列表</li>
 *   <li>将审批人 ID 列表设置为多实例集合变量 {@code assigneeList_<nodeKey>}</li>
 * </ol>
 *
 * <p>Spring Bean 名称：{@code multiInstanceAssigneeListener}，
 * BPMN XML 中通过 {@code delegateExpression="${multiInstanceAssigneeListener}"} 引用。</p>
 */
@Slf4j
@Component("multiInstanceAssigneeListener")
public class MultiInstanceAssigneeListener implements ExecutionListener {

    @Autowired
    private ApproverResolverService approverResolverService;

    @Autowired
    private ProcessTemplateRepository processTemplateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void notify(DelegateExecution execution) {
        String nodeKey = execution.getCurrentActivityId();
        if (nodeKey == null) {
            log.warn("MultiInstanceAssigneeListener: 无法获取当前活动 ID，跳过审批人分配");
            return;
        }

        // 从流程变量中获取业务上下文
        Object businessInstanceIdObj = execution.getVariable("businessInstanceId");
        Object templateIdObj = execution.getVariable("templateId");

        if (businessInstanceIdObj == null || templateIdObj == null) {
            log.warn("MultiInstanceAssigneeListener: 缺少 businessInstanceId 或 templateId 变量，" +
                    "nodeKey={}", nodeKey);
            // 兜底：设置空列表，避免流程卡死
            execution.setVariable("assigneeList_" + nodeKey, List.of());
            return;
        }

        Long instanceId = toLong(businessInstanceIdObj);
        Long templateId = toLong(templateIdObj);

        // 从模板 nodeConfig 获取审批策略
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null) {
            log.warn("MultiInstanceAssigneeListener: 模板 {} 不存在", templateId);
            execution.setVariable("assigneeList_" + nodeKey, List.of());
            return;
        }

        String assignStrategy = null;
        String assignValue = null;

        if (hasText(template.getNodeConfig())) {
            Map<String, Object> nodeConfig = findNodeConfig(template.getNodeConfig(), nodeKey);
            if (nodeConfig != null) {
                assignStrategy = stringValue(nodeConfig.get("assignStrategy"));
                assignValue = stringValue(nodeConfig.get("assignValue"));

                // 兼容前端 assigneeType → assignStrategy 映射
                if (!hasText(assignStrategy)) {
                    String assigneeType = stringValue(nodeConfig.get("assigneeType"));
                    if (hasText(assigneeType)) {
                        assignStrategy = mapAssigneeTypeToStrategy(assigneeType);
                    }
                }
                // assignValue 可能来自 assigneeValue
                if (!hasText(assignValue)) {
                    assignValue = stringValue(nodeConfig.get("assigneeValue"));
                }
            }
        }

        // 解析审批人列表
        List<Long> approverIds;
        if (hasText(assignStrategy)) {
            approverIds = approverResolverService.resolveApprovers(
                    instanceId, nodeKey, assignStrategy, assignValue);
        } else {
            // 兜底：使用部门经理
            log.info("MultiInstanceAssigneeListener: 节点 {} 未配置审批策略，使用部门经理兜底", nodeKey);
            approverIds = approverResolverService.resolveApprovers(
                    instanceId, nodeKey, "DEPARTMENT_MANAGER", null);
        }

        // Flowable 多实例集合元素必须是 String 列表（用于 assignee 赋值）
        List<String> assigneeList = approverIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        if (assigneeList.isEmpty()) {
            log.warn("MultiInstanceAssigneeListener: 节点 {} 的审批人列表为空，流程将卡死！", nodeKey);
            // 设置一个系统管理员兜底
            assigneeList = List.of("1");
        }

        String collectionVar = "assigneeList_" + nodeKey;
        execution.setVariable(collectionVar, assigneeList);
        log.info("MultiInstanceAssigneeListener: 节点 {} 分配审批人 {} 人，collectionVar={}",
                nodeKey, assigneeList.size(), collectionVar);
    }

    /**
     * 前端 assigneeType → 后端 assignStrategy 映射。
     */
    private String mapAssigneeTypeToStrategy(String assigneeType) {
        return switch (assigneeType.toUpperCase()) {
            case "MANAGER" -> "DIRECT_SUPERVISOR";
            case "DEPT_LEADER" -> "DEPARTMENT_MANAGER";
            case "USER" -> "SPECIFIC_USERS";
            case "ROLE" -> "ROLE";
            default -> "";
        };
    }

    /**
     * 从 nodeConfig JSON（Map 格式或 List 格式）中查找指定 nodeId/nodeKey 的配置。
     */
    private Map<String, Object> findNodeConfig(String nodeConfigJson, String nodeKey) {
        try {
            // 尝试 Map 格式: { "NodeId": {...}, ... }
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    nodeConfigJson, new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) return config;

            // nodeKey 可能与 nodeId 不同，遍历查找
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Object candidateKey = entry.getValue().get("nodeKey");
                Object candidateId = entry.getValue().get("nodeId");
                if (nodeKey.equals(stringValue(candidateKey)) || nodeKey.equals(stringValue(candidateId))) {
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
            // 尝试 List 格式
            try {
                List<Map<String, Object>> list = objectMapper.readValue(
                        nodeConfigJson, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    Object nk = item.get("nodeKey");
                    Object nid = item.get("nodeId");
                    if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                        return item;
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return null;
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
