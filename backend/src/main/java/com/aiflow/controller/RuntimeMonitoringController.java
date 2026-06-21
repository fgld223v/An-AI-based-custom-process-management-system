package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.BusinessProcessInstanceDTO;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.TimelineDTO;
import com.aiflow.service.BusinessMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/runtime-monitor")
public class RuntimeMonitoringController {

    private final BusinessMonitoringService businessMonitoringService;

    @GetMapping("/instances")
    public ApiResponse<List<BusinessProcessInstanceDTO>> listInstances(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                businessMonitoringService.listGlobalProcessInstances(templateId, status, keyword));
    }

    @GetMapping("/instances/{id}")
    public ApiResponse<BusinessProcessInstanceDTO> getInstance(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getGlobalProcessInstance(id));
    }

    @GetMapping("/instances/{id}/timeline")
    public ApiResponse<TimelineDTO> getTimeline(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getGlobalTimeline(id));
    }

    @GetMapping("/instances/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.listGlobalSubmissions(id));
    }
}
