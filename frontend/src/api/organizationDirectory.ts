import request from './request'

// 组织目录 API：获取部门/用户选项、工作流角色选项

export interface OrganizationDepartmentOption {
  id: number
  name: string
  parentId?: number | null
}

export interface OrganizationUserOption {
  id: number
  name: string
  departmentId?: number | null
}

export interface WorkflowRoleOption {
  id: number
  roleCode: string
  roleName: string
  roleScope: 'global' | 'department'
}

export async function getOrganizationDepartments() {
  return await request.get<OrganizationDepartmentOption[]>('/api/organization-directory/departments') || []
}

export async function getOrganizationUsers() {
  return await request.get<OrganizationUserOption[]>('/api/organization-directory/users') || []
}

export async function getWorkflowRoleOptions() {
  return await request.get<WorkflowRoleOption[]>('/api/workflow-roles') || []
}
