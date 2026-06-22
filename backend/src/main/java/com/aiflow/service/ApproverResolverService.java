package com.aiflow.service;

import java.util.List;

/**
 * 审批角色解析器 — 根据节点配置的 assignStrategy 和发起人信息，
 * 动态解析审批人列表。
 *
 * <p>支持组织关系、部门范围流程角色和全局流程角色。</p>
 * <ul>
 *   <li>{@code DEPARTMENT_MANAGER} — 找到发起人所属部门的 leader</li>
 *   <li>{@code SPECIFIC_USERS}  — 按 assignValue 中配置的用户 ID 列表</li>
 *   <li>{@code DIRECT_SUPERVISOR} — 发起人的直属上级</li>
 *   <li>{@code ROLE_IN_APPLICANT_DEPT} — 发起人部门内的流程角色</li>
 *   <li>{@code ROLE_IN_SPECIFIED_DEPT} — 指定部门内的流程角色</li>
 *   <li>{@code GLOBAL_ROLE} — 全局流程角色</li>
 * </ul>
 */
public interface ApproverResolverService {

    /**
     * 解析审批人。
     *
     * @param instanceId         业务 ProcessInstance 主键
     * @param taskDefinitionKey  Flowable taskDefinitionKey
     * @param assignStrategy     分配策略
     * @param assignValue        策略参数（如用户 ID 列表 JSON）
     * @return 审批人用户 ID 列表
     */
    List<Long> resolveApprovers(Long instanceId, String taskDefinitionKey,
                                String assignStrategy, String assignValue);
}
