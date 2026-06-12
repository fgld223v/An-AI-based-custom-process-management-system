package com.example.aiflow.module.ai.controller;

import com.example.aiflow.common.result.ApiResult;
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
    public ApiResult<Map<String, Object>> generateForm() {
        return ApiResult.fail(501, "AI表单生成功能暂未启用");
    }

    @PostMapping("/generate-process")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ApiResult<Map<String, Object>> generateProcess() {
        return ApiResult.fail(501, "AI流程生成功能暂未启用");
    }
}
