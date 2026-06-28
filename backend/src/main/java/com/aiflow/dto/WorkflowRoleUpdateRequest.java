package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * 工作流角色更新请求DTO
 */
public class WorkflowRoleUpdateRequest {

    @NotBlank
    @Size(max = 128)
    private String roleName;

    @Size(max = 512)
    private String description;

    private Integer enabled;
}
