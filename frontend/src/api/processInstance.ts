import request from './request'
import type {
  FormSubmission,
  NotificationItem,
  ProcessInstance,
  ProcessDiagram,
  ProcessInstanceListParams,
  RuntimeState,
  SaveNodeFormPayload,
  StartProcessPreviewPayload
} from '@/types/workflow'

// 流程实例 API：创建草稿、填写表单、提交、查看流转图、运行时状态、催办

export async function getProcessInstanceList(params: ProcessInstanceListParams = {}) {
  return await request.get<ProcessInstance[]>('/api/process-instances', { params }) || []
}

export async function createProcessInstanceDraft(data: StartProcessPreviewPayload) {
  return await request.post<ProcessInstance>('/api/process-instances/draft', data)
}

export async function saveNodeForm(data: SaveNodeFormPayload) {
  return await request.post<FormSubmission>('/api/process-instances/node-form', data)
}

export async function submitProcessInstance(id: number) {
  return await request.put<ProcessInstance>(`/api/process-instances/${id}/submit`)
}

export async function getProcessInstanceDetail(id: number) {
  return await request.get<ProcessInstance>(`/api/process-instances/${id}`)
}

export async function getProcessInstanceDiagram(id: number) {
  return await request.get<ProcessDiagram>(`/api/process-instances/${id}/diagram`)
}

export async function getProcessInstanceSubmissions(id: number) {
  return await request.get<FormSubmission[]>(`/api/process-instances/${id}/submissions`) || []
}

export async function getRuntimeState(id: number) {
  return await request.get<RuntimeState>(`/api/process-instances/${id}/runtime-state`)
}

export async function urgeProcessInstance(id: number) {
  return await request.post<NotificationItem>(`/api/process-instances/${id}/urge`)
}
