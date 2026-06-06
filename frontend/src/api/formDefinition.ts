import request from './request'
import type { ApiResponse, FormDefinition } from '@/types/workflow'

export async function getPublishedForms() {
  const response = await request.get<ApiResponse<FormDefinition[]>>('/forms/published')
  return response.data.data || []
}
