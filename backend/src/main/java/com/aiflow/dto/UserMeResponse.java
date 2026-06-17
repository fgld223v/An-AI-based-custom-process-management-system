package com.aiflow.dto;

import com.aiflow.entity.UserEntity;
import lombok.Data;

@Data
public class UserMeResponse {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String systemRole;
    private Long departmentId;
    private Long supervisorId;
    private String managedBizTypeIds;

    public static UserMeResponse from(UserEntity user) {
        UserMeResponse response = new UserMeResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setSystemRole(user.getSystemRole());
        response.setDepartmentId(user.getDepartmentId());
        response.setSupervisorId(user.getSupervisorId());
        response.setManagedBizTypeIds(user.getManagedBizTypeIds());
        return response;
    }
}
