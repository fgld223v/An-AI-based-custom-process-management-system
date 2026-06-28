/**
 * 认证相关类型定义。
 *
 * 包含：
 *  - SystemRole：系统角色联合类型
 *  - 登录/重置密码请求体
 *  - 登录响应 / 用户信息
 *  - 统一 API 响应包装类型 ApiResult<T>
 */

/** 系统角色：超管 / 业务管理员 / 普通用户 */
export type SystemRole = 'super_admin' | 'biz_admin' | 'normal_user'

/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
}

/** 重置密码请求参数 */
export interface ResetPasswordRequest {
  username: string
  verifyType?: 'phone' | 'email'   // 验证方式
  verifyValue?: string              // 验证码
  newPassword: string
  confirmPassword: string
}

/** 登录成功响应 */
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
  managedBizTypeIds?: string | null  // 管理的业务类型 ID，逗号分隔
}

/** 当前登录用户信息 */
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

/** 统一 API 响应包装 */
export interface ApiResult<T> {
  code: number     // 业务状态码，200 表示成功
  message: string  // 提示信息
  data: T          // 响应数据
}
