package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.Department;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.service.ApproverResolverService;
import com.aiflow.service.WorkflowRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批人解析器实现 — 根据分配策略动态解析审批人列表。
 *
 * <p>支持的审批人分配策略：</p>
 * <ul>
 *   <li><b>DEPARTMENT_MANAGER</b> — 发起人所属部门的负责人</li>
 *   <li><b>SPECIFIC_USERS</b> — 指定用户（JSON 数组格式）</li>
 *   <li><b>DIRECT_SUPERVISOR</b> — 发起人的直属上级</li>
 *   <li><b>SPECIFIED_DEPARTMENT_MANAGER</b> — 指定部门的负责人</li>
 *   <li><b>ROLE_IN_APPLICANT_DEPT</b> — 发起人所在部门中拥有指定角色的成员</li>
 *   <li><b>ROLE_IN_SPECIFIED_DEPT</b> — 指定部门中拥有指定角色的成员</li>
 *   <li><b>GLOBAL_ROLE</b> — 全局范围内拥有指定角色的成员</li>
 *   <li><b>ROLE</b> — 兼容模式：优先按工作流角色解析，失败则按旧角色字段解析</li>
 * </ul>
 *
 * <p>所有方法都会过滤掉已删除或已禁用的用户，确保返回的是活跃用户列表。</p>
 */
