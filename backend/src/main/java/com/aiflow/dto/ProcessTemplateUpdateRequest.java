package com.aiflow.dto;

import lombok.Data;

@Data
public class ProcessTemplateUpdateRequest {

    private String templateName;
    private Long bizTypeId;
    private Long formId;
    private String bpmnXml;
    private String nodeConfig;
    private String formBindConfig;
}
