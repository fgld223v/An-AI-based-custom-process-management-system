import request from './request'

/** AI 流程生成 —— 返回 BPMN XML + nodeConfig */
export async function generateProcess(description: string) {
  return await request.post('/api/ai/generate-process', { description })
}

/** AI 表单生成 —— 返回 formName + fields 数组 */
export async function generateForm(description: string) {
  return await request.post('/api/ai/generate-form', { description })
}
