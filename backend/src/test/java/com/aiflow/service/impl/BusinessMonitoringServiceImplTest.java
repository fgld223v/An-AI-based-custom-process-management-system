package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.InstanceAnomalyService;
import com.aiflow.service.ProcessTimelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessMonitoringServiceImplTest {

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private ProcessTemplateRepository processTemplateRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private FormSubmissionRepository formSubmissionRepository;

    @Mock
    private ProcessTimelineService processTimelineService;

    @Mock
    private InstanceAnomalyService instanceAnomalyService;

    @InjectMocks
    private BusinessMonitoringServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsesCurrentUserAsTemplateCreatorScope() {
        authenticate(10L, "biz_admin");
        ProcessInstance instance = instance();
        when(processInstanceRepository.listInstancesOwnedByTemplateCreator(
                10L, ProcessResourceType.BUSINESS_PROCESS, 3L, "running", "leave"))
                .thenReturn(List.of(instance));
        when(processTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
        when(sysUserRepository.findAllById(any())).thenReturn(List.of(applicant()));
        when(instanceAnomalyService.findAnomalies(any())).thenReturn(Map.of());

        var result = service.listOwnedProcessInstances(3L, " running ", " leave ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateName()).isEqualTo("Leave flow");
        assertThat(result.get(0).getApplicantName()).isEqualTo("Alice");
        verify(processInstanceRepository).listInstancesOwnedByTemplateCreator(
                10L, ProcessResourceType.BUSINESS_PROCESS, 3L, "running", "leave");
    }

    @Test
    void normalUserCannotUseBusinessMonitoringQuery() {
        authenticate(20L, "normal_user");

        assertThatThrownBy(() -> service.listOwnedProcessInstances(null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(processInstanceRepository, never()).listInstancesOwnedByTemplateCreator(
                any(), any(), any(), any(), any());
    }

    @Test
    void detailOutsideOwnedTemplateScopeIsDenied() {
        authenticate(10L, "biz_admin");
        when(processInstanceRepository.findOwnedInstance(
                99L, 10L, ProcessResourceType.BUSINESS_PROCESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedProcessInstance(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no permission");
    }

    @Test
    void superAdministratorCanQueryGlobalInstances() {
        authenticate(1L, "super_admin");
        when(processInstanceRepository.listGlobalProcessInstances(null, null, null)).thenReturn(List.of(instance()));
        when(processTemplateRepository.findAllById(any())).thenReturn(List.of(template()));
        when(sysUserRepository.findAllById(any())).thenReturn(List.of(applicant()));
        when(instanceAnomalyService.findAnomalies(any())).thenReturn(Map.of(100L, "任务超时"));

        var result = service.listGlobalProcessInstances(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnomaly()).isTrue();
        verify(processInstanceRepository).listGlobalProcessInstances(null, null, null);
    }

    private ProcessInstance instance() {
        return ProcessInstance.builder()
                .id(100L)
                .instanceCode("PI_100")
                .templateId(3L)
                .formId(8L)
                .applicantId(20L)
                .bizTypeId(2L)
                .title("Annual leave")
                .status("running")
                .currentNodeKey("Task_Approve")
                .currentNodeName("Manager approval")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();
    }

    private ProcessTemplate template() {
        return ProcessTemplate.builder()
                .id(3L)
                .templateCode("leave-flow")
                .templateName("Leave flow")
                .version(2)
                .status(TemplateStatus.PUBLISHED)
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .createdBy(10L)
                .deleted(0)
                .build();
    }

    private SysUser applicant() {
        return SysUser.builder()
                .id(20L)
                .username("alice")
                .nickname("Alice")
                .departmentId(4L)
                .enabled(1)
                .deleted(0)
                .build();
    }

    private void authenticate(Long id, String systemRole) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user-" + id);
        entity.setPassword("-");
        entity.setSystemRole(systemRole);
        entity.setRole("biz_admin".equals(systemRole) ? "MANAGER" : "USER");
        entity.setEnabled(1);
        CurrentUser user = new CurrentUser(entity,
                List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
