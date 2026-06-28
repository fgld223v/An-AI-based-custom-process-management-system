/**
 * 文件上传/下载工具。
 *
 * 与主请求实例（request.ts）解耦，直接使用 axios 创建独立请求：
 *  - downloadBlob：下载二进制流并触发浏览器下载
 *  - uploadExcel：上传 Excel 文件并返回 JSON 结果
 *
 * 注意：downloadBlob 不使用封装的 request（因为 responseType: 'blob' 会跳过 JSON 拦截器）。
 */
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

/**
 * 下载服务器生成的 Excel（或其他二进制）文件。
 *
 * 原理：以 blob 方式获取数据 → 创建临时 a 元素 → 模拟点击触发下载 → 清理
 *
 * @param url      下载接口路径
 * @param filename 浏览器保存时的默认文件名
 */
export async function downloadBlob(url: string, filename: string) {
  const authStore = useAuthStore()
  const response = await axios.get(url, {
    responseType: 'blob',  // 二进制流，不经过 JSON 解析
    headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {}
  })

  const blob = response.data as Blob
  // 创建临时下载链接
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()                         // 触发浏览器下载
  document.body.removeChild(link)      // 清理 DOM
  URL.revokeObjectURL(link.href)       // 释放内存
}

/**
 * 上传 Excel 文件。
 *
 * @param url  上传接口路径
 * @param file 用户选择的 File 对象
 * @returns 解包后的 JSON 数据（code === 200 时）
 */
export async function uploadExcel(url: string, file: File): Promise<any> {
  const authStore = useAuthStore()
  const formData = new FormData()
  formData.append('file', file)

  const response = await axios.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {})
    }
  })

  const result = response.data
  if (result && result.code === 200) {
    return result.data
  }
  throw new Error(result?.message || '导入失败')
}
