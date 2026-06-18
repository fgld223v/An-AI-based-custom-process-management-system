package com.aiflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus entity for sys_user table.
 * Used by the security/auth module (separate from JPA com.aiflow.model.SysUser).
 */
@Data
@TableName("sys_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String role;

    /** 系统角色：super_admin / biz_admin / normal_user */
    private String systemRole;

    private Long departmentId;

    /** 直属上级用户ID */
    private Long supervisorId;

    /** 业务管理员管辖的业务类型ID列表（JSON数组，如 [1,2,3]） */
    private String managedBizTypeIds;

    private Integer enabled;

    private Integer deleted;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
