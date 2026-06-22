package com.aiflow.service;

import com.aiflow.dto.WorkflowRoleAssignmentDTO;
import com.aiflow.dto.WorkflowRoleAssignmentRequest;
import com.aiflow.dto.WorkflowRoleCreateRequest;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.dto.WorkflowRoleUpdateRequest;

import java.util.List;

public interface WorkflowRoleService {

    List<WorkflowRoleDTO> listRoles(boolean enabledOnly);

    WorkflowRoleDTO createRole(WorkflowRoleCreateRequest request);

    WorkflowRoleDTO updateRole(Long roleId, WorkflowRoleUpdateRequest request);

    void deleteRole(Long roleId);

    List<WorkflowRoleAssignmentDTO> listRoleAssignments(Long roleId);

    List<WorkflowRoleAssignmentDTO> listUserAssignments(Long userId);

    WorkflowRoleAssignmentDTO assignRole(Long roleId, WorkflowRoleAssignmentRequest request);

    void revokeAssignment(Long assignmentId);

    List<Long> resolveActiveUserIds(String roleCode, Long departmentId);
}
