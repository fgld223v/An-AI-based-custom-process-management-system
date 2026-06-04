export type UserRole = 'ADMIN' | 'MANAGER' | 'USER'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: number
  username: string
  nickname: string
  role: UserRole
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: UserRole
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
