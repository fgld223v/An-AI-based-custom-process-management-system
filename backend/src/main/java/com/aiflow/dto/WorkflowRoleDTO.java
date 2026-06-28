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
/**
 * 工作流角色DTO：包含角色编码、名称、范围及成员数量
 */
public class WorkflowRoleDTO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private String roleScope;
    private Integer enabled;
    private Long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
