/** 系统角色：super_admin / biz_admin / normal_user */
export type SystemRole = 'super_admin' | 'biz_admin' | 'normal_user'

export interface LoginRequest {
  username: string
  password: string
}

export interface ResetPasswordRequest {
  username: string
  verifyType?: 'phone' | 'email'
  verifyValue?: string
  newPassword: string
  confirmPassword: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: number
  username: string
  nickname: string
  role: string
  systemRole: SystemRole
  departmentId?: number | null
  supervisorId?: number | null
  managedBizTypeIds?: string | null
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  phone?: string | null
  email?: string | null
  role: string
  systemRole: SystemRole
  departmentId?: number | null
  supervisorId?: number | null
  managedBizTypeIds?: string | null
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
