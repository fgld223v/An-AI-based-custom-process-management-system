package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.LoginRequest;
import com.aiflow.dto.LoginResponse;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
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
                currentUser.getRole()
        );
        return ApiResponse.success(response);
    }
}
