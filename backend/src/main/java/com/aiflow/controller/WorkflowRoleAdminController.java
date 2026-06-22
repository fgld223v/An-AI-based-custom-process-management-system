package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.WorkflowRoleAssignmentDTO;
import com.aiflow.dto.WorkflowRoleAssignmentRequest;
import com.aiflow.dto.WorkflowRoleCreateRequest;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.dto.WorkflowRoleUpdateRequest;
import com.aiflow.service.WorkflowRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workflow-roles")
public class WorkflowRoleAdminController {

    private final WorkflowRoleService workflowRoleService;

    @GetMapping
    public ApiResponse<List<WorkflowRoleDTO>> listRoles() {
        return ApiResponse.success(workflowRoleService.listRoles(false));
    }

    @PostMapping
    public ApiResponse<WorkflowRoleDTO> createRole(
            @Valid @RequestBody WorkflowRoleCreateRequest request) {
        return ApiResponse.success(workflowRoleService.createRole(request));
    }

    @PutMapping("/{roleId}")
    public ApiResponse<WorkflowRoleDTO> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody WorkflowRoleUpdateRequest request) {
        return ApiResponse.success(workflowRoleService.updateRole(roleId, request));
    }

    @DeleteMapping("/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable Long roleId) {
        workflowRoleService.deleteRole(roleId);
        return ApiResponse.success();
    }

    @GetMapping("/{roleId}/assignments")
    public ApiResponse<List<WorkflowRoleAssignmentDTO>> listRoleAssignments(
            @PathVariable Long roleId) {
        return ApiResponse.success(workflowRoleService.listRoleAssignments(roleId));
    }

    @PostMapping("/{roleId}/assignments")
    public ApiResponse<WorkflowRoleAssignmentDTO> assignRole(
            @PathVariable Long roleId,
            @Valid @RequestBody WorkflowRoleAssignmentRequest request) {
        return ApiResponse.success(workflowRoleService.assignRole(roleId, request));
    }

    @GetMapping("/user-assignments/{userId}")
    public ApiResponse<List<WorkflowRoleAssignmentDTO>> listUserAssignments(
            @PathVariable Long userId) {
        return ApiResponse.success(workflowRoleService.listUserAssignments(userId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> revokeAssignment(@PathVariable Long assignmentId) {
        workflowRoleService.revokeAssignment(assignmentId);
        return ApiResponse.success();
    }
}
