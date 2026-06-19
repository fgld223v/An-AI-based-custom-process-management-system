import request from './request'

export interface AiNodeConfigItem {
  nodeKey: string
  nodeName: string
  businessType: string
}

export interface AiGenerateProcessResult {
  bpmnXml: string
  nodeConfig: AiNodeConfigItem[]
  summary: string
}

export interface AiGenerateFormResult {
  fieldList: string
  formSchema: string
}

export async function generateProcess(description: string) {
  return await request.post<AiGenerateProcessResult>('/api/ai/generate-process', { description })
}

export async function generateForm(description: string) {
  return await request.post<AiGenerateFormResult>('/api/ai/generate-form', { description })
}
