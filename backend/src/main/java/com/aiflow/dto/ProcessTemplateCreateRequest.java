package com.aiflow.dto;

import lombok.Data;

@Data
public class ProcessTemplateCreateRequest {

    private String templateCode;
    private String templateName;
    private Long bizTypeId;
    private Long formId;
    private String sourceType;
    private String resourceType;
    private String bpmnXml;
    private String nodeConfig;
    private String formBindConfig;
    private Long createdBy;
}
