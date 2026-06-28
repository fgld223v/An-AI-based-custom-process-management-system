package com.aiflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
/**
 * AI聊天会话响应DTO
 */
public class AiChatSessionResponse {

    private Long id;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
