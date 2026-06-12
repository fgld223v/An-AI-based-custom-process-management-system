package com.aiflow.dto;

import com.aiflow.entity.UserEntity;
import lombok.Data;

@Data
public class UserMeResponse {

    private Long id;
    private String username;
    private String nickname;
    private String role;

    public static UserMeResponse from(UserEntity user) {
        UserMeResponse response = new UserMeResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        return response;
    }
}
