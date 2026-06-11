package com.aiflow.module.user.dto;

import com.aiflow.module.user.entity.SysUser;
import lombok.Data;

@Data
public class UserMeResponse {

    private Long id;
    private String username;
    private String nickname;
    private String role;

    public static UserMeResponse from(SysUser user) {
        UserMeResponse response = new UserMeResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        return response;
    }
}
