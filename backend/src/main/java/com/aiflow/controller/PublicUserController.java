package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的用户列表接口 —— 所有已登录用户均可访问。
 * 仅返回基础信息（id/nickname/username/role），用于个人设置选择直属上级。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class PublicUserController {

    private final SysUserRepository sysUserRepository;

    @GetMapping
    public ApiResponse<List<UserBrief>> listUsers() {
        List<SysUser> users = sysUserRepository.findByDeletedOrderByIdAsc(0);
        List<UserBrief> result = users.stream()
                .filter(u -> u.getEnabled() != null && u.getEnabled() == 1)
                .map(u -> {
                    UserBrief b = new UserBrief();
                    b.setId(u.getId());
                    b.setUsername(u.getUsername());
                    b.setNickname(u.getNickname());
                    b.setRole(u.getRole());
                    return b;
                })
                .toList();
        return ApiResponse.success(result);
    }

    @Data
    public static class UserBrief {
        private Long id;
        private String username;
        private String nickname;
        private String role;
    }
}
