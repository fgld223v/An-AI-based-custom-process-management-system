package com.aiflow.dto;

import lombok.Data;

@Data
public class NotificationUpdateRequest {
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private Boolean isRead;
}
