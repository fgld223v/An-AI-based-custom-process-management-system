import request from './request'
import type { FormDefinition, FormDefinitionPayload } from '@/types/workflow'

export async function getForms() {
  return await request.get<FormDefinition[]>('/api/forms') || []
}

export async function getPublishedForms() {
  return await request.get<FormDefinition[]>('/api/forms/published') || []
}

export async function getFormDetail(id: number) {
  return await request.get<FormDefinition>(`/api/forms/${id}`)
}

export async function createForm(data: FormDefinitionPayload) {
  return await request.post<FormDefinition>('/api/forms', data)
}

export async function updateForm(id: number, data: FormDefinitionPayload) {
  return await request.put<FormDefinition>(`/api/forms/${id}`, data)
}

export async function publishForm(id: number) {
  return await request.post<FormDefinition>(`/api/forms/${id}/publish`)
}
