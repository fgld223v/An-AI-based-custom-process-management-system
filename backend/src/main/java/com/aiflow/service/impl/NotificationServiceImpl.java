package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;
import com.aiflow.model.Notification;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.NotificationService;
import com.aiflow.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final long DEFAULT_RECEIVER_ID = 1L;

    private final NotificationRepository notificationRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> listNotifications(Long receiverId, String type, Boolean isRead, String keyword) {
        Long resolvedReceiverId = receiverId == null ? SecurityUtils.currentUserId() : receiverId;
        return notificationRepository
                .listNotifications(resolvedReceiverId, normalize(type), isRead, normalize(keyword))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDTO getNotification(Long id) {
        return toDto(getRequiredNotification(id));
    }

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

    @Override
    public NotificationDTO markRead(Long id) {
        Notification notification = getRequiredNotification(id);
        LocalDateTime now = LocalDateTime.now();
        applyReadStatus(notification, true, now);
        notification.setUpdatedAt(now);
        NotificationDTO dto = toDto(notificationRepository.save(notification));
        notificationWebSocketHandler.broadcastChanged(dto, "read");
        return dto;
    }

    @Override
    public NotificationDTO markUnread(Long id) {
        Notification notification = getRequiredNotification(id);
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
        Long resolvedReceiverId = receiverId == null ? SecurityUtils.currentUserId() : receiverId;
        if (resolvedReceiverId == null) {
            resolvedReceiverId = DEFAULT_RECEIVER_ID;
        }
        return notificationRepository.countByReceiverIdAndIsReadAndDeleted(resolvedReceiverId, false, 0);
    }

    @Override
    public void deleteNotification(Long id) {
        Notification notification = getRequiredNotification(id);
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
