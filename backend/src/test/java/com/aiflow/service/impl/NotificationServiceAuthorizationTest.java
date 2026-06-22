package com.aiflow.service.impl;

import com.aiflow.dto.NotificationUpdateRequest;
import com.aiflow.entity.UserEntity;
import com.aiflow.model.Notification;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceAuthorizationTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void normalUserCannotQueryAnotherUsersNotifications() {
        authenticate(10L, "normal_user");
        NotificationServiceImpl service = service();

        assertThatThrownBy(() -> service.listNotifications(20L, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(notificationRepository, never()).listNotifications(20L, null, null, null);
    }

    @Test
    void normalUserCannotReadOrModifyAnotherUsersNotification() {
        authenticate(10L, "normal_user");
        Notification notification = Notification.builder()
                .id(1L)
                .receiverId(20L)
                .isRead(false)
                .deleted(0)
                .build();
        when(notificationRepository.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(notification));
        NotificationServiceImpl service = service();

        assertThatThrownBy(() -> service.getNotification(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.markRead(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteNotification(1L)).isInstanceOf(AccessDeniedException.class);

        verify(notificationRepository, never()).save(notification);
    }

    @Test
    void ownerCanReadAndMarkNotificationRead() {
        authenticate(10L, "normal_user");
        Notification notification = Notification.builder()
                .id(1L)
                .receiverId(10L)
                .type("task_remind")
                .title("Approval required")
                .isRead(false)
                .deleted(0)
                .build();
        when(notificationRepository.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        assertThat(service().markRead(1L).getIsRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void normalUserCannotRewriteNotificationContent() {
        authenticate(10L, "normal_user");

        assertThatThrownBy(() -> service().updateNotification(1L, new NotificationUpdateRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(notificationRepository, never()).findByIdAndDeleted(1L, 0);
    }

    @Test
    void superAdminCanQueryAnotherUsersNotifications() {
        authenticate(1L, "super_admin");
        when(notificationRepository.listNotifications(20L, null, null, null)).thenReturn(List.of());

        assertThat(service().listNotifications(20L, null, null, null)).isEmpty();
        verify(notificationRepository).listNotifications(20L, null, null, null);
    }

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(notificationRepository, notificationWebSocketHandler);
    }

    private void authenticate(Long userId, String systemRole) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername("user-" + userId);
        entity.setPassword("-");
        entity.setRole("USER");
        entity.setSystemRole(systemRole);
        entity.setEnabled(1);
        String authority = "super_admin".equals(systemRole) ? "ROLE_SUPER_ADMIN" : "ROLE_NORMAL_USER";
        CurrentUser currentUser = new CurrentUser(entity, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }
}
