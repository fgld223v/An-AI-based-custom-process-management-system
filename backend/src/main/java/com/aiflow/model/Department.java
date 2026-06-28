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
 * 部门表 (department)
 * 存储组织架构中的部门信息，支持树形层级（通过 parent_id 自关联）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "department")
public class Department {

    /** 部门主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 上级部门 ID，根部门为 null，自关联形成树形结构 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 部门编码，用于唯一标识和快捷检索 */
    @Column(name = "dept_code")
    private String deptCode;

    /** 部门名称 */
    @Column(name = "dept_name")
    private String deptName;

    /** 同级排序序号，值越小越靠前 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 部门负责人用户 ID，关联 sys_user 表 */
    @Column(name = "leader_user_id")
    private Long leaderUserId;

    /** 部门状态：1-启用，0-禁用 */
    @Column(name = "status", columnDefinition = "TINYINT")
    private Integer status;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
