package com.aiflow.service;

import com.aiflow.dto.WorkflowRoleAssignmentDTO;
import com.aiflow.dto.WorkflowRoleAssignmentRequest;
import com.aiflow.dto.WorkflowRoleCreateRequest;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.dto.WorkflowRoleUpdateRequest;

import java.util.List;

/**
 * 工作流角色服务接口，提供流程角色的 CRUD、用户分配及角色成员解析能力。
 */
public interface WorkflowRoleService {

    /**
     * 查询角色列表，可选择是否仅返回启用的角色。
     */
    List<WorkflowRoleDTO> listRoles(boolean enabledOnly);

    /**
     * 创建新角色。
     */
    WorkflowRoleDTO createRole(WorkflowRoleCreateRequest request);

    /**
     * 更新角色信息。
     */
    WorkflowRoleDTO updateRole(Long roleId, WorkflowRoleUpdateRequest request);

    /**
     * 删除角色。
     */
    void deleteRole(Long roleId);

    /**
     * 查询指定角色下的所有用户分配。
     */
    List<WorkflowRoleAssignmentDTO> listRoleAssignments(Long roleId);

    /**
     * 查询指定用户的所有角色分配。
     */
    List<WorkflowRoleAssignmentDTO> listUserAssignments(Long userId);

    /**
     * 为角色分配用户。
     */
    WorkflowRoleAssignmentDTO assignRole(Long roleId, WorkflowRoleAssignmentRequest request);

    /**
     * 撤销指定的角色分配。
     */
    void revokeAssignment(Long assignmentId);

    /**
     * 根据角色编码和部门ID解析当前生效的用户ID列表。
     */
    List<Long> resolveActiveUserIds(String roleCode, Long departmentId);
}
