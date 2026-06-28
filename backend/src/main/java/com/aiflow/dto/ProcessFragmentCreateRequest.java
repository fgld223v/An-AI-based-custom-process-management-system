package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 流程片段创建请求DTO
 */
public class ProcessFragmentCreateRequest {

    private String fragmentCode;
    private String fragmentName;
    private Long bizTypeId;
    private String description;
    private String fragmentType;
    private String bpmnXml;
    private String nodeConfig;
    private Long createdBy;
}
