package com.aiflow.module.user.controller;

import com.aiflow.common.result.ApiResult;
import com.aiflow.module.user.dto.UserMeResponse;
import com.aiflow.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ApiResult<UserMeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResult.success(UserMeResponse.from(currentUser.getUser()));
    }
}
