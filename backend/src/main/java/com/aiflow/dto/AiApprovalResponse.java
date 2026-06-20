package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiApprovalResponse {
    private String suggestion;    // approve / reject / supplement
    private String reason;
    private Double confidence;
    private List<String> riskPoints;
}
