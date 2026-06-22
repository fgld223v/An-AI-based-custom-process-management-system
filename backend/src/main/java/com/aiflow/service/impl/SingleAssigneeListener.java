package com.aiflow.service.impl;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApproverResolverService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 单人审批节点 TaskListener。
 *
 * <p>在 SINGLE 审批节点任务创建时触发（event=create）：</p>
 * <ol>
 *   <li>从流程变量中获取 businessInstanceId、templateId</li>
 *   <li>从模板 nodeConfig 中解析 assignStrategy / assignValue</li>
 *   <li>调用 {@link ApproverResolverService} 解析审批人</li>
 *   <li>将第一个解析出的审批人设置为任务 assignee</li>
 * </ol>
 *
 * <p>如果节点是 form_fill 类型（已有 initiator assignee），则不覆盖。</p>
 *
 * <p>Spring Bean 名称：{@code singleAssigneeListener}，
 * BPMN XML 中通过 {@code delegateExpression="${singleAssigneeListener}"} 引用。</p>
 */
@Slf4j
@Component("singleAssigneeListener")
@RequiredArgsConstructor
public class SingleAssigneeListener implements TaskListener {

    private final ApproverResolverService approverResolverService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void notify(DelegateTask task) {
        String nodeKey = task.getTaskDefinitionKey();
        if (nodeKey == null) {
            log.warn("SingleAssigneeListener: 无法获取 taskDefinitionKey，跳过审批人分配");
            return;
        }

        // 注意：不要因为已有 assignee 就跳过！
        // Flowable 会在 TaskListener 触发前把 ${initiator} 表达式解析为实际用户 ID，
        // 所以此处的 assignee 是流程发起人而非占位符。审批节点需要重新分配到真正的审批人。

        // 从流程变量中获取业务上下文
        Object businessInstanceIdObj = task.getVariable("businessInstanceId");
        Object templateIdObj = task.getVariable("templateId");

        if (businessInstanceIdObj == null || templateIdObj == null) {
            throw new IllegalStateException(
                    "审批任务缺少 businessInstanceId 或 templateId，nodeKey=" + nodeKey);
        }

        Long instanceId = toLong(businessInstanceIdObj);
        Long templateId = toLong(templateIdObj);

        // 从模板 nodeConfig 获取审批策略
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null) {
            throw new IllegalStateException("审批任务关联的流程模板不存在，templateId=" + templateId);
        }

        String assignStrategy = null;
        String assignValue = null;
        String businessType = null;

        if (hasText(template.getNodeConfig())) {
            Map<String, Object> nodeConfig = findNodeConfig(template.getNodeConfig(), nodeKey);
            if (nodeConfig != null) {
                assignStrategy = stringValue(nodeConfig.get("assignStrategy"));
                assignValue = stringValue(nodeConfig.get("assignValue"));
                businessType = stringValue(nodeConfig.get("businessType"));

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

        // form_fill 节点不需要重新分配 — initiator assignee 已足够
        if ("form_fill".equals(businessType)) {
            log.debug("SingleAssigneeListener: 节点 {} 是 form_fill，保持 initiator assignee", nodeKey);
            return;
        }

        // 解析审批人
        List<Long> approverIds;
        if (hasText(assignStrategy)) {
            approverIds = approverResolverService.resolveApprovers(
                    instanceId, nodeKey, assignStrategy, assignValue);
        } else {
            // 兜底：使用部门经理
            log.info("SingleAssigneeListener: 审批节点 {} 未配置审批策略，使用部门经理兜底", nodeKey);
            approverIds = approverResolverService.resolveApprovers(
                    instanceId, nodeKey, "DEPARTMENT_MANAGER", null);
        }

        if (approverIds.isEmpty()) {
            throw new IllegalStateException("审批节点未解析到有效处理人，nodeKey=" + nodeKey);
        }

        // 单人审批取第一个审批人
        String assigneeId = String.valueOf(approverIds.get(0));
        task.setAssignee(assigneeId);
        log.info("SingleAssigneeListener: 审批节点 {} 已分配 assignee={}（strategy={}, value={}）",
                nodeKey, assigneeId, assignStrategy, assignValue);
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
     * 从 nodeConfig JSON 中查找指定 nodeId/nodeKey 的配置。
     */
    private Map<String, Object> findNodeConfig(String nodeConfigJson, String nodeKey) {
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    nodeConfigJson, new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) return config;

            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Object candidateKey = entry.getValue().get("nodeKey");
                Object candidateId = entry.getValue().get("nodeId");
                if (nodeKey.equals(stringValue(candidateKey)) || nodeKey.equals(stringValue(candidateId))) {
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
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
