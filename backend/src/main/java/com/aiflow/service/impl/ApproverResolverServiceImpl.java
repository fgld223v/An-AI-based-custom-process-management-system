package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.Department;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.service.ApproverResolverService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Override
    public List<Long> resolveApprovers(Long instanceId, String taskDefinitionKey,
                                       String assignStrategy, String assignValue) {
        if (assignStrategy == null || assignStrategy.isBlank()) {
            return Collections.emptyList();
        }

        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));

        return switch (assignStrategy.toUpperCase()) {
            case "DEPARTMENT_MANAGER" -> resolveDeptManager(instance);
            case "SPECIFIC_USERS" -> resolveSpecificUsers(assignValue);
            case "DIRECT_SUPERVISOR" -> resolveSupervisor(instance);
            case "DEPARTMENT" -> resolveDeptManager(instance);
            case "ROLE" -> resolveRoleMembers(assignValue);
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
        Long applicantId = instance.getApplicantId();
        if (applicantId == null) return defaultApprover();

        // 查询发起人的用户记录获取 departmentId
        UserEntity applicant = sysUserMapper.selectById(applicantId);
        if (applicant == null || applicant.getDepartmentId() == null) return defaultApprover();

        // 查询部门获取 leader
        Department dept = departmentRepository.findByIdAndDeleted(applicant.getDepartmentId(), 0).orElse(null);
        if (dept != null && dept.getLeaderUserId() != null) {
            return List.of(dept.getLeaderUserId());
        }
        return defaultApprover();
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
            return raw.stream()
                    .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : null)
                    .filter(id -> id != null)
                    .toList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * 直属上级 — 查询发起人的 supervisor_id。
     */
    private List<Long> resolveSupervisor(ProcessInstance instance) {
        Long applicantId = instance.getApplicantId();
        if (applicantId == null) return resolveDeptManager(instance);
        UserEntity applicant = sysUserMapper.selectById(applicantId);
        if (applicant != null && applicant.getSupervisorId() != null) {
            return List.of(applicant.getSupervisorId());
        }
        return resolveDeptManager(instance); // 无上级时回退到部门经理
    }

    /**
     * 角色成员 — 查询拥有指定角色的所有用户。
     * assignValue: "ADMIN" / "MANAGER" / "USER"
     */
    private List<Long> resolveRoleMembers(String assignValue) {
        if (assignValue == null || assignValue.isBlank()) {
            return Collections.emptyList();
        }
        List<UserEntity> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getRole, assignValue)
                        .eq(UserEntity::getDeleted, 0));
        return users.stream().map(UserEntity::getId).toList();
    }

    /**
     * 兜底 — 返回系统管理员。
     */
    private List<Long> defaultApprover() {
        List<UserEntity> admins = sysUserMapper.selectList(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getRole, "ADMIN")
                        .eq(UserEntity::getDeleted, 0)
                        .last("LIMIT 1"));
        if (!admins.isEmpty()) {
            return List.of(admins.get(0).getId());
        }
        // 最终兜底
        return List.of(1L);
    }
}
