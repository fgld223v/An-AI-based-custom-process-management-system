package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.LoginRequest;
import com.aiflow.dto.LoginResponse;
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
import org.springframework.web.bind.annotation.*;

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
                token,
                "Bearer",
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getNickname(),
                currentUser.getRole(),
                currentUser.getSystemRole(),
                currentUser.getDepartmentId(),
                currentUser.getSupervisorId(),
                currentUser.getManagedBizTypeIds()
        );
        return ApiResponse.success(response);
    }

    /**
     * 用户注册 — 使用 JPA Repository 避免 MyBatis 代理问题。
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername().trim();

        if (sysUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = SysUser.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword().trim()))
                .nickname(request.getNickname() != null && !request.getNickname().isBlank()
                        ? request.getNickname().trim() : username)
                .role("USER")
                .systemRole("normal_user")
                .enabled(1)
                .deleted(0)
                .createdTime(now)
                .updatedTime(now)
                .build();
        sysUserRepository.save(user);

        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "message", "注册成功"
        ));
    }

    @Data
    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank
        private String username;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 6, message = "密码至少6位")
        private String password;
        private String nickname;
    }
}
