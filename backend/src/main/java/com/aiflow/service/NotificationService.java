package com.aiflow.service;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;

import java.util.List;

public interface NotificationService {

    List<NotificationDTO> listNotifications(Long receiverId, String type, Boolean isRead, String keyword);

    NotificationDTO getNotification(Long id);

    NotificationDTO createNotification(NotificationCreateRequest request);

    NotificationDTO updateNotification(Long id, NotificationUpdateRequest request);

    NotificationDTO markRead(Long id);

    NotificationDTO markUnread(Long id);

    long countUnread(Long receiverId);

    void deleteNotification(Long id);
}
