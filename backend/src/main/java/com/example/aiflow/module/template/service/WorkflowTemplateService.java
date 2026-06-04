package com.example.aiflow.module.template.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aiflow.module.template.dto.WorkflowTemplatePageRequest;
import com.example.aiflow.module.template.dto.WorkflowTemplateRequest;
import com.example.aiflow.module.template.dto.WorkflowTemplateResponse;

public interface WorkflowTemplateService {

    IPage<WorkflowTemplateResponse> pageTemplates(WorkflowTemplatePageRequest request);

    WorkflowTemplateResponse getTemplate(Long id);

    WorkflowTemplateResponse createTemplate(WorkflowTemplateRequest request);

    WorkflowTemplateResponse updateTemplate(Long id, WorkflowTemplateRequest request);

    void deleteTemplate(Long id);
}
