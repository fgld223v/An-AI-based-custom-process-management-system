package com.aiflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class WorkflowRoleAssignmentRequest {

    @NotNull
    @Positive
    private Long userId;

    private Long departmentId;
}
