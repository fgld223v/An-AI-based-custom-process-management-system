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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "system_role", columnDefinition = "ENUM('super_admin','biz_admin','normal_user')")
    private String systemRole;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "managed_biz_type_ids", columnDefinition = "JSON")
    private String managedBizTypeIds;

    @Column(name = "enabled", columnDefinition = "TINYINT")
    private Integer enabled;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
