package com.aiflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
/**
 * AI聊天消息响应DTO
 */
public class AiChatMessageResponse {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
