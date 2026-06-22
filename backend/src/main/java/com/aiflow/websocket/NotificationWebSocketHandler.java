package com.aiflow.websocket;

import com.aiflow.dto.NotificationDTO;
import com.aiflow.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        if (session.isOpen()) {
            session.close();
        }
    }

    public void broadcastChanged(NotificationDTO notification, String action) {
        if (notification == null) {
            return;
        }
        broadcast(notification.getReceiverId(), Map.of(
                "event", "notification.changed",
                "action", action,
                "notificationId", notification.getId(),
                "receiverId", notification.getReceiverId(),
                "isRead", Boolean.TRUE.equals(notification.getIsRead())
        ));
    }

    public void broadcastDeleted(Long notificationId, Long receiverId) {
        broadcast(receiverId, Map.of(
                "event", "notification.changed",
                "action", "deleted",
                "notificationId", notificationId,
                "receiverId", receiverId
        ));
    }

    private void broadcast(Long receiverId, Map<String, Object> payload) {
        if (sessions.isEmpty()) {
            return;
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize notification websocket payload", ex);
            return;
        }
        TextMessage message = new TextMessage(body);
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            if (!isReceiverSession(session, receiverId)) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException ex) {
                log.warn("Failed to send notification websocket message", ex);
            }
        }
    }

    private boolean isReceiverSession(WebSocketSession session, Long receiverId) {
        if (!(session.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return false;
        }
        return currentUser.getId().equals(receiverId);
    }
}
