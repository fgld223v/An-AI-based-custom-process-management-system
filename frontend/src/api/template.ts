import request from './request'
import type { WorkflowTemplate, WorkflowTemplateDraft } from '@/types/template'

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export async function getTemplatePage(params = {}) {
  return await request.get<PageResult<WorkflowTemplate>>('/api/templates', { params })
}

export async function createTemplate(data: WorkflowTemplateDraft) {
  return await request.post<WorkflowTemplate>('/api/templates', data)
}
