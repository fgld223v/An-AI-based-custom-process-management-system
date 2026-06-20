import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

export interface FileUploadResult {
  fileName: string
  originalName: string
  url: string
  size: number
}

/**
 * 上传单个文件到后端。
 * 使用独立的 axios 实例（不走 request.ts 拦截器），因为上传需要 multipart/form-data。
 */
export async function uploadFile(file: File): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)

  const authStore = useAuthStore()
  const headers: Record<string, string> = {}
  if (authStore.token) {
    headers.Authorization = `Bearer ${authStore.token}`
  }

  const response = await axios.post('/api/files/upload', formData, {
    headers: { ...headers, 'Content-Type': 'multipart/form-data' },
    timeout: 30000
  })

  const result = response.data
  if (result && result.code === 200) {
    return result.data as FileUploadResult
  }
  throw new Error(result?.message || '上传失败')
}

/**
 * 批量上传多个文件。
 */
export async function uploadFiles(files: File[]): Promise<FileUploadResult[]> {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))

  const authStore = useAuthStore()
  const headers: Record<string, string> = {}
  if (authStore.token) {
    headers.Authorization = `Bearer ${authStore.token}`
  }

  const response = await axios.post('/api/files/upload/batch', formData, {
    headers: { ...headers, 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })

  const result = response.data
  if (result && result.code === 200) {
    return result.data as FileUploadResult[]
  }
  throw new Error(result?.message || '批量上传失败')
}

/**
 * 获取文件的下载/预览 URL。
 */
export function getFileUrl(fileName: string): string {
  return `/api/files/download/${encodeURIComponent(fileName)}`
}
