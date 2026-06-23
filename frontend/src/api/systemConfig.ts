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

/** 获取所有系统配置项 */
export async function getSystemConfigList(): Promise<any[]> {
  return await request.get('/api/system-config') || []
}

/** 获取单个配置项 */
export async function getSystemConfig(key: string): Promise<any> {
  return await request.get(`/api/system-config/${key}`)
}

/** 更新配置项 */
export async function updateSystemConfig(key: string, configValue: string): Promise<void> {
  return await request.put(`/api/system-config/${key}`, { configValue })
}

/** 新增配置项 */
export async function createSystemConfig(data: {
  configKey: string
  configName: string
  configValue: string
  valueType: string
  description: string
}): Promise<any> {
  return await request.post('/api/system-config', data)
}

/** 删除配置项 */
export async function deleteSystemConfig(key: string): Promise<void> {
  return await request.delete(`/api/system-config/${key}`)
}
