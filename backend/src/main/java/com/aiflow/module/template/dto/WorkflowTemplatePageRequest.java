package com.aiflow.module.template.dto;

import lombok.Data;

@Data
public class WorkflowTemplatePageRequest {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String templateName;

    private String businessType;

    private String status;
}
