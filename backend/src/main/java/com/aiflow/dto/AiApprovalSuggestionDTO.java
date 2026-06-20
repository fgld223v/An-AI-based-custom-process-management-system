package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiApprovalSuggestionDTO {

    /** 建议类型：approve（建议通过）、reject（建议驳回）、supplement（建议补充材料） */
    private String suggestion;

    /** AI 推理依据 */
    private String reason;

    /** 置信度 0.0-1.0 */
    private Double confidence;

    /** 风险点列表 */
    private List<String> riskPoints;
}
