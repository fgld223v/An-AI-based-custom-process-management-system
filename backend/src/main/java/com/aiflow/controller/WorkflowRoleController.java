package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.service.WorkflowRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow-roles")
public class WorkflowRoleController {

    private final WorkflowRoleService workflowRoleService;

    @GetMapping
    public ApiResponse<List<WorkflowRoleDTO>> listEnabledRoles() {
        return ApiResponse.success(workflowRoleService.listRoles(true));
    }
}
