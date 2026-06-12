package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.UserMeResponse;
import com.aiflow.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.success(UserMeResponse.from(currentUser.getUser()));
    }
}
