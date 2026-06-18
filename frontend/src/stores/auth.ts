import { defineStore } from 'pinia'
import { loginApi, getMeApi } from '@/api/auth'
import type { LoginRequest, UserInfo } from '@/types/auth'

const TOKEN_KEY = 'ai-flow-token'
const USER_KEY = 'ai-flow-user'

function readUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) as UserInfo : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser() as UserInfo | null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    username: (state) => state.user?.nickname || state.user?.username || '用户',
    role: (state) => state.user?.role || 'USER'
  },
  actions: {
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
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    async fetchMe() {
      if (!this.token) return
      this.user = await getMeApi()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
