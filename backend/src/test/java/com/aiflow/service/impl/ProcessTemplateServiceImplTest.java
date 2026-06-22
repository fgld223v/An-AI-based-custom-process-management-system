package com.aiflow.service.impl;

import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.Department;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.FlowableDeploymentService;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.WorkflowRoleService;
import com.aiflow.dto.WorkflowRoleDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessTemplateServiceImplTest {

    @Mock
    private ProcessTemplateRepository processTemplateRepository;

    @Mock
    private FormDefinitionRepository formDefinitionRepository;

    @Mock
    private FlowableDeploymentService flowableDeploymentService;

    @Mock
    private ProcessAuthorizationService processAuthorizationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FormBindConfigParser formBindConfigParser;

    @Mock
    private NodeConfigParser nodeConfigParser;

    @Mock
    private WorkflowRoleService workflowRoleService;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @InjectMocks
    private ProcessTemplateServiceImpl service;

    @Test
    void createTemplateDefaultsToSystemTemplate() {
        ProcessTemplate input = ProcessTemplate.builder()
                .templateCode("leave-template")
                .templateName("Leave template")
                .build();
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessTemplate saved = service.createTemplate(input);

        assertThat(saved.getResourceType()).isEqualTo(ProcessResourceType.SYSTEM_TEMPLATE);
    }

    @Test
    void listTemplatesOnlyReturnsSystemTemplates() {
        service.listTemplates();

        verify(processTemplateRepository)
                .findByResourceTypeAndDeletedOrderByUpdatedAtDesc(ProcessResourceType.SYSTEM_TEMPLATE, 0);
    }

    @Test
    void listTemplatesByCreatorOnlyReturnsBusinessProcesses() {
        service.listTemplatesByCreatedBy(12L);

        verify(processTemplateRepository)
                .findByCreatedByAndResourceTypeAndDeletedOrderByUpdatedAtDesc(
                        12L, ProcessResourceType.BUSINESS_PROCESS, 0);
    }

    @Test
    void catalogOnlyReturnsPublishedAndDeployedBusinessProcesses() {
        when(processTemplateRepository
                .findByResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeletedOrderByUpdatedAtDesc(
                        ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0))
                .thenReturn(List.of());

        assertThat(service.listPublishedBusinessProcesses()).isEmpty();

        verify(processTemplateRepository)
                .findByResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeletedOrderByUpdatedAtDesc(
                        ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0);
    }

    @Test
    void createNextVersionCopiesPublishedVersionAsDraft() {
        ProcessTemplate published = version(1L, 1, TemplateStatus.PUBLISHED);
        when(processTemplateRepository.findByIdAndDeleted(1L, 0)).thenReturn(java.util.Optional.of(published));
        when(processTemplateRepository.findByTemplateCodeAndResourceTypeAndDeletedOrderByVersionDesc(
                "leave-flow", ProcessResourceType.BUSINESS_PROCESS, 0)).thenReturn(List.of(published));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessTemplate draft = service.createNextVersion(1L);

        assertThat(draft.getVersion()).isEqualTo(2);
        assertThat(draft.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(draft.getFlowableDeploymentId()).isNull();
        assertThat(draft.getBpmnXml()).isEqualTo(published.getBpmnXml());
    }

    @Test
    void createNextVersionReusesExistingDraft() {
        ProcessTemplate published = version(1L, 1, TemplateStatus.PUBLISHED);
        ProcessTemplate draft = version(2L, 2, TemplateStatus.DRAFT);
        when(processTemplateRepository.findByIdAndDeleted(1L, 0)).thenReturn(java.util.Optional.of(published));
        when(processTemplateRepository.findByTemplateCodeAndResourceTypeAndDeletedOrderByVersionDesc(
                "leave-flow", ProcessResourceType.BUSINESS_PROCESS, 0)).thenReturn(List.of(draft, published));

        assertThat(service.createNextVersion(1L)).isSameAs(draft);
        verify(processTemplateRepository, never()).save(any(ProcessTemplate.class));
    }

    @Test
    void publishingNewVersionDisablesPreviousPublishedVersion() {
        ProcessTemplate previous = version(1L, 1, TemplateStatus.PUBLISHED);
        ProcessTemplate draft = version(2L, 2, TemplateStatus.DRAFT);
        when(processTemplateRepository.findByIdAndDeleted(2L, 0)).thenReturn(java.util.Optional.of(draft));
        when(processTemplateRepository.findByTemplateCodeAndResourceTypeAndStatusAndDeleted(
                "leave-flow", ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0))
                .thenReturn(List.of(previous));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessTemplate published = service.publishTemplate(2L);

        assertThat(published.getStatus()).isEqualTo(TemplateStatus.PUBLISHED);
        assertThat(previous.getStatus()).isEqualTo(TemplateStatus.DISABLED);
        assertThat(previous.getFlowableDeploymentId()).isEqualTo("deployment-1");
    }

    @Test
    void disablingPublishedVersionKeepsDeploymentInformation() {
        ProcessTemplate published = version(1L, 1, TemplateStatus.PUBLISHED);
        when(processTemplateRepository.findByIdAndDeleted(1L, 0)).thenReturn(java.util.Optional.of(published));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessTemplate disabled = service.unpublishTemplate(1L);

        assertThat(disabled.getStatus()).isEqualTo(TemplateStatus.DISABLED);
        assertThat(disabled.getFlowableDeploymentId()).isEqualTo("deployment-1");
        assertThat(disabled.getFlowableProcessDefinitionId()).isEqualTo("definition-1");
    }

    @Test
    void publishingRejectsMissingDepartmentWorkflowRole() {
        ProcessTemplate draft = version(2L, 2, TemplateStatus.DRAFT);
        draft.setNodeConfig("{}");
        when(processTemplateRepository.findByIdAndDeleted(2L, 0))
                .thenReturn(java.util.Optional.of(draft));
        when(nodeConfigParser.asOrderedList("{}")).thenReturn(List.of(java.util.Map.of(
                "businessType", "approval",
                "nodeName", "Finance approval",
                "assignStrategy", "ROLE_IN_APPLICANT_DEPT",
                "assignValue", "FINANCE_APPROVER")));
        when(workflowRoleService.listRoles(true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishTemplate(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINANCE_APPROVER");
    }

    @Test
    void publishingAcceptsEnabledRoleWithMatchingScope() {
        ProcessTemplate draft = version(2L, 2, TemplateStatus.DRAFT);
        draft.setNodeConfig("{}");
        when(processTemplateRepository.findByIdAndDeleted(2L, 0))
                .thenReturn(java.util.Optional.of(draft));
        when(processTemplateRepository.findByTemplateCodeAndResourceTypeAndStatusAndDeleted(
                "leave-flow", ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0))
                .thenReturn(List.of());
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeConfigParser.asOrderedList("{}")).thenReturn(List.of(java.util.Map.of(
                "businessType", "approval",
                "nodeName", "Finance approval",
                "assignStrategy", "ROLE_IN_APPLICANT_DEPT",
                "assignValue", "FINANCE_APPROVER")));
        when(workflowRoleService.listRoles(true)).thenReturn(List.of(WorkflowRoleDTO.builder()
                .roleCode("FINANCE_APPROVER").roleScope("department").enabled(1).build()));

        assertThat(service.publishTemplate(2L).getStatus()).isEqualTo(TemplateStatus.PUBLISHED);
    }

    @Test
    void publishingRejectsSpecifiedDepartmentRoleWithoutActiveMembers() {
        ProcessTemplate draft = version(2L, 2, TemplateStatus.DRAFT);
        draft.setNodeConfig("{}");
        String assignValue = "{\"departmentId\":8,\"roleCode\":\"FINANCE_APPROVER\"}";
        when(processTemplateRepository.findByIdAndDeleted(2L, 0))
                .thenReturn(java.util.Optional.of(draft));
        when(nodeConfigParser.asOrderedList("{}")).thenReturn(List.of(java.util.Map.of(
                "businessType", "approval",
                "nodeName", "Finance approval",
                "assignStrategy", "ROLE_IN_SPECIFIED_DEPT",
                "assignValue", assignValue)));
        when(workflowRoleService.listRoles(true)).thenReturn(List.of(WorkflowRoleDTO.builder()
                .roleCode("FINANCE_APPROVER").roleScope("department").enabled(1).build()));
        when(departmentRepository.findByIdAndDeleted(8L, 0)).thenReturn(java.util.Optional.of(
                Department.builder().id(8L).status(1).deleted(0).build()));
        when(workflowRoleService.resolveActiveUserIds("FINANCE_APPROVER", 8L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishTemplate(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有有效的流程角色成员");
    }

    private ProcessTemplate version(Long id, int version, TemplateStatus status) {
        return ProcessTemplate.builder()
                .id(id)
                .templateCode("leave-flow")
                .templateName("Leave flow")
                .version(version)
                .status(status)
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .sourceType(com.aiflow.enums.TemplateSourceType.MANUAL)
                .bizTypeId(2L)
                .createdBy(12L)
                .bpmnXml("<bpmn:definitions><bpmn:process /></bpmn:definitions>")
                .nodeConfig(null)
                .flowableDeploymentId("deployment-1")
                .flowableProcessDefinitionId("definition-1")
                .deleted(0)
                .build();
    }
}
