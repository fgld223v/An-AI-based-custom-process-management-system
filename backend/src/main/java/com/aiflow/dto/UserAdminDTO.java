package com.aiflow.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户管理列表 DTO — 不含密码。
 */
@Data
public class UserAdminDTO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String systemRole;
    private Long departmentId;
    private Long supervisorId;
    private String managedBizTypeIds;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}