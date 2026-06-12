package com.aiflow.dto;

import lombok.Data;

@Data
public class StartProcessPreviewRequest {
    private Long templateId;
    private String instanceTitle;
    private String startNodeKey;
    private String startNodeName;
    private String businessType;
    private Long formId;
    private String formDataJson;
    private String status;
}