package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowRoleCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$",
            message = "必须以英文字母开头，且只能包含英文字母、数字和下划线")
    private String roleCode;

    @NotBlank
    @Size(max = 128)
    private String roleName;

    @Size(max = 512)
    private String description;

    @NotBlank
    @Pattern(regexp = "(?i)global|department", message = "作用范围必须为全局或部门")
    private String roleScope;

    private Integer enabled;
}
