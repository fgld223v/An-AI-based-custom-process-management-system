import request from './request'
import type { ApiResponse, ProcessFragment, ProcessFragmentPayload } from '@/types/workflow'

export async function getProcessFragments() {
  const response = await request.get<ApiResponse<ProcessFragment[]>>('/process-fragments')
  return response.data.data || []
}

export async function getProcessFragmentDetail(id: number) {
  const response = await request.get<ApiResponse<ProcessFragment>>(`/process-fragments/${id}`)
  return response.data.data
}

export async function createProcessFragment(data: ProcessFragmentPayload) {
  const response = await request.post<ApiResponse<ProcessFragment>>('/process-fragments', data)
  return response.data.data
}

export async function updateProcessFragment(id: number, data: ProcessFragmentPayload) {
  const response = await request.put<ApiResponse<ProcessFragment>>(`/process-fragments/${id}`, data)
  return response.data.data
}

export async function publishProcessFragment(id: number) {
  const response = await request.post<ApiResponse<ProcessFragment>>(`/process-fragments/${id}/publish`)
  return response.data.data
}
