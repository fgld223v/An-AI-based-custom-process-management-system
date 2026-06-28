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
 * 运行时监控控制器（管理员视角），提供全局流程实例的查询接口。
 *
 * <p>基础路径: /api/runtime-monitor</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/runtime-monitor")
public class RuntimeMonitoringController {

    private final BusinessMonitoringService businessMonitoringService;

    /**
     * GET /api/runtime-monitor/instances — 查询全局所有流程实例列表。
     */
    @GetMapping("/instances")
    public ApiResponse<List<BusinessProcessInstanceDTO>> listInstances(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                businessMonitoringService.listGlobalProcessInstances(templateId, status, keyword));
    }

    /**
     * GET /api/runtime-monitor/instances/{id} — 查询全局单个流程实例。
     */
    @GetMapping("/instances/{id}")
    public ApiResponse<BusinessProcessInstanceDTO> getInstance(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getGlobalProcessInstance(id));
    }

    /**
     * GET /api/runtime-monitor/instances/{id}/timeline — 查询全局流程时间线。
     */
    @GetMapping("/instances/{id}/timeline")
    public ApiResponse<TimelineDTO> getTimeline(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.getGlobalTimeline(id));
    }

    /**
     * GET /api/runtime-monitor/instances/{id}/submissions — 查询全局流程的表单提交记录。
     */
    @GetMapping("/instances/{id}/submissions")
    public ApiResponse<List<FormSubmissionDTO>> listSubmissions(@PathVariable Long id) {
        return ApiResponse.success(businessMonitoringService.listGlobalSubmissions(id));
    }
}
