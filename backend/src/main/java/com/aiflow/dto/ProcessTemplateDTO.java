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
public class ProcessTemplateDTO {

    private Long id;
    private String templateCode;
    private String templateName;
    private Long bizTypeId;
    private Long formId;
    private Integer version;
    private String status;
    private String sourceType;
    private String bpmnXml;
    private String nodeConfig;
    private String formBindConfig;
    private String flowableDeploymentId;
    private String flowableProcessDefinitionId;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
