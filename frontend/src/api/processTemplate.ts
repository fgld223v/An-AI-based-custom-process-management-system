import request from './request'
import type { ApiResponse, ProcessTemplate, ProcessTemplatePayload } from '@/types/workflow'

export async function getProcessTemplates() {
  const response = await request.get<ApiResponse<ProcessTemplate[]>>('/process-templates')
  return response.data.data || []
}

export async function getProcessTemplateDetail(id: number) {
  const response = await request.get<ApiResponse<ProcessTemplate>>(`/process-templates/${id}`)
  return response.data.data
}

export async function createProcessTemplate(data: ProcessTemplatePayload) {
  const response = await request.post<ApiResponse<ProcessTemplate>>('/process-templates', data)
  return response.data.data
}

export async function updateProcessTemplate(id: number, data: ProcessTemplatePayload) {
  const response = await request.put<ApiResponse<ProcessTemplate>>(`/process-templates/${id}`, data)
  return response.data.data
}

export async function publishProcessTemplate(id: number) {
  const response = await request.post<ApiResponse<ProcessTemplate>>(`/process-templates/${id}/publish`)
  return response.data.data
}
