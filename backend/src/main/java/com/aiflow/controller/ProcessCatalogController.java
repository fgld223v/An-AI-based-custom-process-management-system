package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.ProcessRoutePreviewDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.ProcessRoutePreviewService;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程目录控制器（普通用户视角），提供用户有权限发起的已发布流程列表及路由预览。
 *
 * <p>基础路径: /api/process-catalog</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-catalog")
public class ProcessCatalogController {

    private final ProcessTemplateService processTemplateService;
    private final ProcessAuthorizationService processAuthorizationService;
    private final ProcessRoutePreviewService processRoutePreviewService;

    /**
     * GET /api/process-catalog — 查询当前用户有权限发起的所有已发布流程。
     */
    @GetMapping
    public ApiResponse<List<ProcessTemplateDTO>> listAvailableProcesses() {
        List<ProcessTemplateDTO> result = processTemplateService.listPublishedBusinessProcesses()
                .stream()
                .filter(processAuthorizationService::canCurrentUserStart)
                .map(DtoMapper::toProcessTemplateDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * GET /api/process-catalog/{id} — 查询单个可发起流程详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> getAvailableProcess(@PathVariable Long id) {
        ProcessTemplate process = processTemplateService.findPublishedBusinessProcessById(id)
                .orElseThrow(() -> new IllegalArgumentException("available business process not found"));
        processAuthorizationService.assertCanStart(process);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(process));
    }

    /**
     * GET /api/process-catalog/{id}/route-preview — 预览流程审批路由。
     */
    @GetMapping("/{id}/route-preview")
    public ApiResponse<ProcessRoutePreviewDTO> previewRoute(@PathVariable Long id) {
        ProcessTemplate process = processTemplateService.findPublishedBusinessProcessById(id)
                .orElseThrow(() -> new IllegalArgumentException("available business process not found"));
        processAuthorizationService.assertCanStart(process);
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("authenticated user is required");
        }
        return ApiResponse.success(processRoutePreviewService.preview(process, currentUser.getId()));
    }
}
