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
 * 审批角色解析器实现。
 */
@Service
@RequiredArgsConstructor
public class ApproverResolverServiceImpl implements ApproverResolverService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final DepartmentRepository departmentRepository;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowRoleService workflowRoleService;

    @Override
    public List<Long> resolveApprovers(Long instanceId, String taskDefinitionKey,
                                       String assignStrategy, String assignValue) {
        if (assignStrategy == null || assignStrategy.isBlank()) {
            return Collections.emptyList();
        }

        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));

        return switch (assignStrategy.trim().toUpperCase()) {
            case "DEPARTMENT_MANAGER" -> resolveDeptManager(instance);
            case "SPECIFIC_USERS" -> resolveSpecificUsers(assignValue);
            case "DIRECT_SUPERVISOR" -> resolveSupervisor(instance);
            case "DEPARTMENT" -> resolveDeptManager(instance);
            case "SPECIFIED_DEPARTMENT_MANAGER" -> resolveSpecifiedDeptManager(assignValue);
            case "ROLE_IN_APPLICANT_DEPT" -> resolveWorkflowRole(
                    roleCode(assignValue), applicantDepartmentId(instance));
            case "ROLE_IN_SPECIFIED_DEPT" -> resolveSpecifiedDepartmentRole(assignValue);
            case "GLOBAL_ROLE" -> resolveWorkflowRole(roleCode(assignValue), null);
            case "ROLE" -> resolveCompatibleRole(instance, assignValue);
            default -> Collections.emptyList();
        };
    }

    // ========================================================================
    // 策略实现
    // ========================================================================

    /**
     * 部门经理 — 找到发起人所属部门的 leader_user_id。
     */
    private List<Long> resolveDeptManager(ProcessInstance instance) {
        UserEntity applicant = applicant(instance);
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
     * 直属上级 — 当前回退到部门经理。
     */
    private List<Long> resolveSupervisor(ProcessInstance instance) {
        UserEntity applicant = applicant(instance);
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

    private List<Long> resolveCompatibleRole(ProcessInstance instance, String assignValue) {
        List<Long> workflowUsers = resolveWorkflowRole(
                roleCode(assignValue), applicantDepartmentId(instance));
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

    private List<Long> resolveDepartmentLeader(Long departmentId) {
        Department department = departmentRepository.findByIdAndDeleted(departmentId, 0)
                .orElseThrow(() -> new IllegalStateException("审批部门不存在或已删除，departmentId=" + departmentId));
        if (!Integer.valueOf(1).equals(department.getStatus())) {
            throw new IllegalStateException("审批部门已停用，departmentId=" + departmentId);
        }
        if (department.getLeaderUserId() == null) {
            throw new IllegalStateException("审批部门未配置负责人，departmentId=" + departmentId);
        }
        List<Long> leaders = activeDistinct(List.of(department.getLeaderUserId()));
        if (leaders.isEmpty()) {
            throw new IllegalStateException("审批部门负责人不存在或账号已停用，userId="
                    + department.getLeaderUserId());
        }
        return leaders;
    }

    private Long applicantDepartmentId(ProcessInstance instance) {
        UserEntity applicant = applicant(instance);
        return applicant == null ? null : applicant.getDepartmentId();
    }

    private UserEntity applicant(ProcessInstance instance) {
        if (instance == null || instance.getApplicantId() == null) return null;
        UserEntity user = sysUserMapper.selectById(instance.getApplicantId());
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
