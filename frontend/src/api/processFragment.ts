import request from './request'
import type { ProcessFragment, ProcessFragmentPayload } from '@/types/workflow'

export async function getProcessFragments() {
  return await request.get<ProcessFragment[]>('/api/process-fragments') || []
}

export async function getProcessFragmentDetail(id: number) {
  return await request.get<ProcessFragment>(`/api/process-fragments/${id}`)
}

export async function createProcessFragment(data: ProcessFragmentPayload) {
  return await request.post<ProcessFragment>('/api/process-fragments', data)
}

export async function updateProcessFragment(id: number, data: ProcessFragmentPayload) {
  return await request.put<ProcessFragment>(`/api/process-fragments/${id}`, data)
}

export async function publishProcessFragment(id: number) {
  return await request.post<ProcessFragment>(`/api/process-fragments/${id}/publish`)
}
