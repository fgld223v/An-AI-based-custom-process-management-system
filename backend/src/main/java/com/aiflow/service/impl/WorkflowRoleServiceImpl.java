package com.aiflow.service.impl;

import com.aiflow.dto.WorkflowRoleAssignmentDTO;
import com.aiflow.dto.WorkflowRoleAssignmentRequest;
import com.aiflow.dto.WorkflowRoleCreateRequest;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.dto.WorkflowRoleUpdateRequest;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.model.UserWorkflowRole;
import com.aiflow.model.WorkflowRole;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.UserWorkflowRoleRepository;
import com.aiflow.repository.WorkflowRoleRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.WorkflowRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkflowRoleServiceImpl implements WorkflowRoleService {

    private static final String GLOBAL_SCOPE = "global";
    private static final String DEPARTMENT_SCOPE = "department";
    private static final long GLOBAL_DEPARTMENT_ID = 0L;

    private final WorkflowRoleRepository workflowRoleRepository;
    private final UserWorkflowRoleRepository userWorkflowRoleRepository;
    private final SysUserRepository sysUserRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRoleDTO> listRoles(boolean enabledOnly) {
        List<WorkflowRole> roles = enabledOnly
                ? workflowRoleRepository.findByDeletedAndEnabledOrderByRoleNameAsc(0, 1)
                : workflowRoleRepository.findByDeletedOrderByRoleNameAsc(0);
        return roles.stream().map(this::toRoleDto).toList();
    }

    @Override
    public WorkflowRoleDTO createRole(WorkflowRoleCreateRequest request) {
        String roleCode = normalizeRoleCode(request.getRoleCode());
        if (workflowRoleRepository.existsByRoleCode(roleCode)) {
            throw new IllegalArgumentException("流程角色编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        WorkflowRole role = WorkflowRole.builder()
                .roleCode(roleCode)
                .roleName(request.getRoleName().trim())
                .description(normalize(request.getDescription()))
                .roleScope(normalizeScope(request.getRoleScope()))
                .enabled(normalizeEnabled(request.getEnabled(), 1))
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return toRoleDto(workflowRoleRepository.save(role));
    }

    @Override
    public WorkflowRoleDTO updateRole(Long roleId, WorkflowRoleUpdateRequest request) {
        WorkflowRole role = getRequiredRole(roleId);
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(normalize(request.getDescription()));
        role.setEnabled(normalizeEnabled(request.getEnabled(), role.getEnabled()));
        role.setUpdatedAt(LocalDateTime.now());
        return toRoleDto(workflowRoleRepository.save(role));
    }

    @Override
    public void deleteRole(Long roleId) {
        WorkflowRole role = getRequiredRole(roleId);
        LocalDateTime now = LocalDateTime.now();
        role.setDeleted(1);
        role.setEnabled(0);
        role.setUpdatedAt(now);
        workflowRoleRepository.save(role);

        List<UserWorkflowRole> assignments = userWorkflowRoleRepository
                .findByRoleIdAndDeletedOrderByIdAsc(roleId, 0);
        assignments.forEach(item -> {
            item.setDeleted(1);
            item.setUpdatedAt(now);
        });
        userWorkflowRoleRepository.saveAll(assignments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRoleAssignmentDTO> listRoleAssignments(Long roleId) {
        WorkflowRole role = getRequiredRole(roleId);
        return toAssignmentDtos(
                userWorkflowRoleRepository.findByRoleIdAndDeletedOrderByIdAsc(roleId, 0),
                Map.of(role.getId(), role));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRoleAssignmentDTO> listUserAssignments(Long userId) {
        getRequiredUser(userId);
        List<UserWorkflowRole> assignments = userWorkflowRoleRepository
                .findByUserIdAndDeletedOrderByIdAsc(userId, 0);
        Map<Long, WorkflowRole> roles = workflowRoleRepository.findAllById(
                        assignments.stream().map(UserWorkflowRole::getRoleId).distinct().toList())
                .stream()
                .filter(role -> Integer.valueOf(0).equals(role.getDeleted()))
                .collect(Collectors.toMap(WorkflowRole::getId, Function.identity()));
        return toAssignmentDtos(assignments, roles);
    }

    @Override
    public WorkflowRoleAssignmentDTO assignRole(Long roleId, WorkflowRoleAssignmentRequest request) {
        WorkflowRole role = getRequiredRole(roleId);
        if (!Integer.valueOf(1).equals(role.getEnabled())) {
            throw new IllegalStateException("流程角色已停用，不能继续授权");
        }
        SysUser user = getRequiredUser(request.getUserId());
        Long departmentId = resolveAssignmentDepartment(role, request.getDepartmentId());
        if (DEPARTMENT_SCOPE.equals(role.getRoleScope())
                && !Objects.equals(user.getDepartmentId(), departmentId)) {
            throw new IllegalArgumentException("用户必须属于授权部门，不能创建跨部门角色授权");
        }

        UserWorkflowRole existing = userWorkflowRoleRepository
                .findByUserIdAndRoleIdAndDepartmentId(user.getId(), roleId, departmentId)
                .orElse(null);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted())) {
                existing.setDeleted(0);
                existing.setCreatedBy(SecurityUtils.currentUserId());
                existing.setUpdatedAt(LocalDateTime.now());
                existing = userWorkflowRoleRepository.save(existing);
            }
            return toAssignmentDtos(List.of(existing), Map.of(roleId, role)).get(0);
        }

        LocalDateTime now = LocalDateTime.now();
        UserWorkflowRole assignment = UserWorkflowRole.builder()
                .userId(user.getId())
                .roleId(roleId)
                .departmentId(departmentId)
                .createdBy(SecurityUtils.currentUserId())
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return toAssignmentDtos(
                List.of(userWorkflowRoleRepository.save(assignment)), Map.of(roleId, role)).get(0);
    }

    @Override
    public void revokeAssignment(Long assignmentId) {
        UserWorkflowRole assignment = userWorkflowRoleRepository.findByIdAndDeleted(assignmentId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程角色授权不存在"));
        assignment.setDeleted(1);
        assignment.setUpdatedAt(LocalDateTime.now());
        userWorkflowRoleRepository.save(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> resolveActiveUserIds(String roleCode, Long departmentId) {
        WorkflowRole role = workflowRoleRepository
                .findByRoleCodeAndDeleted(normalizeRoleCode(roleCode), 0)
                .filter(item -> Integer.valueOf(1).equals(item.getEnabled()))
                .orElse(null);
        if (role == null) {
            return List.of();
        }
        Long scopeDepartmentId = GLOBAL_SCOPE.equals(role.getRoleScope())
                ? GLOBAL_DEPARTMENT_ID
                : departmentId;
        if (scopeDepartmentId == null || scopeDepartmentId <= 0 && !GLOBAL_SCOPE.equals(role.getRoleScope())) {
            return List.of();
        }
        List<UserWorkflowRole> assignments = userWorkflowRoleRepository
                .findByRoleIdAndDeletedOrderByIdAsc(role.getId(), 0)
                .stream()
                .filter(item -> Objects.equals(scopeDepartmentId, item.getDepartmentId()))
                .toList();
        Map<Long, SysUser> users = sysUserRepository.findAllById(
                        assignments.stream().map(UserWorkflowRole::getUserId).distinct().toList())
                .stream()
                .filter(this::isActiveUser)
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return assignments.stream()
                .map(UserWorkflowRole::getUserId)
                .filter(users::containsKey)
                .distinct()
                .toList();
    }

    private List<WorkflowRoleAssignmentDTO> toAssignmentDtos(
            List<UserWorkflowRole> assignments, Map<Long, WorkflowRole> roles) {
        Map<Long, SysUser> users = sysUserRepository.findAllById(
                        assignments.stream().map(UserWorkflowRole::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<Long> departmentIds = assignments.stream()
                .map(UserWorkflowRole::getDepartmentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, Department> departments = departmentRepository.findAllById(departmentIds)
                .stream().collect(Collectors.toMap(Department::getId, Function.identity()));

        return assignments.stream()
                .filter(item -> roles.containsKey(item.getRoleId()))
                .map(item -> {
                    WorkflowRole role = roles.get(item.getRoleId());
                    SysUser user = users.get(item.getUserId());
                    Department department = departments.get(item.getDepartmentId());
                    return WorkflowRoleAssignmentDTO.builder()
                            .id(item.getId())
                            .roleId(role.getId())
                            .roleCode(role.getRoleCode())
                            .roleName(role.getRoleName())
                            .roleScope(role.getRoleScope())
                            .userId(item.getUserId())
                            .username(user == null ? null : user.getUsername())
                            .userName(resolveUserName(user, item.getUserId()))
                            .departmentId(item.getDepartmentId() != null && item.getDepartmentId() > 0
                                    ? item.getDepartmentId() : null)
                            .departmentName(department == null ? null : department.getDeptName())
                            .createdBy(item.getCreatedBy())
                            .createdAt(item.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private WorkflowRoleDTO toRoleDto(WorkflowRole role) {
        return WorkflowRoleDTO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .roleScope(role.getRoleScope())
                .enabled(role.getEnabled())
                .memberCount(userWorkflowRoleRepository.countByRoleIdAndDeleted(role.getId(), 0))
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private Long resolveAssignmentDepartment(WorkflowRole role, Long requestedDepartmentId) {
        if (GLOBAL_SCOPE.equals(role.getRoleScope())) {
            if (requestedDepartmentId != null && requestedDepartmentId > 0) {
                throw new IllegalArgumentException("全局流程角色不能指定部门");
            }
            return GLOBAL_DEPARTMENT_ID;
        }
        if (requestedDepartmentId == null || requestedDepartmentId <= 0) {
            throw new IllegalArgumentException("部门范围流程角色必须指定部门");
        }
        Department department = departmentRepository.findByIdAndDeleted(requestedDepartmentId, 0)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
        if (!Integer.valueOf(1).equals(department.getStatus())) {
            throw new IllegalStateException("部门已停用，不能授权流程角色");
        }
        return department.getId();
    }

    private WorkflowRole getRequiredRole(Long roleId) {
        if (roleId == null) throw new IllegalArgumentException("roleId must not be null");
        return workflowRoleRepository.findByIdAndDeleted(roleId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程角色不存在"));
    }

    private SysUser getRequiredUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (Integer.valueOf(1).equals(user.getDeleted())) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private boolean isActiveUser(SysUser user) {
        return user != null
                && !Integer.valueOf(1).equals(user.getDeleted())
                && Integer.valueOf(1).equals(user.getEnabled());
    }

    private String resolveUserName(SysUser user, Long userId) {
        if (user == null) return "用户#" + userId;
        if (hasText(user.getNickname())) return user.getNickname().trim();
        return hasText(user.getUsername()) ? user.getUsername().trim() : "用户#" + userId;
    }

    private String normalizeRoleCode(String value) {
        if (!hasText(value)) throw new IllegalArgumentException("roleCode must not be blank");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScope(String value) {
        String scope = hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
        if (!List.of(GLOBAL_SCOPE, DEPARTMENT_SCOPE).contains(scope)) {
            throw new IllegalArgumentException("roleScope must be global or department");
        }
        return scope;
    }

    private Integer normalizeEnabled(Integer value, Integer fallback) {
        Integer resolved = value == null ? fallback : value;
        if (!Integer.valueOf(0).equals(resolved) && !Integer.valueOf(1).equals(resolved)) {
            throw new IllegalArgumentException("enabled must be 0 or 1");
        }
        return resolved;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
