package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 通知创建请求DTO
 */
public class NotificationCreateRequest {
    private Long receiverId;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
}
