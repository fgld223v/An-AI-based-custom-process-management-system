package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.UserMeResponse;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserRepository sysUserRepository;

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        // 从数据库实时读取，确保部门/上级等修改后能及时生效
        SysUser user = sysUserRepository.findByIdAndEnabledNotNull(currentUser.getId())
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail(401, "用户不存在或已被删除");
        }
        return ApiResponse.success(UserMeResponse.fromEntity(user));
    }
}
