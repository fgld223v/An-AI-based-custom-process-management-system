package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.enums.FormStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.security.CurrentUser;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormDefinitionServiceImplTest {

    @Mock
    private FormDefinitionRepository repository;

    @InjectMocks
    private FormDefinitionServiceImpl service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAssignsAuthenticatedUserAsOwner() {
        authenticate(12L, "biz_admin");
        when(repository.save(any(FormDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FormDefinition saved = service.createForm(FormDefinition.builder()
                .formCode("expense")
                .formName("Expense")
                .build());

        assertThat(saved.getCreatedBy()).isEqualTo(12L);
        assertThat(saved.getSourceType()).isEqualTo("manual");
    }

    @Test
    void businessAdministratorListsOnlyOwnedForms() {
        authenticate(12L, "biz_admin");

        service.listForms();

        verify(repository).findByCreatedByAndDeletedOrderByUpdatedAtDesc(12L, 0);
    }

    @Test
    void businessAdministratorCannotModifyAnotherUsersForm() {
        authenticate(12L, "biz_admin");
        FormDefinition otherUsersForm = FormDefinition.builder()
                .id(5L).createdBy(99L).status(FormStatus.DRAFT).build();
        when(repository.findById(5L)).thenReturn(Optional.of(otherUsersForm));

        assertThatThrownBy(() -> service.updateForm(5L, FormDefinition.builder().formName("Changed").build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdministratorListsAllForms() {
        authenticate(1L, "super_admin");

        service.listForms();

        verify(repository).findByDeletedOrderByUpdatedAtDesc(0);
    }

    private void authenticate(Long id, String systemRole) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user-" + id);
        entity.setPassword("-");
        entity.setSystemRole(systemRole);
        entity.setRole("USER");
        entity.setEnabled(1);
        CurrentUser user = new CurrentUser(entity,
                List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
