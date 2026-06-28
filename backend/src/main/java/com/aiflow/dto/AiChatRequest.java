package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * AI聊天请求DTO：包含会话ID和消息内容
 */
public class AiChatRequest {

    @NotNull(message = "sessionId must not be null")
    private Long sessionId;

    @NotBlank(message = "message must not be blank")
    private String message;
}
