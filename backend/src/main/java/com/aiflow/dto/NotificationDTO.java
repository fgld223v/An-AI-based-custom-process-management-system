package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long receiverId;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
