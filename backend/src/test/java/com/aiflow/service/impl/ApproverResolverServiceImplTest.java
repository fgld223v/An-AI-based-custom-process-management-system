package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.Department;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.service.WorkflowRoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproverResolverServiceImplTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private WorkflowRoleService workflowRoleService;

    private ApproverResolverServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApproverResolverServiceImpl(
                processInstanceRepository,
                departmentRepository,
                sysUserMapper,
                new ObjectMapper(),
                workflowRoleService);
        when(processInstanceRepository.findByIdAndDeleted(100L, 0))
                .thenReturn(Optional.of(ProcessInstance.builder().id(100L).applicantId(10L).build()));
    }

    @Test
    void resolvesDirectSupervisorFromApplicantOrganization() {
        UserEntity applicant = activeUser(10L);
        applicant.setSupervisorId(20L);
        when(sysUserMapper.selectById(10L)).thenReturn(applicant);
        when(sysUserMapper.selectById(20L)).thenReturn(activeUser(20L));

        assertThat(service.resolveApprovers(100L, "Approve", "DIRECT_SUPERVISOR", null))
                .containsExactly(20L);
    }

    @Test
    void resolvesApplicantDepartmentLeader() {
        UserEntity applicant = activeUser(10L);
        applicant.setDepartmentId(6L);
        when(sysUserMapper.selectById(10L)).thenReturn(applicant);
        when(departmentRepository.findByIdAndDeleted(6L, 0)).thenReturn(Optional.of(
                Department.builder().id(6L).leaderUserId(21L).status(1).deleted(0).build()));
        when(sysUserMapper.selectById(21L)).thenReturn(activeUser(21L));

        assertThat(service.resolveApprovers(100L, "Approve", "DEPARTMENT_MANAGER", null))
                .containsExactly(21L);
    }

    @Test
    void resolvesWorkflowRoleInsideApplicantDepartment() {
        UserEntity applicant = activeUser(10L);
        applicant.setDepartmentId(6L);
        when(sysUserMapper.selectById(10L)).thenReturn(applicant);
        when(workflowRoleService.resolveActiveUserIds("FINANCE_APPROVER", 6L))
                .thenReturn(List.of(30L, 31L));

        assertThat(service.resolveApprovers(
                100L, "Approve", "ROLE_IN_APPLICANT_DEPT", "FINANCE_APPROVER"))
                .containsExactly(30L, 31L);
    }

    @Test
    void resolvesWorkflowRoleInsideSpecifiedDepartment() {
        String config = "{\"roleCode\":\"FINANCE_APPROVER\",\"departmentId\":8}";
        when(workflowRoleService.resolveActiveUserIds("FINANCE_APPROVER", 8L))
                .thenReturn(List.of(40L));

        assertThat(service.resolveApprovers(
                100L, "Approve", "ROLE_IN_SPECIFIED_DEPT", config))
                .containsExactly(40L);
        verify(workflowRoleService).resolveActiveUserIds("FINANCE_APPROVER", 8L);
    }

    @Test
    void reportsMissingDepartmentLeaderInsteadOfFallingBackToAdministrator() {
        UserEntity applicant = activeUser(10L);
        applicant.setDepartmentId(6L);
        when(sysUserMapper.selectById(10L)).thenReturn(applicant);
        when(departmentRepository.findByIdAndDeleted(6L, 0)).thenReturn(Optional.of(
                Department.builder().id(6L).status(1).deleted(0).build()));

        assertThatThrownBy(() -> service.resolveApprovers(
                100L, "Approve", "DEPARTMENT_MANAGER", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置负责人")
                .hasMessageContaining("departmentId=6");
    }

    private UserEntity activeUser(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEnabled(1);
        user.setDeleted(0);
        return user;
    }
}
