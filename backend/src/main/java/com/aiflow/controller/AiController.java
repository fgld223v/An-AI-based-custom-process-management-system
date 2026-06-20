package com.aiflow.controller;

import com.aiflow.annotation.AuditLog;
import com.aiflow.common.ApiResponse;
import com.aiflow.dto.AiApprovalRequest;
import com.aiflow.dto.AiApprovalResponse;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.AiGenerateRequest;
import com.aiflow.dto.*;
import com.aiflow.service.AiFormService;
import com.aiflow.service.AiOptimizationService;
import com.aiflow.dto.AiGenerateFormResponse;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.AiGenerateRequest;
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

    @AuditLog("AI_GENERATE_PROCESS")
    @PostMapping("/generate-process")
    public ApiResponse<AiGenerateProcessResponse> generateProcess(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiProcessService.generateProcess(request.getDescription()));
    }

    @AuditLog("AI_GENERATE_FORM")
    @PostMapping("/generate-form")
    public ApiResponse<AiGenerateFormResponse> generateForm(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiFormService.generateForm(request.getDescription()));
    }

    /** AI 审批建议 — 分析表单数据和流程上下文，给出审批建议 */
    @PostMapping("/suggest-approval")
    public ApiResponse<AiApprovalSuggestionDTO> suggestApproval(
            @RequestBody SuggestApprovalRequest request) {
        return ApiResponse.success(
                aiApprovalService.suggest(request.getInstanceId(), request.getNodeKey()));
    }

    @Data
    public static class SuggestApprovalRequest {
        private Long instanceId;
        private String nodeKey;
    }

    @AuditLog("AI_SUGGEST_APPROVAL")
    @PostMapping("/suggest-approval")
    public ApiResponse<AiApprovalResponse> suggestApproval(
            @Valid @RequestBody AiApprovalRequest request) {
        return ApiResponse.success(aiApprovalService.suggest(request));
    }
    /** AI 流程优化 — 分析单个模板 */
    @AuditLog("AI_OPTIMIZE")
    @PostMapping("/optimize/{templateId}")
    public ApiResponse<OptimizationAnalysisDTO> optimizeTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(aiOptimizationService.optimizeTemplate(templateId));
    }

    /** AI 流程优化 — 批量分析所有已完成流程的模板 */
    @AuditLog("AI_OPTIMIZE_ALL")
    @PostMapping("/optimize-all")
    public ApiResponse<List<OptimizationAnalysisDTO>> optimizeAll() {
        return ApiResponse.success(aiOptimizationService.optimizeAll());
    }

    /** AI 优化 — 采纳单条建议，实际修改模板 nodeConfig */
    @AuditLog("AI_ADOPT_OPTIMIZATION")
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
