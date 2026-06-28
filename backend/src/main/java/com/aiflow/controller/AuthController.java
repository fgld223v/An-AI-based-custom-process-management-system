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

/**
 * 认证控制器 -- 提供用户登录、注册和密码重置功能。
 *
 * <p>端点一览：
 * <ul>
 *   <li>POST /api/auth/login          -- 用户登录，返回 JWT Token</li>
 *   <li>POST /api/auth/register       -- 用户注册（默认角色：普通用户）</li>
 *   <li>POST /api/auth/reset-password -- 通过手机号或邮箱验证后重置密码</li>
 * </ul>
 *
 * <p>所有端点均为公开访问，无需预先登录。
 */
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

    /**
     * 用户登录接口。
     *
     * <p>POST /api/auth/login -- 使用用户名密码进行认证，成功后返回 JWT 令牌及用户基本信息。
     *
     * @param request 登录请求体，包含用户名和密码
     * @return 包含 token、tokenType 和用户信息的 LoginResponse
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 使用 Spring Security 的 AuthenticationManager 进行凭证校验
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        // 校验通过后，从 principal 中提取当前用户信息
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        // 生成 JWT 令牌
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

    /**
     * 用户注册接口。
     *
     * <p>POST /api/auth/register -- 新用户注册，默认角色为普通用户（USER / normal_user），
     * 密码使用 BCrypt 加密存储。
     *
     * @param request 注册请求体，包含用户名、昵称（可选）和密码
     * @return 注册成功的用户 ID 和用户名
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        // 检查用户名是否已被占用
        if (sysUserRepository.existsByUsername(request.getUsername().trim())) {
            return ApiResponse.fail(400, "用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 构建新用户实体，默认角色为普通用户，账户启用且未删除
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

    /**
     * 密码重置接口（无需登录）。
     *
     * <p>POST /api/auth/reset-password -- 通过用户名 + 手机号或邮箱进行身份验证后重置密码。
     *
     * @param request 包含用户名、新密码、确认密码，以及验证方式（phone/email）和验证值
     * @return 操作结果消息
     */
    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        // 验证两次密码输入一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.fail(400, "两次输入的密码不一致");
        }

        // 根据用户名查找未删除的用户
        String username = request.getUsername().trim();
        SysUser user = sysUserRepository.findByUsernameAndDeleted(username, 0).orElse(null);
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
            // 手机号验证（精确匹配）
            verified = verifyValue.trim().equals(user.getPhone());
        } else if ("email".equals(verifyType)) {
            // 邮箱验证（忽略大小写）
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

    /** 注册请求体内部类 */
    @Data
    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank(message = "用户名不能为空")
        private String username;
        private String nickname;
        @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
        private String password;
    }
}
