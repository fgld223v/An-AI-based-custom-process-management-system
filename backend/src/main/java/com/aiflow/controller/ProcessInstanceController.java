package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.service.ProcessInstanceService;
import com.aiflow.service.TaskUrgeService;
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
    private final TaskUrgeService taskUrgeService;

    @GetMapping
    public ApiResponse<List<ProcessInstanceDTO>> listInstances(@RequestParam(required = false) Long templateId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(processInstanceService.listInstances(templateId, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessInstanceDTO> getInstance(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getInstance(id));
    }

    @GetMapping("/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.listSubmissions(id));
    }

    @PostMapping("/draft")
    public ApiResponse<ProcessInstanceDTO> createDraft(@RequestBody StartProcessPreviewRequest request) {
        return ApiResponse.success(processInstanceService.createDraft(request));
    }

    @PostMapping("/node-form")
    public ApiResponse<FormSubmissionDTO> saveNodeForm(@RequestBody SaveNodeFormRequest request) {
        return ApiResponse.success(processInstanceService.saveNodeForm(request));
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<ProcessInstanceDTO> submitInstance(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.submitInstance(id));
    }

    @GetMapping("/{id}/runtime-state")
    public ApiResponse<RuntimeStateDTO> getRuntimeState(@PathVariable Long id) {
        return ApiResponse.success(processInstanceService.getRuntimeState(id));
    }

    @PostMapping("/{id}/urge")
    public ApiResponse<NotificationDTO> urgeCurrentTask(@PathVariable Long id) {
        return ApiResponse.success(taskUrgeService.urgeCurrentTask(id));
    }
}
