package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRoleAssignmentDTO {
    private Long id;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String roleScope;
    private Long userId;
    private String username;
    private String userName;
    private Long departmentId;
    private String departmentName;
    private Long createdBy;
    private LocalDateTime createdAt;
}
