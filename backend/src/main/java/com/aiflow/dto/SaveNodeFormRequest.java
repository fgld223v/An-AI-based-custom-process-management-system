package com.aiflow.dto;

import lombok.Data;

@Data
public class SaveNodeFormRequest {
    private Long processInstanceId;
    private Long templateId;
    private String nodeKey;
    private String nodeName;
    private String businessType;
    private Long formId;
    private String formDataJson;
    private String status;
}