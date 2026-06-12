package com.aiflow.service;

import com.aiflow.dto.WorkflowTemplatePageRequest;
import com.aiflow.dto.WorkflowTemplateRequest;
import com.aiflow.dto.WorkflowTemplateResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface WorkflowTemplateService {

    IPage<WorkflowTemplateResponse> pageTemplates(WorkflowTemplatePageRequest request);

    WorkflowTemplateResponse getTemplate(Long id);

    WorkflowTemplateResponse createTemplate(WorkflowTemplateRequest request);

    WorkflowTemplateResponse updateTemplate(Long id, WorkflowTemplateRequest request);

    void deleteTemplate(Long id);
}
