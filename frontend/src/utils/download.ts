import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

/**
 * 下载服务器生成的 Excel 文件。跳过 JSON 拦截器直接处理 blob。
 */
export async function downloadBlob(url: string, filename: string) {
  const authStore = useAuthStore()
  const response = await axios.get(url, {
    responseType: 'blob',
    headers: authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {}
  })

  const blob = response.data as Blob
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

/**
 * 上传 Excel 文件到指定 URL，返回 JSON 结果。
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
