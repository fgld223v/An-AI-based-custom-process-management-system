import request from './request'

export interface AiProcessNodeConfigItem {
  nodeKey: string
  nodeName: string
  businessType: string
  /** 审批模式：SINGLE（单人）/ ALL（会签）/ ANY（或签），仅 approval 节点有效 */
  approvalMode?: string
  /** 审批人分配策略：DIRECT_SUPERVISOR / DEPARTMENT_MANAGER / ROLE / SPECIFIC_USERS */
  assignStrategy?: string
  /** 抄送目标：APPLICANT / APPROVER / USER，仅 notify 节点有效 */
  notifyTarget?: string
  /** 通知渠道：in_app / email / both，仅 notify 节点有效 */
  notifyChannel?: string
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

/** AI 流程优化 —— 分析单个模板 */
export async function optimizeTemplate(templateId: number) {
  return await request.post(`/api/ai/optimize/${templateId}`)
}

/** AI 流程优化 —— 批量分析所有模板 */
export async function optimizeAll() {
  return await request.post('/api/ai/optimize-all')
}

/** AI 流程优化 —— 采纳单条建议 */
export async function adoptSuggestion(templateId: number, type: string, nodeKey: string | null | undefined, suggestion: string) {
  return await request.post(`/api/ai/optimize/${templateId}/adopt`, { type, nodeKey, suggestion })
}
