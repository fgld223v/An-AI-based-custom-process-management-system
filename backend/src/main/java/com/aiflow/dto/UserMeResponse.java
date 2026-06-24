package com.aiflow.dto;

import com.aiflow.entity.UserEntity;
import com.aiflow.model.SysUser;
import lombok.Data;

@Data
public class UserMeResponse {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String role;
    private String systemRole;
    private Long departmentId;
    private Long supervisorId;
    private String managedBizTypeIds;

    public static UserMeResponse from(UserEntity user) {
        return build(user.getId(), user.getUsername(), user.getNickname(),
                user.getPhone(), user.getEmail(),
                user.getRole(), user.getSystemRole(), user.getDepartmentId(),
                user.getSupervisorId(), user.getManagedBizTypeIds());
    }

    public static UserMeResponse fromEntity(SysUser user) {
        return build(user.getId(), user.getUsername(), user.getNickname(),
                user.getPhone(), user.getEmail(),
                user.getRole(), user.getSystemRole(), user.getDepartmentId(),
                user.getSupervisorId(), user.getManagedBizTypeIds());
    }

    private static UserMeResponse build(Long id, String username, String nickname,
                                         String phone, String email,
                                         String role, String systemRole,
                                         Long departmentId, Long supervisorId,
                                         String managedBizTypeIds) {
        UserMeResponse response = new UserMeResponse();
        response.setId(id);
        response.setUsername(username);
        response.setNickname(nickname);
        response.setPhone(phone);
        response.setEmail(email);
        response.setRole(role);
        response.setSystemRole(systemRole);
        response.setDepartmentId(departmentId);
        response.setSupervisorId(supervisorId);
        response.setManagedBizTypeIds(managedBizTypeIds);
        return response;
    }
}
