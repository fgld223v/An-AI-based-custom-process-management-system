package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * AI审批建议响应DTO：包含审批建议、理由、置信度及风险点
 */
public class AiApprovalResponse {
    private String suggestion;    // approve / reject / supplement
    private String reason;
    private Double confidence;
    private List<String> riskPoints;
}
