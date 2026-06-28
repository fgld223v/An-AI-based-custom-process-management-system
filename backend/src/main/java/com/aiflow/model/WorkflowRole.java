package com.aiflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作流角色表 (workflow_role)
 * 定义流程中使用的审批角色（如部门经理、HR等），用于节点审批人分配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_role")
public class WorkflowRole {

    /** 角色主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色编码，全局唯一标识 */
    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    /** 角色显示名称 */
    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    /** 角色描述说明 */
    @Column(name = "description", length = 512)
    private String description;

    /** 角色作用范围：global(全局) / department(部门级) */
    @Column(name = "role_scope", nullable = false,
            columnDefinition = "ENUM('global','department')")
    private String roleScope;

    /** 启用状态：1-启用，0-禁用 */
    @Column(name = "enabled", nullable = false, columnDefinition = "TINYINT")
    private Integer enabled;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;
}
