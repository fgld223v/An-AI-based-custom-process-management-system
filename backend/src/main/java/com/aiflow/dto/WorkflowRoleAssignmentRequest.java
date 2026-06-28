package com.aiflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
/**
 * 工作流角色分配请求DTO
 */
public class WorkflowRoleAssignmentRequest {

    @NotNull
    @Positive
    private Long userId;

    private Long departmentId;
}
