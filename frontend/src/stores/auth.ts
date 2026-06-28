/**
 * 认证状态管理（Pinia Store）。
 *
 * 职责：
 *  - 持久化 token 与用户信息到 localStorage
 *  - 登录 / 获取当前用户 / 退出
 *  - 提供 isLoggedIn、username、role 等派生状态
 *
 * 兼容性：localStorage 中旧数据可能缺少 systemRole，
 * 路由守卫会在 beforeEach 中调用 fetchMe 补全。
 */
import { defineStore } from 'pinia'
import { loginApi, getMeApi } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types/auth'

// localStorage 键名常量，避免魔法字符串
const TOKEN_KEY = 'ai-flow-token'
const USER_KEY = 'ai-flow-user'

/** 从 localStorage 读取用户信息（可能为 null） */
function readUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) as UserInfo : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',  // 页面刷新后从本地恢复
    user: readUser() as UserInfo | null
  }),
  getters: {
    /** 是否已登录 —— 仅以 token 存在为依据 */
    isLoggedIn: (state) => Boolean(state.token),
    /** 显示名称：优先昵称，其次用户名 */
    username: (state) => state.user?.nickname || state.user?.username || '用户',
    /** 角色标识（如 USER、ADMIN 等） */
    role: (state) => state.user?.role || 'USER'
  },
  actions: {
    /** 登录：调用 API，持久化 token 与用户信息 */
    async login(payload: LoginRequest) {
      const result = await loginApi(payload)
      this.token = result.token
      this.user = {
        id: result.userId,
        username: result.username,
        nickname: result.nickname,
        role: result.role,
        systemRole: result.systemRole,
        departmentId: result.departmentId,
        supervisorId: result.supervisorId,
        managedBizTypeIds: result.managedBizTypeIds
      }
      // 持久化到 localStorage，刷新页面后可恢复登录态
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    /** 获取当前登录用户信息（用于补全 systemRole 等字段） */
    async fetchMe() {
      if (!this.token) return
      this.user = await getMeApi()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    /** 退出登录：清除状态与本地存储 */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
