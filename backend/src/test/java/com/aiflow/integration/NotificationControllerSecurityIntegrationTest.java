package com.aiflow.integration;

import com.aiflow.common.GlobalExceptionHandler;
import com.aiflow.controller.NotificationController;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.entity.UserEntity;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.NotificationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({NotificationController.class, GlobalExceptionHandler.class,
        NotificationControllerSecurityIntegrationTest.TestSecurityConfiguration.class})
class NotificationControllerSecurityIntegrationTest {

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
    private NotificationService notificationService;

    @Test
    void normalUserCannotForgeOrRewriteNotificationContent() throws Exception {
        UsernamePasswordAuthenticationToken user = currentUserToken(10L, "normal_user");

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(user))
                        .contentType("application/json")
                        .content("{\"receiverId\":20,\"type\":\"system_notice\",\"title\":\"forged\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/notifications/1")
                        .with(authentication(user))
                        .contentType("application/json")
                        .content("{\"title\":\"rewritten\"}"))
                .andExpect(status().isForbidden());

        verify(notificationService, never()).createNotification(any());
        verify(notificationService, never()).updateNotification(any(), any());
    }

    @Test
    void superAdminCanCreateNotification() throws Exception {
        when(notificationService.createNotification(any())).thenReturn(NotificationDTO.builder().id(1L).build());

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(currentUserToken(1L, "super_admin")))
                        .contentType("application/json")
                        .content("{\"receiverId\":20,\"type\":\"system_notice\",\"title\":\"maintenance\"}"))
                .andExpect(status().isOk());

        verify(notificationService).createNotification(any());
    }

    private UsernamePasswordAuthenticationToken currentUserToken(Long userId, String systemRole) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername("user-" + userId);
        entity.setPassword("-");
        entity.setRole("USER");
        entity.setSystemRole(systemRole);
        entity.setEnabled(1);
        String authority = "super_admin".equals(systemRole) ? "ROLE_SUPER_ADMIN" : "ROLE_NORMAL_USER";
        CurrentUser currentUser = new CurrentUser(entity, List.of(new SimpleGrantedAuthority(authority)));
        return new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities());
    }
}
