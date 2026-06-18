package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiGenerateRequest {

    @NotBlank(message = "不能为空")
    private String description;
}
