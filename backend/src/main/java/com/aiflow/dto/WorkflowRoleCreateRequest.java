package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowRoleCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,63}$",
            message = "must start with a letter and contain only letters, numbers, and underscores")
    private String roleCode;

    @NotBlank
    @Size(max = 128)
    private String roleName;

    @Size(max = 512)
    private String description;

    @NotBlank
    @Pattern(regexp = "(?i)global|department", message = "must be global or department")
    private String roleScope;

    private Integer enabled;
}
