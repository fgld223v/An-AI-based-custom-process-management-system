import request from './request'

export interface AutomationRule {
  id: string
  name: string
  scope: string
  field: string
  operator: string
  value: string
  action: 'approve' | 'notify'
  enabled: boolean
  remark: string
  updatedAt: string
}

/** 获取全部自动化策略规则（JSON 数组） */
export async function getAutomationRules(): Promise<AutomationRule[]> {
  const res = await request.get<string>('/api/system-config/automation-rules')
  const raw = (res as any)?.data ?? res ?? '[]'
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as AutomationRule[]
    } catch {
      return []
    }
  }
  return Array.isArray(raw) ? raw : []
}

/** 保存全部自动化策略规则（全量替换） */
export async function saveAutomationRules(rules: AutomationRule[]): Promise<void> {
  await request.put('/api/system-config/automation-rules', {
    rules: JSON.stringify(rules)
  })
}
