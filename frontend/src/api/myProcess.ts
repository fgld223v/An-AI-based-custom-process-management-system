import request from './request'
import type { ProcessTemplate, ProcessTemplatePayload, TemplateFormBinding } from '@/types/workflow'

// 我的流程 API：个人流程的增删改查、发布与版本管理

export async function getMyProcesses() {
  return await request.get<ProcessTemplate[]>('/api/my-processes') || []
}

export async function getMyProcessDetail(id: number) {
  return await request.get<ProcessTemplate>(`/api/my-processes/${id}`)
}

export async function getMyProcessBoundForm(id: number) {
  return await request.get<TemplateFormBinding>(`/api/my-processes/${id}/form`)
}

export async function createMyProcess(data: ProcessTemplatePayload) {
  return await request.post<ProcessTemplate>('/api/my-processes', data)
}

export async function updateMyProcess(id: number, data: ProcessTemplatePayload) {
  return await request.put<ProcessTemplate>(`/api/my-processes/${id}`, data)
}

export async function publishMyProcess(id: number) {
  return await request.post<ProcessTemplate>(`/api/my-processes/${id}/publish`)
}

export async function createMyProcessVersion(id: number) {
  return await request.post<ProcessTemplate>(`/api/my-processes/${id}/new-version`)
}

export async function unpublishMyProcess(id: number) {
  return await request.post<ProcessTemplate>(`/api/my-processes/${id}/unpublish`)
}

export async function deleteMyProcess(id: number) {
  return await request.delete<void>(`/api/my-processes/${id}`)
}
