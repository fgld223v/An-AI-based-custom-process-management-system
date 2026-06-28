import request from './request'
import type { LoginRequest, LoginResponse, ResetPasswordRequest, UserInfo } from '@/types/auth'

// 认证相关 API：登录、重置密码、获取当前用户信息

export async function loginApi(data: LoginRequest) {
  return await request.post<LoginResponse>('/api/auth/login', data)
}

export async function resetPasswordApi(data: ResetPasswordRequest) {
  return await request.post<{ message: string }>('/api/auth/reset-password', data)
}

export async function getMeApi() {
  return await request.get<UserInfo>('/api/user/me')
}
