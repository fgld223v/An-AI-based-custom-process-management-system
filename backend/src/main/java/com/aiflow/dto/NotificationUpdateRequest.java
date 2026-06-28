package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 通知更新请求DTO
 */
public class NotificationUpdateRequest {
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private Boolean isRead;
}
