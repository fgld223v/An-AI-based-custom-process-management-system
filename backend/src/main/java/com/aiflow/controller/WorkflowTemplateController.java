package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.WorkflowTemplatePageRequest;
import com.aiflow.dto.WorkflowTemplateRequest;
import com.aiflow.dto.WorkflowTemplateResponse;
import com.aiflow.service.WorkflowTemplateService;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
    public ApiResponse<IPage<WorkflowTemplateResponse>> pageTemplates(WorkflowTemplatePageRequest request) {
        return ApiResponse.success(workflowTemplateService.pageTemplates(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowTemplateResponse> getTemplate(@PathVariable Long id) {
        return ApiResponse.success(workflowTemplateService.getTemplate(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<WorkflowTemplateResponse> createTemplate(@Valid @RequestBody WorkflowTemplateRequest request) {
        return ApiResponse.success(workflowTemplateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<WorkflowTemplateResponse> updateTemplate(@PathVariable Long id,
                                                              @Valid @RequestBody WorkflowTemplateRequest request) {
        return ApiResponse.success(workflowTemplateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        workflowTemplateService.deleteTemplate(id);
        return ApiResponse.success();
    }
}
