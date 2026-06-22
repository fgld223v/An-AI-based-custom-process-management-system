package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务驳回请求。
 */
@Data
public class TaskRejectRequest {

    /** 业务 ProcessInstance 主键 */
    @NotNull
    private Long instanceId;

    /** 驳回原因 */
    @NotBlank
    private String rejectReason;
}
