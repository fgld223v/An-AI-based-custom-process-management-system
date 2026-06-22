import request from './request'

export type WorkflowRoleScope = 'global' | 'department'

export interface WorkflowRole {
  id: number
  roleCode: string
  roleName: string
  description?: string | null
  roleScope: WorkflowRoleScope
  enabled: number
  memberCount: number
  createdAt?: string
  updatedAt?: string
}

export interface WorkflowRoleAssignment {
  id: number
  roleId: number
  roleCode: string
  roleName: string
  roleScope: WorkflowRoleScope
  userId: number
  username?: string
  userName: string
  departmentId?: number | null
  departmentName?: string | null
  createdAt?: string
}

export interface WorkflowRoleCreatePayload {
  roleCode: string
  roleName: string
  description?: string
  roleScope: WorkflowRoleScope
  enabled?: number
}

export interface WorkflowRoleUpdatePayload {
  roleName: string
  description?: string
  enabled: number
}

export async function getWorkflowRoles() {
  return await request.get<WorkflowRole[]>('/api/admin/workflow-roles') || []
}

export async function createWorkflowRole(payload: WorkflowRoleCreatePayload) {
  return await request.post<WorkflowRole>('/api/admin/workflow-roles', payload)
}

export async function updateWorkflowRole(roleId: number, payload: WorkflowRoleUpdatePayload) {
  return await request.put<WorkflowRole>(`/api/admin/workflow-roles/${roleId}`, payload)
}

export async function deleteWorkflowRole(roleId: number) {
  return await request.delete(`/api/admin/workflow-roles/${roleId}`)
}

export async function getRoleAssignments(roleId: number) {
  return await request.get<WorkflowRoleAssignment[]>(`/api/admin/workflow-roles/${roleId}/assignments`) || []
}

export async function assignWorkflowRole(roleId: number, userId: number, departmentId?: number | null) {
  return await request.post<WorkflowRoleAssignment>(`/api/admin/workflow-roles/${roleId}/assignments`, {
    userId,
    departmentId: departmentId ?? null
  })
}

export async function revokeWorkflowRoleAssignment(assignmentId: number) {
  return await request.delete(`/api/admin/workflow-roles/assignments/${assignmentId}`)
}
