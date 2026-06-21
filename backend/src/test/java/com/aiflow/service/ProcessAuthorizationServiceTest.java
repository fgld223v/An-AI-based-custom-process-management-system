package com.aiflow.service;

import com.aiflow.entity.UserEntity;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.impl.NodeConfigParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessAuthorizationServiceTest {

    private final ProcessAuthorizationService service = new ProcessAuthorizationService(
            new NodeConfigParser(new ObjectMapper()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanPublishBusinessProcessInsideManagedScope() {
        authenticate(10L, "biz_admin", "MANAGER", 2L, "[2,3]");
        ProcessTemplate process = businessProcess(TemplateStatus.DRAFT, "ALL", null);

        assertThatCode(() -> service.assertCanPublish(process)).doesNotThrowAnyException();
    }

    @Test
    void nonOwnerCannotPublishBusinessProcess() {
        authenticate(11L, "biz_admin", "MANAGER", 2L, "[2,3]");
        ProcessTemplate process = businessProcess(TemplateStatus.DRAFT, "ALL", null);

        assertThatThrownBy(() -> service.assertCanPublish(process))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void rolePermissionAllowsMatchingUserAndRejectsOthers() {
        ProcessTemplate process = businessProcess(TemplateStatus.PUBLISHED, "ROLE", "normal_user");
        authenticate(20L, "normal_user", "USER", 3L, null);
        assertThatCode(() -> service.assertCanStart(process)).doesNotThrowAnyException();

        authenticate(21L, "biz_admin", "MANAGER", 3L, "[2]");
        assertThatThrownBy(() -> service.assertCanStart(process))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("role");
    }

    @Test
    void departmentPermissionRequiresMatchingDepartment() {
        ProcessTemplate process = businessProcess(TemplateStatus.PUBLISHED, "DEPARTMENT", "2,3");
        authenticate(20L, "normal_user", "USER", 4L, null);

        assertThatThrownBy(() -> service.assertCanStart(process))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("department");
    }

    @Test
    void unpublishedOrUndeployedProcessCannotBeStarted() {
        authenticate(20L, "normal_user", "USER", 3L, null);
        ProcessTemplate process = businessProcess(TemplateStatus.DRAFT, "ALL", null);

        assertThatThrownBy(() -> service.assertCanStart(process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not published");

        process.setStatus(TemplateStatus.PUBLISHED);
        process.setFlowableDeploymentId(null);
        assertThatThrownBy(() -> service.assertCanStart(process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not deployed");
    }

    @Test
    void restrictedPermissionMustHaveConfiguredValueBeforePublishing() {
        authenticate(10L, "biz_admin", "MANAGER", 2L, "[2,3]");
        ProcessTemplate process = businessProcess(TemplateStatus.DRAFT, "ROLE", null);

        assertThatThrownBy(() -> service.assertCanPublish(process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startPermissionValue");
    }

    private ProcessTemplate businessProcess(TemplateStatus status, String permission, String permissionValue) {
        String valueField = permissionValue == null ? "" : ",\"startPermissionValue\":\"" + permissionValue + "\"";
        String nodeConfig = "{\"StartEvent_1\":{\"nodeId\":\"StartEvent_1\",\"businessType\":\"start\","
                + "\"bpmnType\":\"bpmn:StartEvent\",\"startMode\":\"MANUAL\","
                + "\"startPermission\":\"" + permission + "\"" + valueField + "}}";
        return ProcessTemplate.builder()
                .id(1L)
                .templateCode("leave-flow")
                .templateName("Leave flow")
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .status(status)
                .bizTypeId(2L)
                .createdBy(10L)
                .nodeConfig(nodeConfig)
                .bpmnXml("<bpmn:definitions><bpmn:process /></bpmn:definitions>")
                .flowableDeploymentId("deployment-1")
                .flowableProcessDefinitionId("definition-1")
                .build();
    }

    private void authenticate(Long id, String systemRole, String role, Long departmentId, String managedBizTypeIds) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user-" + id);
        entity.setPassword("-");
        entity.setSystemRole(systemRole);
        entity.setRole(role);
        entity.setDepartmentId(departmentId);
        entity.setManagedBizTypeIds(managedBizTypeIds);
        entity.setEnabled(1);
        CurrentUser user = new CurrentUser(entity, List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
