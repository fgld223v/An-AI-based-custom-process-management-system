package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;
import com.aiflow.model.Notification;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.NotificationService;
import com.aiflow.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务实现 — 管理用户通知的创建、查询、已读/未读标记和删除。
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li><b>通知查询</b> — 按接收人、类型、已读状态、关键字筛选</li>
 *   <li><b>通知创建</b> — 创建后通过 WebSocket 实时推送给目标用户</li>
 *   <li><b>通知编辑</b> — 仅超级管理员可修改通知内容和属性</li>
 *   <li><b>已读/未读</b> — 标记已读时设置 readAt 时间戳，标记未读时清空</li>
 *   <li><b>软删除</b> — 设置 deleted=1，通过 WebSocket 通知客户端移除</li>
 *   <li><b>未读计数</b> — 统计指定接收人的未读通知数量</li>
 * </ul>
 *
 * <p>权限控制：</p>
 * <ul>
 *   <li>普通用户只能查看/操作自己的通知</li>
 *   <li>超级管理员可以查看所有用户的通知，可以编辑通知内容</li>
 * </ul>
 *
 * <p>每次变更后通过 {@link NotificationWebSocketHandler} 实时推送给目标用户。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    /** 默认接收人 ID（用于系统通知无明确接收人时） */
    private static final long DEFAULT_RECEIVER_ID = 1L;

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;  // WebSocket 实时推送

    /**
     * 查询通知列表（支持多条件筛选）。
     *
     * <p>普通用户只能查询自己的通知，超级管理员可查询任意用户的通知。</p>
     *
     * @param receiverId 接收人 ID（null 则默认为当前用户）
     * @param type       通知类型（可选）
     * @param isRead     已读状态（可选）
     * @param keyword    关键字搜索（可选）
     * @return 通知 DTO 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> listNotifications(Long receiverId, String type, Boolean isRead, String keyword) {
        Long resolvedReceiverId = resolveReadableReceiverId(receiverId);
        return notificationRepository
                .listNotifications(resolvedReceiverId, normalize(type), isRead, normalize(keyword))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDTO getNotification(Long id) {
        return toDto(getReadableNotification(id));
    }

    /**
     * 创建通知并实时推送。
     *
     * <p>接收人确定逻辑：请求指定 > 当前登录用户 > 默认接收人（ID=1）。</p>
     * <p>创建后通过 WebSocket 广播 notification.changed(created) 事件给目标用户。</p>
     *
     * @param request 通知创建请求
     * @return 创建的通知 DTO
     */
    @Override
    public NotificationDTO createNotification(NotificationCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireText(request.getType(), "type must not be blank");
        requireText(request.getTitle(), "title must not be blank");

        LocalDateTime now = LocalDateTime.now();
        Long receiverId = request.getReceiverId();
        if (receiverId == null) {
            receiverId = SecurityUtils.currentUserId();
        }
        if (receiverId == null) {
            receiverId = DEFAULT_RECEIVER_ID;
        }

        Notification notification = Notification.builder()
                .receiverId(receiverId)
                .type(request.getType().trim())
                .title(request.getTitle().trim())
                .content(normalize(request.getContent()))
                .targetType(normalize(request.getTargetType()))
                .targetId(request.getTargetId())
                .targetUrl(normalize(request.getTargetUrl()))
                .isRead(false)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        NotificationDTO dto = toDto(notificationRepository.save(notification));
        notificationWebSocketHandler.broadcastChanged(dto, "created");
        return dto;
    }

    @Override
    public NotificationDTO updateNotification(Long id, NotificationUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireSuperAdmin();
        Notification notification = getRequiredNotification(id);
        if (hasText(request.getType())) {
            notification.setType(request.getType().trim());
        }
        if (hasText(request.getTitle())) {
            notification.setTitle(request.getTitle().trim());
        }
        notification.setContent(normalize(request.getContent()));
        notification.setTargetType(normalize(request.getTargetType()));
        notification.setTargetId(request.getTargetId());
        notification.setTargetUrl(normalize(request.getTargetUrl()));
        if (request.getIsRead() != null) {
            applyReadStatus(notification, request.getIsRead(), LocalDateTime.now());
        }
        notification.setUpdatedAt(LocalDateTime.now());
        NotificationDTO dto = toDto(notificationRepository.save(notification));
        notificationWebSocketHandler.broadcastChanged(dto, "updated");
        return dto;
    }

    /**
     * 标记通知为已读，设置 readAt 时间戳。
     *
     * <p>通过 WebSocket 广播 notification.changed(read) 事件。</p>
     */
    @Override
    public NotificationDTO markRead(Long id) {
        Notification notification = getReadableNotification(id);
        LocalDateTime now = LocalDateTime.now();
        applyReadStatus(notification, true, now);
        notification.setUpdatedAt(now);
        NotificationDTO dto = toDto(notificationRepository.save(notification));
        notificationWebSocketHandler.broadcastChanged(dto, "read");
        return dto;
    }

    /**
     * 标记通知为未读，清空 readAt 时间戳。
     *
     * <p>通过 WebSocket 广播 notification.changed(unread) 事件。</p>
     */
    @Override
    public NotificationDTO markUnread(Long id) {
        Notification notification = getReadableNotification(id);
        LocalDateTime now = LocalDateTime.now();
        applyReadStatus(notification, false, now);
        notification.setUpdatedAt(now);
        NotificationDTO dto = toDto(notificationRepository.save(notification));
        notificationWebSocketHandler.broadcastChanged(dto, "unread");
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long receiverId) {
        Long resolvedReceiverId = resolveReadableReceiverId(receiverId);
        return notificationRepository.countByReceiverIdAndIsReadAndDeleted(resolvedReceiverId, false, 0);
    }

    /**
     * 软删除通知（设置 deleted=1）。
     *
     * <p>通过 WebSocket 广播 notification.changed(deleted) 事件，
     * 附带 notificationId 和 receiverId，客户端据此移除通知。</p>
     */
    @Override
    public void deleteNotification(Long id) {
        Notification notification = getReadableNotification(id);
        Long receiverId = notification.getReceiverId();
        LocalDateTime now = LocalDateTime.now();
        notification.setDeleted(1);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);
        notificationWebSocketHandler.broadcastDeleted(id, receiverId);
    }

    private Notification getRequiredNotification(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return notificationRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("notification not found"));
    }

    private Notification getReadableNotification(Long id) {
        Notification notification = getRequiredNotification(id);
        assertCanAccessReceiver(notification.getReceiverId());
        return notification;
    }

    private Long resolveReadableReceiverId(Long receiverId) {
        CurrentUser currentUser = requireCurrentUser();
        Long resolvedReceiverId = receiverId == null ? currentUser.getId() : receiverId;
        if (!SecurityUtils.isSuperAdmin() && !currentUser.getId().equals(resolvedReceiverId)) {
            throw new AccessDeniedException("cannot access another user's notifications");
        }
        return resolvedReceiverId;
    }

    private void assertCanAccessReceiver(Long receiverId) {
        CurrentUser currentUser = requireCurrentUser();
        if (!SecurityUtils.isSuperAdmin() && !currentUser.getId().equals(receiverId)) {
            throw new AccessDeniedException("cannot access another user's notification");
        }
    }

    private void requireSuperAdmin() {
        requireCurrentUser();
        if (!SecurityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("only super administrators can edit notification content");
        }
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("authentication required");
        }
        return currentUser;
    }

    private void applyReadStatus(Notification notification, boolean isRead, LocalDateTime now) {
        notification.setIsRead(isRead);
        notification.setReadAt(isRead ? now : null);
    }

    private NotificationDTO toDto(Notification entity) {
        return NotificationDTO.builder()
                .id(entity.getId())
                .receiverId(entity.getReceiverId())
                .type(entity.getType())
                .title(entity.getTitle())
                .content(entity.getContent())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetUrl(entity.getTargetUrl())
                .isRead(Boolean.TRUE.equals(entity.getIsRead()))
                .readAt(entity.getReadAt())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
