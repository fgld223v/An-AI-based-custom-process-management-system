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

/**
 * 工作流角色管理控制器（管理员视角），提供角色的 CRUD 及用户分配管理。
 *
 * <p>基础路径: /api/admin/workflow-roles</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workflow-roles")
public class WorkflowRoleAdminController {

    private final WorkflowRoleService workflowRoleService;

    /**
     * GET /api/admin/workflow-roles — 查询所有角色（含禁用）。
     */
    @GetMapping
    public ApiResponse<List<WorkflowRoleDTO>> listRoles() {
        return ApiResponse.success(workflowRoleService.listRoles(false));
    }

    /**
     * POST /api/admin/workflow-roles — 创建新角色。
     */
    @PostMapping
    public ApiResponse<WorkflowRoleDTO> createRole(
            @Valid @RequestBody WorkflowRoleCreateRequest request) {
        return ApiResponse.success(workflowRoleService.createRole(request));
    }

    /**
     * PUT /api/admin/workflow-roles/{roleId} — 更新角色信息。
     */
    @PutMapping("/{roleId}")
    public ApiResponse<WorkflowRoleDTO> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody WorkflowRoleUpdateRequest request) {
        return ApiResponse.success(workflowRoleService.updateRole(roleId, request));
    }

    /**
     * DELETE /api/admin/workflow-roles/{roleId} — 删除角色。
     */
    @DeleteMapping("/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable Long roleId) {
        workflowRoleService.deleteRole(roleId);
        return ApiResponse.success();
    }

    /**
     * GET /api/admin/workflow-roles/{roleId}/assignments — 查询角色下的用户分配列表。
     */
    @GetMapping("/{roleId}/assignments")
    public ApiResponse<List<WorkflowRoleAssignmentDTO>> listRoleAssignments(
            @PathVariable Long roleId) {
        return ApiResponse.success(workflowRoleService.listRoleAssignments(roleId));
    }

    /**
     * POST /api/admin/workflow-roles/{roleId}/assignments — 为角色分配用户。
     */
    @PostMapping("/{roleId}/assignments")
    public ApiResponse<WorkflowRoleAssignmentDTO> assignRole(
            @PathVariable Long roleId,
            @Valid @RequestBody WorkflowRoleAssignmentRequest request) {
        return ApiResponse.success(workflowRoleService.assignRole(roleId, request));
    }

    /**
     * GET /api/admin/workflow-roles/user-assignments/{userId} — 查询指定用户的所有角色分配。
     */
    @GetMapping("/user-assignments/{userId}")
    public ApiResponse<List<WorkflowRoleAssignmentDTO>> listUserAssignments(
            @PathVariable Long userId) {
        return ApiResponse.success(workflowRoleService.listUserAssignments(userId));
    }

    /**
     * DELETE /api/admin/workflow-roles/assignments/{assignmentId} — 撤销角色分配。
     */
    @DeleteMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> revokeAssignment(@PathVariable Long assignmentId) {
        workflowRoleService.revokeAssignment(assignmentId);
        return ApiResponse.success();
    }
}
