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

    private Boolean enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
