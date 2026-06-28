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

/**
 * 业务监控控制器（普通用户视角），提供当前用户相关流程实例的查询接口。
 *
 * <p>基础路径: /api/business-monitor</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/business-monitor")
public class BusinessMonitoringController {

    private final BusinessMonitoringService businessMonitoringService;

    /**
     * GET /api/business-monitor/instances — 查询当前用户相关的流程实例列表。
     */
    @GetMapping("/instances")
    public ApiResponse<List<BusinessProcessInstanceDTO>> listInstances(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                businessMonitoringService.listOwnedProcessInstances(templateId, status, keyword));
    }

    /**
     * GET /api/business-monitor/instances/{id} — 查询当前用户相关的单个流程实例。
     */
    @GetMapping("/instances/{id}")
    public ApiResponse<BusinessProcessInstanceDTO> getInstance(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getOwnedProcessInstance(id));
    }

    /**
     * GET /api/business-monitor/instances/{id}/timeline — 查询流程时间线。
     */
    @GetMapping("/instances/{id}/timeline")
    public ApiResponse<TimelineDTO> getTimeline(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getOwnedTimeline(id));
    }

    /**
     * GET /api/business-monitor/instances/{id}/submissions — 查询流程的表单提交记录。
     */
    @GetMapping("/instances/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.listOwnedSubmissions(id));
    }
}
