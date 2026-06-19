package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.AiApprovalRequest;
import com.aiflow.dto.AiApprovalResponse;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.AiGenerateRequest;
import com.aiflow.dto.*;
import com.aiflow.service.AiFormService;
import com.aiflow.service.AiOptimizationService;
import com.aiflow.dto.AiGenerateFormResponse;
import com.aiflow.service.AiApprovalService;
import com.aiflow.service.AiFormService;
import com.aiflow.service.AiProcessService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiProcessService aiProcessService;
    private final AiFormService aiFormService;
    private final AiApprovalService aiApprovalService;
    private final AiOptimizationService aiOptimizationService;

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

    @PostMapping("/suggest-approval")
    public ApiResponse<AiApprovalResponse> suggestApproval(
            @Valid @RequestBody AiApprovalRequest request) {
        return ApiResponse.success(aiApprovalService.suggest(request));
    }
    /** AI 流程优化 — 分析单个模板 */
    @PostMapping("/optimize/{templateId}")
    public ApiResponse<OptimizationAnalysisDTO> optimizeTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(aiOptimizationService.optimizeTemplate(templateId));
    }

    /** AI 流程优化 — 批量分析所有已完成流程的模板 */
    @PostMapping("/optimize-all")
    public ApiResponse<List<OptimizationAnalysisDTO>> optimizeAll() {
        return ApiResponse.success(aiOptimizationService.optimizeAll());
    }

    /** AI 优化 — 采纳单条建议，实际修改模板 nodeConfig */
    @PostMapping("/optimize/{templateId}/adopt")
    public ApiResponse<java.util.Map<String, Object>> adoptOptimization(
            @PathVariable Long templateId,
            @RequestBody AdoptOptimizationRequest request) {
        aiOptimizationService.adoptSuggestion(templateId, request.getType(),
                request.getNodeKey(), request.getSuggestion());
        return ApiResponse.success(java.util.Map.of("adopted", true));
    }

    @Data
    public static class AdoptOptimizationRequest {
        private String type;
        private String nodeKey;
        private String suggestion;
    }

    @Data
    public static class OptimizeRequest {
        private Long templateId;
    }
}
