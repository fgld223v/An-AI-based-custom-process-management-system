package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.SaveNodeFormRequestDTO;
import com.aiflow.dto.StartProcessPreviewRequestDTO;
import com.aiflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-instances")
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    @GetMapping
    public ApiResponse<List<ProcessInstanceDTO>> listInstances(@RequestParam(required = false) Long templateId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(processInstanceService.listInstances(templateId, status, keyword));
    }

    @PostMapping("/draft")
    public ApiResponse<ProcessInstanceDTO> createDraft(@RequestBody StartProcessPreviewRequestDTO request) {
        return ApiResponse.success(processInstanceService.createDraft(request));
    }

    @PostMapping("/node-form")
    public ApiResponse<FormSubmissionDTO> saveNodeForm(@RequestBody SaveNodeFormRequestDTO request) {
        return ApiResponse.success(processInstanceService.saveNodeForm(request));
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<ProcessInstanceDTO> submitInstance(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.submitInstance(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessInstanceDTO> getInstanceDetail(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getInstanceDetail(id));
    }

    @GetMapping("/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.listSubmissions(id));
    }
}
