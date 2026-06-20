package com.aiflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiApprovalRequest {
    @NotNull(message = "不能为空")
    private Long instanceId;

    @NotNull(message = "不能为空")
    private String nodeKey;
}
