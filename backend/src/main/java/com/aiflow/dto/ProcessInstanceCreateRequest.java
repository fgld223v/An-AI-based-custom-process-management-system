package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInstanceCreateRequest {

    private Long templateId;

    private Long applicantId;

    private String title;

    private String formData;

    private Map<String, Object> variables;
}
