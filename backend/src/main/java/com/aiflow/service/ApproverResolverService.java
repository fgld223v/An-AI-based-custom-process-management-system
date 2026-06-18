package com.aiflow.service;

import java.util.List;

/**
 * 审批角色解析器 — 根据节点配置的 assignStrategy 和发起人信息，
 * 动态解析审批人列表。
 *
 * <p>三种策略：</p>
 * <ul>
 *   <li>{@code DEPARTMENT_MANAGER} — 找到发起人所属部门的 leader</li>
 *   <li>{@code SPECIFIC_USERS}  — 按 assignValue 中配置的用户 ID 列表</li>
 *   <li>{@code DIRECT_SUPERVISOR} — 发起人的直属上级（当前回退到部门经理）</li>
 * </ul>
 */
public interface ApproverResolverService {

    /**
     * 解析审批人。
     *
     * @param instanceId         业务 ProcessInstance 主键
     * @param taskDefinitionKey  Flowable taskDefinitionKey
     * @param assignStrategy     分配策略（DEPARTMENT_MANAGER / SPECIFIC_USERS / DIRECT_SUPERVISOR）
     * @param assignValue        策略参数（如用户 ID 列表 JSON）
     * @return 审批人用户 ID 列表
     */
    List<Long> resolveApprovers(Long instanceId, String taskDefinitionKey,
                                String assignStrategy, String assignValue);
}
