import request from './request'

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
