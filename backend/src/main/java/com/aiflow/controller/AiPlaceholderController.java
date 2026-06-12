package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiPlaceholderController {

    @PostMapping("/generate-form")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ApiResponse<Map<String, Object>> generateForm() {
        return ApiResponse.fail(501, "AI表单生成功能暂未启用");
    }

    @PostMapping("/generate-process")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ApiResponse<Map<String, Object>> generateProcess() {
        return ApiResponse.fail(501, "AI流程生成功能暂未启用");
    }
}
