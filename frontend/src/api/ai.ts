import request from './request'

/** AI 流程生成 —— 返回 BPMN XML + nodeConfig */
export async function generateProcess(description: string) {
  return await request.post('/api/ai/generate-process', { description })
}

/** AI 表单生成 —— 返回 formName + fields 数组 */
export async function generateForm(description: string) {
  return await request.post('/api/ai/generate-form', { description })
}

/** AI 流程优化 — 分析单个模板 */
export async function optimizeTemplate(templateId: number) {
  return await request.post(`/api/ai/optimize/${templateId}`)
}

/** AI 流程优化 — 批量分析所有模板 */
export async function optimizeAll() {
  return await request.post('/api/ai/optimize-all')
}

/** AI 流程优化 — 采纳单条建议 */
export async function adoptSuggestion(templateId: number, type: string, nodeKey: string | null | undefined, suggestion: string) {
  return await request.post(`/api/ai/optimize/${templateId}/adopt`, { type, nodeKey, suggestion })
}
