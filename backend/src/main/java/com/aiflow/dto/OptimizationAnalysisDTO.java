package com.aiflow.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * AI 流程优化分析结果。
 */
@Data
public class OptimizationAnalysisDTO {
    private Long templateId;
    private String templateName;
    /** AI 整体分析摘要 */
    private String analysis;
    /** 优化建议列表 */
    private List<OptimizationSuggestionDTO> suggestions;
    /** 原始指标数据 */
    private Map<String, Object> metrics;
}
