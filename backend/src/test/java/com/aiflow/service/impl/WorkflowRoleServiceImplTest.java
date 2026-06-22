package com.aiflow.service.impl;

import com.aiflow.dto.WorkflowRoleAssignmentRequest;
import com.aiflow.dto.WorkflowRoleCreateRequest;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.model.UserWorkflowRole;
import com.aiflow.model.WorkflowRole;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.UserWorkflowRoleRepository;
import com.aiflow.repository.WorkflowRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRoleServiceImplTest {

    @Mock
    private WorkflowRoleRepository workflowRoleRepository;

    @Mock
    private UserWorkflowRoleRepository userWorkflowRoleRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private WorkflowRoleServiceImpl service;

    @Test
    void createRoleNormalizesStableCodeAndScope() {
        WorkflowRoleCreateRequest request = new WorkflowRoleCreateRequest();
        request.setRoleCode("finance_approver");
        request.setRoleName("财务审批人");
        request.setRoleScope("DEPARTMENT");
        when(workflowRoleRepository.existsByRoleCode("FINANCE_APPROVER")).thenReturn(false);
        when(workflowRoleRepository.save(any())).thenAnswer(invocation -> {
            WorkflowRole role = invocation.getArgument(0);
            role.setId(8L);
            return role;
        });
        when(userWorkflowRoleRepository.countByRoleIdAndDeleted(8L, 0)).thenReturn(0L);

        var result = service.createRole(request);

        assertThat(result.getRoleCode()).isEqualTo("FINANCE_APPROVER");
        assertThat(result.getRoleScope()).isEqualTo("department");
        assertThat(result.getEnabled()).isEqualTo(1);
    }

    @Test
    void departmentRoleRequiresDepartmentAssignment() {
        WorkflowRole role = role(3L, "DEPT_APPROVER", "department");
        when(workflowRoleRepository.findByIdAndDeleted(3L, 0)).thenReturn(Optional.of(role));
        when(sysUserRepository.findById(10L)).thenReturn(Optional.of(user(10L, 1)));
        WorkflowRoleAssignmentRequest request = new WorkflowRoleAssignmentRequest();
        request.setUserId(10L);

        assertThatThrownBy(() -> service.assignRole(3L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须指定部门");
    }

    @Test
    void assignsDepartmentScopedRoleToUser() {
        WorkflowRole role = role(3L, "DEPT_APPROVER", "department");
        SysUser user = user(10L, 1);
        Department department = Department.builder()
                .id(6L).deptName("财务部").status(1).deleted(0).build();
        when(workflowRoleRepository.findByIdAndDeleted(3L, 0)).thenReturn(Optional.of(role));
        when(sysUserRepository.findById(10L)).thenReturn(Optional.of(user));
        when(departmentRepository.findByIdAndDeleted(6L, 0)).thenReturn(Optional.of(department));
        when(userWorkflowRoleRepository.findByUserIdAndRoleIdAndDepartmentId(10L, 3L, 6L))
                .thenReturn(Optional.empty());
        when(userWorkflowRoleRepository.save(any())).thenAnswer(invocation -> {
            UserWorkflowRole assignment = invocation.getArgument(0);
            assignment.setId(20L);
            return assignment;
        });
        when(sysUserRepository.findAllById(any())).thenReturn(List.of(user));
        when(departmentRepository.findAllById(any())).thenReturn(List.of(department));
        WorkflowRoleAssignmentRequest request = new WorkflowRoleAssignmentRequest();
        request.setUserId(10L);
        request.setDepartmentId(6L);

        var result = service.assignRole(3L, request);

        assertThat(result.getRoleCode()).isEqualTo("DEPT_APPROVER");
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getDepartmentId()).isEqualTo(6L);
        assertThat(result.getDepartmentName()).isEqualTo("财务部");
    }

    @Test
    void resolverReturnsOnlyEnabledUsersInRequestedDepartment() {
        WorkflowRole role = role(3L, "DEPT_APPROVER", "department");
        UserWorkflowRole first = assignment(1L, 10L, 3L, 6L);
        UserWorkflowRole second = assignment(2L, 11L, 3L, 6L);
        UserWorkflowRole otherDepartment = assignment(3L, 12L, 3L, 7L);
        when(workflowRoleRepository.findByRoleCodeAndDeleted("DEPT_APPROVER", 0))
                .thenReturn(Optional.of(role));
        when(userWorkflowRoleRepository.findByRoleIdAndDeletedOrderByIdAsc(3L, 0))
                .thenReturn(List.of(first, second, otherDepartment));
        when(sysUserRepository.findAllById(any())).thenReturn(List.of(user(10L, 1), user(11L, 0)));

        assertThat(service.resolveActiveUserIds("dept_approver", 6L)).containsExactly(10L);
    }

    private WorkflowRole role(Long id, String code, String scope) {
        return WorkflowRole.builder()
                .id(id)
                .roleCode(code)
                .roleName(code)
                .roleScope(scope)
                .enabled(1)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SysUser user(Long id, Integer enabled) {
        return SysUser.builder()
                .id(id)
                .username("user-" + id)
                .nickname("User " + id)
                .enabled(enabled)
                .deleted(0)
                .build();
    }

    private UserWorkflowRole assignment(Long id, Long userId, Long roleId, Long departmentId) {
        return UserWorkflowRole.builder()
                .id(id)
                .userId(userId)
                .roleId(roleId)
                .departmentId(departmentId)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
