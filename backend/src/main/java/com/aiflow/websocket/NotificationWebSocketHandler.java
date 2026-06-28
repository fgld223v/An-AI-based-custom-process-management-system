
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

/**
 * 通知 WebSocket 处理器 — 实现服务端到客户端的通知实时推送。
 *
 * <p>架构：</p>
 * <ul>
 *   <li>使用 ConcurrentHashMap.newKeySet() 维护所有活跃的 WebSocket 会话</li>
 *   <li>客户端通过 {@code /ws/notification} 端点建立连接</li>
 *   <li>服务端变更通知后，广播 JSON 消息给目标用户</li>
 * </ul>
 *
 * <p>广播策略：</p>
 * <ul>
 *   <li><b>精确推送</b> — 通过会话 Principal 中的 CurrentUser.ID 匹配 receiverId，
 *       仅推送给目标用户，不广播给所有连接</li>
 *   <li><b>清理机制</b> — 发送前自动移除已关闭的会话，
 *       handleTransportError 中也清理异常会话</li>
 * </ul>
 *
 * <p>消息格式：JSON，包含 event、action、notificationId、receiverId、isRead 字段。
 * 变更事件类型为 {@code notification.changed}，动作为 created/updated/read/unread/deleted。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    /** 线程安全的活动会话集合 */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * WebSocket 连接建立后，将会话加入活跃会话集合。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * WebSocket 连接关闭后，从活跃会话集合中移除。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /**
     * 传输异常处理 — 移除异常会话并尝试关闭连接。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * 广播通知变更事件给目标用户。
     *
     * <p>消息格式：</p>
     * <pre>{@code
     * {
     *   "event": "notification.changed",
     *   "action": "created|updated|read|unread|deleted",
     *   "notificationId": 123,
     *   "receiverId": 456,
     *   "isRead": true|false
     * }
     * }</pre>
     *
     * @param notification 变更的通知对象
     * @param action       变更动作
     */
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

    /**
     * 广播通知删除事件给目标用户。
     *
     * @param notificationId 被删除的通知 ID
     * @param receiverId     通知接收人 ID
     */
    public void broadcastDeleted(Long notificationId, Long receiverId) {
        broadcast(receiverId, Map.of(
                "event", "notification.changed",
                "action", "deleted",
                "notificationId", notificationId,
                "receiverId", receiverId
        ));
    }

    /**
     * 核心广播方法 — 序列化 payload 为 JSON 后推送给指定接收人的所有活跃会话。
     *
     * <p>发送前自动清理已关闭的会话（sessions.removeIf）。</p>
     *
     * @param receiverId 目标接收人用户 ID
     * @param payload    广播消息内容
     */
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

    /**
     * 判断会话是否属于指定接收人。
     *
     * <p>通过会话的 Principal 获取 CurrentUser，比对用户 ID。
     * 非认证会话或 Principal 类型不匹配时返回 false。</p>
     */
    private boolean isReceiverSession(WebSocketSession session, Long receiverId) {
        if (!(session.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return false;
        }
        return currentUser.getId().equals(receiverId);
    }
}
