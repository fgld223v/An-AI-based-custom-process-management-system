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
 * 系统用户表 (sys_user)
 * 存储平台所有用户的基本信息，包括登录凭据、联系方式、角色权限及归属关系。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sys_user")
public class SysUser {

    /** 用户主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 登录用户名 */
    @Column(name = "username")
    private String username;

    /** 加密后的登录密码 */
    @Column(name = "password")
    private String password;

    /** 用户昵称 / 显示名称 */
    @Column(name = "nickname")
    private String nickname;

    /** 联系电话 */
    @Column(name = "phone")
    private String phone;

    /** 电子邮箱 */
    @Column(name = "email")
    private String email;

    /** 业务角色标签（如部门负责人等业务含义的角色） */
    @Column(name = "role")
    private String role;

    /** 系统级角色：super_admin(超级管理员) / biz_admin(业务管理员) / normal_user(普通用户) */
    @Column(name = "system_role", columnDefinition = "ENUM('super_admin','biz_admin','normal_user')")
    private String systemRole;

    /** 所属部门 ID，关联 department 表 */
    @Column(name = "department_id")
    private Long departmentId;

    /** 直属上级用户 ID */
    @Column(name = "supervisor_id")
    private Long supervisorId;

    /** 管理的业务类型 ID 列表，JSON 数组格式 */
    @Column(name = "managed_biz_type_ids", columnDefinition = "JSON")
    private String managedBizTypeIds;

    /** 启用状态：1-启用，0-禁用 */
    @Column(name = "enabled", columnDefinition = "TINYINT")
    private Integer enabled;

    /** 创建时间 */
    @Column(name = "created_time")
    private LocalDateTime createdTime;

    /** 最后更新时间 */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
