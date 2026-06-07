package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInstanceDTO {

    private Long id;

    private String instanceCode;

    private Long templateId;

    private String templateName;

    private Long formId;

    private Long applicantId;

    private Long bizTypeId;

    private String title;

    private String status;

    private String formData;

    private String currentNodeKey;

    private String flowableProcessInstanceId;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
