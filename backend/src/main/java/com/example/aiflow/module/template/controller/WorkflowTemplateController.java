package com.example.aiflow.module.template.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aiflow.common.result.ApiResult;
import com.example.aiflow.module.template.dto.WorkflowTemplatePageRequest;
import com.example.aiflow.module.template.dto.WorkflowTemplateRequest;
import com.example.aiflow.module.template.dto.WorkflowTemplateResponse;
import com.example.aiflow.module.template.service.WorkflowTemplateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    public WorkflowTemplateController(WorkflowTemplateService workflowTemplateService) {
        this.workflowTemplateService = workflowTemplateService;
    }

    @GetMapping
    public ApiResult<IPage<WorkflowTemplateResponse>> pageTemplates(WorkflowTemplatePageRequest request) {
        return ApiResult.success(workflowTemplateService.pageTemplates(request));
    }

    @GetMapping("/{id}")
    public ApiResult<WorkflowTemplateResponse> getTemplate(@PathVariable Long id) {
        return ApiResult.success(workflowTemplateService.getTemplate(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResult<WorkflowTemplateResponse> createTemplate(@Valid @RequestBody WorkflowTemplateRequest request) {
        return ApiResult.success(workflowTemplateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResult<WorkflowTemplateResponse> updateTemplate(@PathVariable Long id,
                                                              @Valid @RequestBody WorkflowTemplateRequest request) {
        return ApiResult.success(workflowTemplateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResult<Void> deleteTemplate(@PathVariable Long id) {
        workflowTemplateService.deleteTemplate(id);
        return ApiResult.success();
    }
}
