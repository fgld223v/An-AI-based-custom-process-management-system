import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { ApiResult } from '@/types/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (result && typeof result.code === 'number' && result.code !== 200) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.replace('/login')
      ElMessage.warning('登录已失效，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || '网络请求异常')
    }
    return Promise.reject(error)
  }
)

export default request
