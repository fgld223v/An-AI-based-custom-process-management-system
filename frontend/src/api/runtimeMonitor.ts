import request from './request'
import type { BusinessProcessInstance, BusinessProcessInstanceListParams, FormSubmission, ProcessTimeline } from '@/types/workflow'

// 全局运行时监控 API：查看所有流程实例、时间线、表单提交

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
