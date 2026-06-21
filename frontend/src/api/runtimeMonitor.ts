import request from './request'
import type { BusinessProcessInstance, BusinessProcessInstanceListParams, FormSubmission, ProcessTimeline } from '@/types/workflow'

export async function getGlobalProcessInstances(params: BusinessProcessInstanceListParams = {}) {
  return await request.get<BusinessProcessInstance[]>('/api/runtime-monitor/instances', { params }) || []
}

export async function getGlobalProcessInstanceDetail(id: number) {
  return await request.get<BusinessProcessInstance>(`/api/runtime-monitor/instances/${id}`)
}

export async function getGlobalProcessInstanceTimeline(id: number) {
  return await request.get<ProcessTimeline>(`/api/runtime-monitor/instances/${id}/timeline`)
}

export async function getGlobalProcessInstanceSubmissions(id: number) {
  return await request.get<FormSubmission[]>(`/api/runtime-monitor/instances/${id}/submissions`) || []
}
