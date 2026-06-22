package com.aiflow.integration;

import com.aiflow.common.GlobalExceptionHandler;
import com.aiflow.controller.TaskController;
import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.entity.UserEntity;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.TaskQueryService;
import com.aiflow.service.TaskRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({TaskController.class, GlobalExceptionHandler.class,
        TaskControllerSecurityIntegrationTest.TestSecurityConfiguration.class})
class TaskControllerSecurityIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class WebTestApplication {
    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.sendError(HttpStatus.UNAUTHORIZED.value())))
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskQueryService taskQueryService;

    @MockBean
    private TaskRuntimeService taskRuntimeService;

    @Test
    void unauthenticatedUserCannotReadTodoTasks() throws Exception {
        mockMvc.perform(get("/api/tasks/my"))
                .andExpect(status().isUnauthorized());

        verify(taskQueryService, never()).listMyTasks();
    }

    @Test
    void authenticatedUserIdentityReachesTaskService() throws Exception {
        when(taskQueryService.listMyTasks()).thenAnswer(invocation -> {
            assertThat(SecurityUtils.currentUserId()).isEqualTo(20L);
            return List.of();
        });

        mockMvc.perform(get("/api/tasks/my").with(authentication(currentUserToken(20L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(taskQueryService).listMyTasks();
    }

    @Test
    void validApprovalRequestIsValidatedAndDelegatedWithCurrentIdentity() throws Exception {
        when(taskRuntimeService.completeTask(eq("task-1"), any(TaskCompleteRequest.class)))
                .thenAnswer(invocation -> {
                    assertThat(SecurityUtils.currentUserId()).isEqualTo(20L);
                    TaskCompleteRequest request = invocation.getArgument(1);
                    assertThat(request.getInstanceId()).isEqualTo(100L);
                    assertThat(request.getNodeKey()).isEqualTo("Approve_1");
                    assertThat(request.getFormData()).containsEntry("approvalResult", "agree");
                    return null;
                });

        mockMvc.perform(post("/api/tasks/task-1/complete")
                        .with(authentication(currentUserToken(20L)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "instanceId": 100,
                                  "nodeKey": "Approve_1",
                                  "formData": {
                                    "approvalResult": "agree",
                                    "approvalComment": "approved"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskRuntimeService).completeTask(eq("task-1"), any(TaskCompleteRequest.class));
    }

    @Test
    void invalidApprovalRequestIsRejectedBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/tasks/task-1/complete")
                        .with(authentication(currentUserToken(20L)))
                        .contentType("application/json")
                        .content("""
                                {"instanceId": 100, "formData": {"approvalResult": "agree"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nodeKey")));

        verify(taskRuntimeService, never()).completeTask(any(), any());
    }

    @Test
    void servicePermissionDenialIsReturnedAsForbidden() throws Exception {
        when(taskQueryService.getTask("task-2"))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("not assignee"));

        mockMvc.perform(get("/api/tasks/task-2").with(authentication(currentUserToken(21L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private UsernamePasswordAuthenticationToken currentUserToken(Long userId) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername("user-" + userId);
        entity.setPassword("-");
        entity.setRole("USER");
        entity.setSystemRole("normal_user");
        entity.setEnabled(1);
        CurrentUser currentUser = new CurrentUser(entity,
                List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        return new UsernamePasswordAuthenticationToken(
                currentUser, null, currentUser.getAuthorities());
    }
}
