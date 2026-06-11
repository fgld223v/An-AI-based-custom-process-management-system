import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { ApiResult } from '@/types/auth'

type DataRequest = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const NETWORK_ERROR_MESSAGE = '请求失败，请检查后端服务是否启动'
const UNAUTHORIZED_MESSAGE = '登录已失效，请重新登录'
const FORBIDDEN_MESSAGE = '当前账号没有操作权限，请重新登录或联系管理员'
const ERROR_TOAST_INTERVAL = 2500
let lastErrorMessage = ''
let lastErrorTime = 0

const request = axios.create({
  baseURL: '',
  timeout: 15000
}) as unknown as DataRequest

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
    if (result && typeof result.code === 'number') {
      if (result.code === 200) {
        return result.data
      }
      const message = result.message || '请求失败'
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const responseMessage = error.response?.data?.message

    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.replace('/login')
      showErrorOnce(UNAUTHORIZED_MESSAGE)
      return Promise.reject(new Error(UNAUTHORIZED_MESSAGE))
    }

    if (status === 403) {
      const message = responseMessage || FORBIDDEN_MESSAGE
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }

    if (error.response) {
      const message = responseMessage || `请求失败，状态码 ${status}`
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }

    showErrorOnce(NETWORK_ERROR_MESSAGE)
    return Promise.reject(new Error(NETWORK_ERROR_MESSAGE))
  }
)

function showErrorOnce(message: string) {
  const now = Date.now()
  if (message === lastErrorMessage && now - lastErrorTime < ERROR_TOAST_INTERVAL) {
    return
  }
  lastErrorMessage = message
  lastErrorTime = now
  ElMessage.error(message)
}

export default request
