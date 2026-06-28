import request from './request'
import type { ProcessRoutePreview, ProcessTemplate } from '@/types/workflow'

// 流程目录 API：获取可用的流程、详情及路由预览

export async function getAvailableProcesses() {
  return await request.get<ProcessTemplate[]>('/api/process-catalog') || []
}

export async function getAvailableProcessDetail(id: number) {
  return await request.get<ProcessTemplate>(`/api/process-catalog/${id}`)
}

export async function getAvailableProcessRoutePreview(id: number) {
  return await request.get<ProcessRoutePreview>(`/api/process-catalog/${id}/route-preview`)
}
