package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private String systemRole;
    private Long departmentId;
    private Long supervisorId;
    /** 业务管理员管辖的业务类型ID列表（JSON数组字符串，如 "[1,2,3]"） */
    private String managedBizTypeIds;
}
