package com.aiflow.dto;

import lombok.Data;

/**
 * AI 流程优化建议。
 */
@Data
public class OptimizationSuggestionDTO {
    /** 类型: redundant_node / bottleneck / approval_optimization / branch_optimization / permission_optimization */
    private String type;

    /** 关联节点ID */
    private String nodeKey;

    /** 关联节点名称 */
    private String nodeName;

    /** 严重程度: high / medium / low */
    private String severity;

    /** 问题描述 */
    private String description;

    /** 具体改进建议 */
    private String suggestion;

    /** 预计改进效果 */
    private String expectedImprovement;
}
