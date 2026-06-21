import request from './request'
import type { BusinessProcessInstance, BusinessProcessInstanceListParams, FormSubmission, ProcessTimeline } from '@/types/workflow'

export async function getBusinessProcessInstances(params: BusinessProcessInstanceListParams = {}) {
  return await request.get<BusinessProcessInstance[]>('/api/business-monitor/instances', { params }) || []
}

export async function getBusinessProcessInstanceDetail(id: number) {
  return await request.get<BusinessProcessInstance>(`/api/business-monitor/instances/${id}`)
}

export async function getBusinessProcessInstanceTimeline(id: number) {
  return await request.get<ProcessTimeline>(`/api/business-monitor/instances/${id}/timeline`)
}

export async function getBusinessProcessInstanceSubmissions(id: number) {
  return await request.get<FormSubmission[]>(`/api/business-monitor/instances/${id}/submissions`) || []
}
