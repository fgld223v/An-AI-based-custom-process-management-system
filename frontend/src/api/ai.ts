import request from './request'

/** AI 流程生成 —— 返回 BPMN XML + nodeConfig */
export async function generateProcess(description: string) {
  return await request.post('/api/ai/generate-process', { description })
}
