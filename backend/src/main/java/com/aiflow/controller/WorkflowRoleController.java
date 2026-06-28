package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.service.WorkflowRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作流角色控制器（普通用户视角），仅提供已启用角色的查询接口。
 *
 * <p>基础路径: /api/workflow-roles</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow-roles")
public class WorkflowRoleController {

    private final WorkflowRoleService workflowRoleService;

    /**
     * GET /api/workflow-roles — 查询所有已启用的工作流角色。
     */
    @GetMapping
    public ApiResponse<List<WorkflowRoleDTO>> listEnabledRoles() {
        return ApiResponse.success(workflowRoleService.listRoles(true));
    }
}
