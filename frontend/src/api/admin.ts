import request from './request'

export interface DepartmentItem {
  id: number
  deptCode: string
  deptName: string
  parentId?: number | null
  sortOrder: number
  leaderUserId?: number | null
  status: number
}

export async function getDepartments() {
  return await request.get<DepartmentItem[]>('/api/departments') || []
}

export async function getUserList() {
  return await request.get<UserBrief[]>('/api/users') || []
}

export interface UserBrief {
  id: number
  username: string
  nickname: string
  role: string
}

export interface AdminUser {
  id: number
  username: string
  nickname: string
  role: string
  systemRole: string
  departmentId?: number | null
  supervisorId?: number | null
  managedBizTypeIds?: string | null
  enabled: number
  deleted?: number
  createdTime?: string
  updatedTime?: string
}

export interface CreateUserPayload {
  username: string
  password: string
  nickname?: string
  systemRole?: string
  departmentId?: number | null
  supervisorId?: number | null
  managedBizTypeIds?: string | null
  enabled?: number
}

export interface UpdateUserPayload {
  nickname?: string
  phone?: string
  email?: string
  systemRole?: string
  password?: string
  departmentId?: number | null
  supervisorId?: number | null
  managedBizTypeIds?: string | null
  enabled?: number
}

export async function getUsers() {
  return await request.get<AdminUser[]>('/api/admin/users')
}

export async function getUser(id: number) {
  return await request.get<AdminUser>(`/api/admin/users/${id}`)
}

export async function createUser(data: CreateUserPayload) {
  return await request.post<AdminUser>('/api/admin/users', data)
}

export async function updateUser(id: number, data: UpdateUserPayload) {
  return await request.put<AdminUser>(`/api/admin/users/${id}`, data)
}

export async function deleteUser(id: number) {
  return await request.delete<{ deleted: boolean; id: number }>(`/api/admin/users/${id}`)
}

// ================================================================
// Excel 导入 / 导出
// ================================================================

export interface ImportResult {
  total: number
  success: number
  failed: number
  errors: Array<{ row: number; reason: string }>
}

// These use raw axios (blob / multipart), see @/utils/download.ts
export const USER_TEMPLATE_URL = '/api/admin/users/template'
export const USER_EXPORT_URL = '/api/admin/users/export'
export const USER_IMPORT_URL = '/api/admin/users/import'

export const DEPT_TEMPLATE_URL = '/api/admin/departments/template'
export const DEPT_EXPORT_URL = '/api/admin/departments/export'
export const DEPT_IMPORT_URL = '/api/admin/departments/import'
