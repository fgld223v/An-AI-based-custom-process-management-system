package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotNull(message = "sessionId must not be null")
    private Long sessionId;

    @NotBlank(message = "message must not be blank")
    private String message;
}
