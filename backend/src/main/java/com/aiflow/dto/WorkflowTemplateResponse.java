package com.aiflow.dto;

import com.aiflow.entity.WorkflowTemplate;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTemplateResponse {

    private Long id;
    private String templateName;
    private String businessType;
    private String formJson;
    private String bpmnXml;
    private String status;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public static WorkflowTemplateResponse from(WorkflowTemplate template) {
        WorkflowTemplateResponse response = new WorkflowTemplateResponse();
        response.setId(template.getId());
        response.setTemplateName(template.getTemplateName());
        response.setBusinessType(template.getBusinessType());
        response.setFormJson(template.getFormJson());
        response.setBpmnXml(template.getBpmnXml());
        response.setStatus(template.getStatus());
        response.setCreatedBy(template.getCreatedBy());
        response.setCreatedTime(template.getCreatedTime());
        response.setUpdatedTime(template.getUpdatedTime());
        return response;
    }
}
