import request from './request'
import type { ApiResult } from '@/types/auth'
import type { WorkflowTemplate, WorkflowTemplateDraft } from '@/types/template'

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export async function getTemplatePage(params = {}) {
  const response = await request.get<ApiResult<PageResult<WorkflowTemplate>>>('/templates', { params })
  return response.data.data
}

export async function createTemplate(data: WorkflowTemplateDraft) {
  const response = await request.post<ApiResult<WorkflowTemplate>>('/templates', data)
  return response.data.data
}
