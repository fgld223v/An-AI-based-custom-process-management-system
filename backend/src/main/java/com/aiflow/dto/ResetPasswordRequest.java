package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 验证方式：phone 或 email */
    private String verifyType;

    /** 验证值：手机号或邮箱地址 */
    private String verifyValue;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码长度至少 6 位")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
