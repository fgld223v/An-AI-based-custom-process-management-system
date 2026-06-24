package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.LoginRequest;
import com.aiflow.dto.LoginResponse;
import com.aiflow.dto.ResetPasswordRequest;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          SysUserRepository sysUserRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        String token = jwtTokenProvider.createToken(currentUser);
        LoginResponse response = new LoginResponse(
                token, "Bearer",
                currentUser.getId(), currentUser.getUsername(), currentUser.getNickname(),
                currentUser.getRole(), currentUser.getSystemRole(),
                currentUser.getDepartmentId(), currentUser.getSupervisorId(),
                currentUser.getManagedBizTypeIds()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (sysUserRepository.existsByUsername(request.getUsername().trim())) {
            return ApiResponse.fail(400, "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = SysUser.builder()
                .username(request.getUsername().trim())
                .nickname(request.getNickname() != null ? request.getNickname().trim() : request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER").systemRole("normal_user")
                .enabled(1).deleted(0)
                .createdTime(now).updatedTime(now)
                .build();
        sysUserRepository.save(user);
        return ApiResponse.success(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        // 验证两次密码输入一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.fail(400, "两次输入的密码不一致");
        }

        String username = request.getUsername().trim();
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }

        // 验证手机号或邮箱（二选一）
        String verifyType = request.getVerifyType();
        String verifyValue = request.getVerifyValue();
        if (verifyType == null || verifyType.isBlank() || verifyValue == null || verifyValue.isBlank()) {
            return ApiResponse.fail(400, "请填写手机号或邮箱进行身份验证");
        }
        boolean verified = false;
        if ("phone".equals(verifyType)) {
            verified = verifyValue.trim().equals(user.getPhone());
        } else if ("email".equals(verifyType)) {
            verified = verifyValue.trim().equalsIgnoreCase(user.getEmail());
        }
        if (!verified) {
            return ApiResponse.fail(400, "身份验证失败，请检查手机号或邮箱是否正确");
        }

        // 更新密码（BCrypt 加密后存储）
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);

        return ApiResponse.success(Map.of("message", "密码重置成功，请使用新密码登录"));
    }

    @Data
    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank(message = "用户名不能为空")
        private String username;
        private String nickname;
        @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
        private String password;
    }
}
