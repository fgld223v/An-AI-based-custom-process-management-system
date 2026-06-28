package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器，用于服务探活/就绪检测。
 *
 * <p>基础路径: /api/health</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * GET /api/health — 返回 "ok" 表示服务正常。
     */
    @GetMapping
    public ApiResponse<String> health() {
        return ApiResponse.success("ok");
    }
}
