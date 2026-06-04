import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface UserInfo {
  id: number
  username: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userInfo = ref<UserInfo | null>(null)

  const login = async (username: string, password: string) => {
    const res: any = await axios.post('/api/auth/login', { username, password })
    token.value = res.token
    userInfo.value = res.user
    localStorage.setItem('token', res.token)
    localStorage.setItem('userInfo', JSON.stringify(res.user))
    return res
  }

  const logout = () => {
    token.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  const loadFromStorage = () => {
    const savedUserInfo = localStorage.getItem('userInfo')
    if (savedUserInfo) {
      userInfo.value = JSON.parse(savedUserInfo)
    }
  }

  loadFromStorage()

  return {
    token,
    userInfo,
    login,
    logout
  }
})
