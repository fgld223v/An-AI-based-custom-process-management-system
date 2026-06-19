import request from './request'

export interface AiProcessNodeConfigItem {
  nodeKey: string
  nodeName: string
  businessType: string
}

export interface AiGenerateProcessResult {
  bpmnXml: string
  nodeConfig: AiProcessNodeConfigItem[]
  summary: string
}

export interface AiGenerateFormResult {
  fieldList: string
  formSchema: string
}

export interface AiApprovalSuggestion {
  suggestion: string
  reason: string
  confidence: number
  riskPoints?: string[]
}

/** AI 流程生成 —— 返回 BPMN XML + nodeConfig */
export async function generateProcess(description: string) {
  return await request.post<AiGenerateProcessResult>('/api/ai/generate-process', { description })
}

/** AI 表单生成 —— 返回 fieldList + formSchema */
export async function generateForm(description: string) {
  return await request.post<AiGenerateFormResult>('/api/ai/generate-form', { description })
}

/** AI 审批建议 —— 返回建议（通过/驳回）+ 理由 */
export async function suggestApproval(instanceId: number, nodeKey: string) {
  return await request.post<AiApprovalSuggestion>('/api/ai/suggest-approval', { instanceId, nodeKey })
}
