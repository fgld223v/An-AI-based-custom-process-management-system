import request from './request'
import type { ApiResult, LoginRequest, LoginResponse, UserInfo } from '@/types/auth'

export async function loginApi(data: LoginRequest) {
  const response = await request.post<ApiResult<LoginResponse>>('/auth/login', data)
  return response.data.data
}

export async function getMeApi() {
  const response = await request.get<ApiResult<UserInfo>>('/user/me')
  return response.data.data
}
