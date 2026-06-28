package com.aiflow.service;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;

import java.util.List;

/**
 * 通知服务接口，提供通知的 CRUD、已读/未读标记及未读数统计能力。
 */
public interface NotificationService {

    /**
     * 根据接收人、类型、已读状态、关键字分页查询通知列表。
     */
    List<NotificationDTO> listNotifications(Long receiverId, String type, Boolean isRead, String keyword);

    /**
     * 查询单条通知详情。
     */
    NotificationDTO getNotification(Long id);

    /**
     * 创建一条新通知。
     */
    NotificationDTO createNotification(NotificationCreateRequest request);

    /**
     * 更新通知内容。
     */
    NotificationDTO updateNotification(Long id, NotificationUpdateRequest request);

    /**
     * 标记通知为已读。
     */
    NotificationDTO markRead(Long id);

    /**
     * 标记通知为未读。
     */
    NotificationDTO markUnread(Long id);

    /**
     * 统计指定接收人的未读通知数量。
     */
    long countUnread(Long receiverId);

    /**
     * 逻辑删除指定通知。
     */
    void deleteNotification(Long id);
}
