/**
 * Axios 请求实例 + 拦截器配置。
 *
 * 特性：
 *  - 请求拦截器自动附加 Bearer Token
 *  - 响应拦截器统一处理：
 *      - code=200 → 直接返回 data
 *      - code!=200 → 弹出错误提示并 reject
 *      - 401（非登录接口） → 自动退出并跳转 /login
 *      - 403 → 提示无权限
 *      - 网络错误 → 提示后端未启动
 *  - 防抖机制：2.5s 内相同错误消息不重复弹出
 *
 * 泛型方法 get/post/put/delete 返回直接解包后的 data 而非 ApiResult。
 */
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { ApiResult } from '@/types/auth'

/**
 * 扩展 AxiosInstance，使 get/post/put/delete 返回解包后的 T 而非 AxiosResponse。
 * 这样业务代码可以直接拿到 data，无需手动 .data。
 */
type DataRequest = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
}

// 预定义错误提示文案
const NETWORK_ERROR_MESSAGE = '请求失败，请检查后端服务是否启动'
const UNAUTHORIZED_MESSAGE = '登录已失效，请重新登录'
const FORBIDDEN_MESSAGE = '当前账号没有操作权限，请联系管理员'
const ERROR_TOAST_INTERVAL = 2500  // 重复错误提示的最小间隔（ms）

// 防抖变量：记录上一次错误提示的内容和时间
let lastErrorMessage = ''
let lastErrorTime = 0

const request = axios.create({
  baseURL: '',       // 使用相对路径，由 Vite 代理转发
  timeout: 60000     // 60 秒超时
}) as unknown as DataRequest

// ==================== 请求拦截器 ====================
request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    // 附带 Bearer Token 用于身份认证
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

// ==================== 响应拦截器 ====================
request.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (result && typeof result.code === 'number') {
      if (result.code === 200) {
        // 成功：直接返回 data，业务代码无需解包
        return result.data
      }
      const message = result.message || '请求失败'
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }
    // 非标准格式，原样返回
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const responseMessage = error.response?.data?.message
    const requestUrl = error.config?.url || ''
    const isLoginEndpoint = requestUrl.includes('/api/auth/login')

    // 401：未授权
    if (status === 401) {
      if (isLoginEndpoint) {
        // 登录接口的 401 是用户名或密码错误，直接透传，不触发退出
        const message = responseMessage || '用户名或密码错误'
        return Promise.reject(new Error(message))
      }
      // 非登录接口的 401 → token 失效，退出并跳转登录
      const authStore = useAuthStore()
      authStore.logout()
      router.replace('/login')
      showErrorOnce(UNAUTHORIZED_MESSAGE)
      return Promise.reject(new Error(UNAUTHORIZED_MESSAGE))
    }

    // 403：无权限
    if (status === 403) {
      const message = responseMessage || FORBIDDEN_MESSAGE
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }

    // 其他 HTTP 错误（4xx, 5xx）
    if (error.response) {
      const message = responseMessage || `请求失败，状态码 ${status}`
      showErrorOnce(message)
      return Promise.reject(new Error(message))
    }

    // 网络错误（无响应，如后端未启动）
    showErrorOnce(NETWORK_ERROR_MESSAGE)
    return Promise.reject(new Error(NETWORK_ERROR_MESSAGE))
  }
)

/**
 * 防抖展示错误提示。
 * 在 ERROR_TOAST_INTERVAL 毫秒内，相同消息不重复弹出，避免短时间内刷屏。
 */
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
