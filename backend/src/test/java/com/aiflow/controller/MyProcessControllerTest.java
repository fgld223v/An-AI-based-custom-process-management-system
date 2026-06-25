package com.aiflow.controller;

import com.aiflow.entity.UserEntity;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.ProcessTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyProcessControllerTest {

    private final ProcessTemplateService processTemplateService = mock(ProcessTemplateService.class);
    private final MyProcessController controller = new MyProcessController(processTemplateService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanDeleteBusinessProcessVersion() {
        authenticate(12L);
        when(processTemplateService.findById(3L)).thenReturn(Optional.of(process(3L, 12L)));

        controller.deleteMyProcess(3L);

        verify(processTemplateService).deleteTemplate(3L);
    }

    @Test
    void nonOwnerCannotDeleteBusinessProcessVersion() {
        authenticate(12L);
        when(processTemplateService.findById(3L)).thenReturn(Optional.of(process(3L, 99L)));

        assertThatThrownBy(() -> controller.deleteMyProcess(3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminCannotUseMyProcessEndpoints() {
        authenticate(1L, "super_admin", "ADMIN", "ROLE_SUPER_ADMIN");

        assertThatThrownBy(controller::listMyProcesses)
                .isInstanceOf(AccessDeniedException.class);
    }

    private ProcessTemplate process(Long id, Long createdBy) {
        return ProcessTemplate.builder()
                .id(id)
                .createdBy(createdBy)
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .status(TemplateStatus.DRAFT)
                .deleted(0)
                .build();
    }

    private void authenticate(Long id) {
        authenticate(id, "biz_admin", "MANAGER", "ROLE_BIZ_ADMIN");
    }

    private void authenticate(Long id, String systemRole, String legacyRole, String authority) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user-" + id);
        entity.setPassword("-");
        entity.setSystemRole(systemRole);
        entity.setRole(legacyRole);
        entity.setEnabled(1);
        CurrentUser user = new CurrentUser(entity, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
