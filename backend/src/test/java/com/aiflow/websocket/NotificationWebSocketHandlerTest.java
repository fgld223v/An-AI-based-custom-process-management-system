package com.aiflow.websocket;

import com.aiflow.dto.NotificationDTO;
import com.aiflow.entity.UserEntity;
import com.aiflow.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWebSocketHandlerTest {

    @Test
    void notificationIsOnlyPushedToItsReceiver() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(new ObjectMapper());
        WebSocketSession receiverSession = sessionFor(10L);
        WebSocketSession otherSession = sessionFor(20L);
        handler.afterConnectionEstablished(receiverSession);
        handler.afterConnectionEstablished(otherSession);

        handler.broadcastChanged(NotificationDTO.builder()
                .id(1L)
                .receiverId(10L)
                .isRead(false)
                .build(), "created");

        verify(receiverSession).sendMessage(any(TextMessage.class));
        verify(otherSession, never()).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession sessionFor(Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getPrincipal()).thenReturn(authentication(userId));
        return session;
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername("user-" + userId);
        entity.setPassword("-");
        entity.setRole("USER");
        entity.setSystemRole("normal_user");
        entity.setEnabled(1);
        CurrentUser currentUser = new CurrentUser(entity,
                List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        return new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities());
    }
}
