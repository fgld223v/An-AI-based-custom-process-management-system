import request from './request'

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
