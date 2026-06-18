package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowTemplateRequest {

    @NotBlank(message = "不能为空")
    private String templateName;

    @NotBlank(message = "不能为空")
    private String businessType;

    @NotBlank(message = "不能为空")
    private String formJson;

    @NotBlank(message = "不能为空")
    private String bpmnXml;

    private String status;
}
