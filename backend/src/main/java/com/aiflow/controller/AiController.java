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

/**
 * AI 智能服务控制器 -- 提供 AI 驱动的流程生成、表单生成、审批建议和流程优化功能。
 *
 * <p>端点一览：
 * <ul>
 *   <li>POST /api/ai/generate-process             -- AI 根据描述生成流程</li>
 *   <li>POST /api/ai/generate-form                -- AI 根据描述生成表单</li>
 *   <li>POST /api/ai/suggest-approval             -- AI 审批建议（分析表单数据和流程上下文）</li>
 *   <li>POST /api/ai/optimize/{templateId}        -- AI 对单个模板进行流程优化分析</li>
 *   <li>POST /api/ai/optimize-all                 -- AI 批量分析所有已完成流程的模板</li>
 *   <li>POST /api/ai/optimize/{templateId}/adopt   -- 采纳 AI 优化建议，实际修改模板 nodeConfig</li>
 * </ul>
 *
 * <p>部分关键操作使用 @AuditLog 注解记录审计日志。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiProcessService aiProcessService;
    private final AiFormService aiFormService;
    private final AiApprovalService aiApprovalService;
    private final AiOptimizationService aiOptimizationService;

    /**
     * AI 生成流程。
     *
     * <p>POST /api/ai/generate-process -- 根据自然语言描述，由 AI 自动生成流程模板结构。
     * 需要登录，记录审计日志（AI_GENERATE_PROCESS）。
     *
     * @param request 包含流程描述的请求体
     * @return AI 生成的流程模板数据
     */
    @AuditLog("AI_GENERATE_PROCESS")
    @PostMapping("/generate-process")
    public ApiResponse<AiGenerateProcessResponse> generateProcess(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiProcessService.generateProcess(request.getDescription()));
    }

    /**
     * AI 生成表单。
     *
     * <p>POST /api/ai/generate-form -- 根据自然语言描述，由 AI 自动生成表单定义。
     * 需要登录，记录审计日志（AI_GENERATE_FORM）。
     *
     * @param request 包含表单描述的请求体
     * @return AI 生成的表单定义数据
     */
    @AuditLog("AI_GENERATE_FORM")
    @PostMapping("/generate-form")
    public ApiResponse<AiGenerateFormResponse> generateForm(
            @Valid @RequestBody AiGenerateRequest request) {
        return ApiResponse.success(
                aiFormService.generateForm(request.getDescription()));
    }

    /**
     * AI 审批建议。
     *
     * <p>POST /api/ai/suggest-approval -- 分析当前表单数据和流程上下文，给出审批建议和理由。
     * 需要登录。
     *
     * @param request 包含流程实例 ID 和任务 ID 等上下文信息
     * @return 审批建议结果，含建议动作和理由
     */
    @PostMapping("/suggest-approval")
    public ApiResponse<AiApprovalResponse> suggestApproval(
            @Valid @RequestBody AiApprovalRequest request) {
        return ApiResponse.success(aiApprovalService.suggest(request));
    }

    /**
     * AI 流程优化 -- 分析单个模板。
     *
     * <p>POST /api/ai/optimize/{templateId} -- 对指定模板进行节点效率、瓶颈分析，
     * 生成优化建议列表。需要登录，记录审计日志（AI_OPTIMIZE）。
     *
     * @param templateId 要分析的模板 ID
     * @return 优化分析结果，包含各节点的改进建议
     */
    @AuditLog("AI_OPTIMIZE")
    @PostMapping("/optimize/{templateId}")
    public ApiResponse<OptimizationAnalysisDTO> optimizeTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(aiOptimizationService.optimizeTemplate(templateId));
    }

    /**
     * AI 流程优化 -- 批量分析所有已完成流程的模板。
     *
     * <p>POST /api/ai/optimize-all -- 对所有已执行完成的流程模板进行集中分析。
     * 需要登录，记录审计日志（AI_OPTIMIZE_ALL）。
     *
     * @return 所有模板的优化分析结果列表
     */
    @AuditLog("AI_OPTIMIZE_ALL")
    @PostMapping("/optimize-all")
    public ApiResponse<List<OptimizationAnalysisDTO>> optimizeAll() {
        return ApiResponse.success(aiOptimizationService.optimizeAll());
    }

    /**
     * 采纳 AI 优化建议。
     *
     * <p>POST /api/ai/optimize/{templateId}/adopt -- 将某条 AI 优化建议应用到模板的
     * nodeConfig 中，实际修改流程定义。需要登录，记录审计日志（AI_ADOPT_OPTIMIZATION）。
     *
     * @param templateId 目标模板 ID
     * @param request    包含优化类型、节点 key 和建议内容的请求体
     * @return 操作结果，含 adopted=true
     */
    @AuditLog("AI_ADOPT_OPTIMIZATION")
    @PostMapping("/optimize/{templateId}/adopt")
    public ApiResponse<java.util.Map<String, Object>> adoptOptimization(
            @PathVariable Long templateId,
            @RequestBody AdoptOptimizationRequest request) {
        aiOptimizationService.adoptSuggestion(templateId, request.getType(),
                request.getNodeKey(), request.getSuggestion());
        return ApiResponse.success(java.util.Map.of("adopted", true));
    }

    /** 采纳优化建议的请求体 */
    @Data
    public static class AdoptOptimizationRequest {
        /** 优化类型，如 node_remove / node_merge / reassign 等 */
        private String type;
        /** 目标节点 key */
        private String nodeKey;
        /** AI 给出的具体建议文本 */
        private String suggestion;
    }

    /** 优化请求体（兼容旧版） */
    @Data
    public static class OptimizeRequest {
        private Long templateId;
    }
}