@Service
@RequiredArgsConstructor
public class ApproverResolverServiceImpl implements ApproverResolverService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final DepartmentRepository departmentRepository;       // 部门信息
    private final SysUserMapper sysUserMapper;                    // 用户 Mapper
    private final ObjectMapper objectMapper;
    private final WorkflowRoleService workflowRoleService;        // 工作流角色服务

    /**
     * 根据流程实例和任务节点解析审批人列表。
     *
     * <p>先从数据库加载流程实例（获取发起人 ID），再根据分配策略和参数值进行解析。</p>
     *
     * @param instanceId       业务流程实例 ID
     * @param taskDefinitionKey 任务节点 key
     * @param assignStrategy    分配策略（如 DEPARTMENT_MANAGER）
     * @param assignValue       分配参数值（如指定用户 ID 列表）
     * @return 审批人 ID 列表（已过滤禁用/删除用户）
     */
    @Override
    public List<Long> resolveApprovers(Long instanceId, String taskDefinitionKey,
                                       String assignStrategy, String assignValue) {
        if (assignStrategy == null || assignStrategy.isBlank()) {
            return Collections.emptyList();
        }

        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));

        return resolveForApplicant(instance.getApplicantId(), assignStrategy, assignValue);
    }

    @Override
    public List<Long> resolveApproversForApplicant(Long applicantId, String taskDefinitionKey,
                                                   String assignStrategy, String assignValue) {
        if (assignStrategy == null || assignStrategy.isBlank()) {
            return Collections.emptyList();
        }
        return resolveForApplicant(applicantId, assignStrategy, assignValue);
    }

    /**
     * 核心路由方法 — 根据分配策略分派到具体的解析实现。
     *
     * <p>支持的策略及其实现：</p>
     * <ul>
     *   <li>DEPARTMENT_MANAGER / DEPARTMENT — 查找发起人所在部门的负责人</li>
     *   <li>SPECIFIC_USERS — 解析指定用户 ID 列表</li>
     *   <li>DIRECT_SUPERVISOR — 查询发起人的直属上级</li>
     *   <li>SPECIFIED_DEPARTMENT_MANAGER — 查找指定部门 ID 的负责人</li>
     *   <li>ROLE_IN_APPLICANT_DEPT — 发起人所在部门中指定角色的成员</li>
     *   <li>ROLE_IN_SPECIFIED_DEPT — 指定部门中指定角色的成员</li>
     *   <li>GLOBAL_ROLE — 全局指定角色成员</li>
     *   <li>ROLE — 兼容模式：先按工作流角色解析，失败则按旧角色字段解析</li>
     * </ul>
     *
     * @param applicantId   发起人用户 ID
     * @param assignStrategy 分配策略（大小写不敏感）
     * @param assignValue   策略参数
     * @return 审批人 ID 列表
     */
    private List<Long> resolveForApplicant(Long applicantId, String assignStrategy, String assignValue) {
        return switch (assignStrategy.trim().toUpperCase()) {
            case "DEPARTMENT_MANAGER" -> resolveDeptManager(applicantId);
            case "SPECIFIC_USERS" -> resolveSpecificUsers(assignValue);
            case "DIRECT_SUPERVISOR" -> resolveSupervisor(applicantId);
            case "DEPARTMENT" -> resolveDeptManager(applicantId); // 别名：等同于部门经理
            case "SPECIFIED_DEPARTMENT_MANAGER" -> resolveSpecifiedDeptManager(assignValue);
            case "ROLE_IN_APPLICANT_DEPT" -> resolveWorkflowRole(
                    roleCode(assignValue), applicantDepartmentId(applicantId));
            case "ROLE_IN_SPECIFIED_DEPT" -> resolveSpecifiedDepartmentRole(assignValue);
            case "GLOBAL_ROLE" -> resolveWorkflowRole(roleCode(assignValue), null);
            case "ROLE" -> resolveCompatibleRole(applicantId, assignValue);
            default -> Collections.emptyList();
        };
    }

    // ========================================================================
    // 策略实现
    // ========================================================================

    /**
     * 部门经理 — 找到发起人所属部门的 leader_user_id。
     */
    private List<Long> resolveDeptManager(Long applicantId) {
        UserEntity applicant = applicant(applicantId);
        if (applicant == null) {
            throw new IllegalStateException("流程发起人不存在或账号已停用");
        }
        Long departmentId = applicant.getDepartmentId();
        if (departmentId == null) {
            throw new IllegalStateException("流程发起人尚未归属部门，无法解析部门负责人");
        }
        return resolveDepartmentLeader(departmentId);
    }

    /**
     * 指定人员 — 从 assignValue JSON 数组解析用户 ID。
     * assignValue 格式: "[1, 2, 3]"
     */
    private List<Long> resolveSpecificUsers(String assignValue) {
        if (assignValue == null || assignValue.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Object> raw = objectMapper.readValue(assignValue,
                    new TypeReference<List<Object>>() {});
            return activeDistinct(raw.stream()
                    .map(this::longValue)
                    .filter(id -> id != null)
                    .toList());
        } catch (Exception ignored) {
            List<Long> ids = new java.util.ArrayList<>();
            for (String part : assignValue.replace("[", "").replace("]", "").split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignoredPart) {
                    // Ignore malformed user identifiers.
                }
            }
            return activeDistinct(ids);
        }
    }

    /**
     * 直属上级 — 查询发起人的 supervisor_id。
     */
    private List<Long> resolveSupervisor(Long applicantId) {
        UserEntity applicant = applicant(applicantId);
        if (applicant == null || applicant.getSupervisorId() == null) return List.of();
        return activeDistinct(List.of(applicant.getSupervisorId()));
    }

    /**
     * 角色成员 — 查询拥有指定角色的所有用户。
     * assignValue: "ADMIN" / "MANAGER" / "USER"
     */
    private List<Long> resolveLegacyRoleMembers(String assignValue) {
        if (assignValue == null || assignValue.isBlank()) {
            return Collections.emptyList();
        }
        List<UserEntity> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getRole, assignValue)
                        .eq(UserEntity::getDeleted, 0));
        return activeDistinct(users.stream().map(UserEntity::getId).toList());
    }

    private List<Long> resolveCompatibleRole(Long applicantId, String assignValue) {
        List<Long> workflowUsers = resolveWorkflowRole(
                roleCode(assignValue), applicantDepartmentId(applicantId));
        return workflowUsers.isEmpty() ? resolveLegacyRoleMembers(assignValue) : workflowUsers;
    }

    private List<Long> resolveSpecifiedDepartmentRole(String assignValue) {
        Map<String, Object> config = parseConfig(assignValue);
        String code = stringValue(config.get("roleCode"));
        Long departmentId = longValue(config.get("departmentId"));
        return resolveWorkflowRole(code, departmentId);
    }

    private List<Long> resolveWorkflowRole(String roleCode, Long departmentId) {
        if (!hasText(roleCode)) return List.of();
        return workflowRoleService.resolveActiveUserIds(roleCode, departmentId);
    }

    private List<Long> resolveSpecifiedDeptManager(String assignValue) {
        Map<String, Object> config = parseConfig(assignValue);
        Long departmentId = longValue(config.get("departmentId"));
        if (departmentId == null) departmentId = longValue(assignValue);
        return departmentId == null ? List.of() : resolveDepartmentLeader(departmentId);
    }

    /**
     * 解析部门负责人 — 查询部门 leader 并校验有效性。
     *
     * <p>校验项：</p>
     * <ol>
     *   <li>部门存在且未删除</li>
     *   <li>部门状态为启用（status=1）</li>
     *   <li>部门已配置负责人（leaderUserId 非空）</li>
     *   <li>负责人用户存在且启用</li>
     * </ol>
     *
     * @param departmentId 部门 ID
     * @return 部门负责人用户 ID 列表
     */
    private List<Long> resolveDepartmentLeader(Long departmentId) {
        Department department = departmentRepository.findByIdAndDeleted(departmentId, 0)
                .orElseThrow(() -> new IllegalStateException("审批部门不存在或已删除，departmentId=" + departmentId));
        if (!Integer.valueOf(1).equals(department.getStatus())) {
            throw new IllegalStateException("审批部门已停用，departmentId=" + departmentId);
        }
        if (department.getLeaderUserId() == null) {
            throw new IllegalStateException("审批部门未配置负责人，departmentId=" + departmentId);
        }
        // 校验负责人用户是否有效（未删除、已启用）
        List<Long> leaders = activeDistinct(List.of(department.getLeaderUserId()));
        if (leaders.isEmpty()) {
            throw new IllegalStateException("审批部门负责人不存在或账号已停用，userId="
                    + department.getLeaderUserId());
        }
        return leaders;
    }

    private Long applicantDepartmentId(Long applicantId) {
        UserEntity applicant = applicant(applicantId);
        return applicant == null ? null : applicant.getDepartmentId();
    }

    private UserEntity applicant(Long applicantId) {
        if (applicantId == null) return null;
        UserEntity user = sysUserMapper.selectById(applicantId);
        return isActive(user) ? user : null;
    }

    private List<Long> activeDistinct(List<Long> userIds) {
        Set<Long> result = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null) continue;
            UserEntity user = sysUserMapper.selectById(userId);
            if (isActive(user)) result.add(userId);
        }
        return List.copyOf(result);
    }

    private boolean isActive(UserEntity user) {
        return user != null
                && !Integer.valueOf(1).equals(user.getDeleted())
                && Integer.valueOf(1).equals(user.getEnabled());
    }

    private String roleCode(String assignValue) {
        Map<String, Object> config = parseConfig(assignValue);
        String configured = stringValue(config.get("roleCode"));
        return hasText(configured) ? configured : normalize(assignValue);
    }

    private Map<String, Object> parseConfig(String value) {
        if (!hasText(value) || !value.trim().startsWith("{")) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
