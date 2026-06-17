package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.AiGenerateFormResponse;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.AiGenerateRequest;
import com.aiflow.service.AiFormService;
import com.aiflow.service.AiProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiProcessService aiProcessService;
    private final AiFormService aiFormService;

    @PostMapping("/generate-process")
    public ApiResponse<AiGenerateProcessResponse> generateProcess(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiProcessService.generateProcess(request.getDescription()));
    }

    @PostMapping("/generate-form")
    public ApiResponse<AiGenerateFormResponse> generateForm(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiFormService.generateForm(request.getDescription()));
    }
}
