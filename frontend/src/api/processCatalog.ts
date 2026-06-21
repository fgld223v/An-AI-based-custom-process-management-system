import request from './request'
import type { ProcessTemplate } from '@/types/workflow'

export async function getAvailableProcesses() {
  return await request.get<ProcessTemplate[]>('/api/process-catalog') || []
}

export async function getAvailableProcessDetail(id: number) {
  return await request.get<ProcessTemplate>(`/api/process-catalog/${id}`)
}
