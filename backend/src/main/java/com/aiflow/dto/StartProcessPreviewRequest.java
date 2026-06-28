package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 启动流程预览请求DTO：发起流程前的草稿/预览请求
 */
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