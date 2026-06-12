import request from './request'
import type {
  FormSubmission,
  ProcessInstance,
  ProcessInstanceListParams,
  RuntimeState,
  SaveNodeFormPayload,
  StartProcessPreviewPayload
} from '@/types/workflow'

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

export async function getProcessInstanceSubmissions(id: number) {
  return await request.get<FormSubmission[]>(`/api/process-instances/${id}/submissions`) || []
}

export async function getRuntimeState(id: number) {
  return await request.get<RuntimeState>(`/api/process-instances/${id}/runtime-state`)
}
