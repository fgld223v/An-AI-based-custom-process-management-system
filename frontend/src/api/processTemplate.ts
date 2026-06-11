import request from './request'
import type { ProcessTemplate, ProcessTemplatePayload, TemplateFormBinding } from '@/types/workflow'

export async function getProcessTemplates() {
  return await request.get<ProcessTemplate[]>('/api/process-templates') || []
}

export async function getProcessTemplateDetail(id: number) {
  return await request.get<ProcessTemplate>(`/api/process-templates/${id}`)
}

export async function getProcessTemplateBoundForm(id: number) {
  return await request.get<TemplateFormBinding>(`/api/process-templates/${id}/form`)
}

export async function createProcessTemplate(data: ProcessTemplatePayload) {
  return await request.post<ProcessTemplate>('/api/process-templates', data)
}

export async function updateProcessTemplate(id: number, data: ProcessTemplatePayload) {
  return await request.put<ProcessTemplate>(`/api/process-templates/${id}`, data)
}

export async function publishProcessTemplate(id: number) {
  return await request.post<ProcessTemplate>(`/api/process-templates/${id}/publish`)
}
