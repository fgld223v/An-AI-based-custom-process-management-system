package com.aiflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiChatSessionResponse {

    private Long id;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
