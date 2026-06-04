export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface WorkflowTemplate {
  id: number
  templateName: string
  businessType: string
  formJson: string
  bpmnXml: string
  status: TemplateStatus
  createdBy?: number
  createdTime?: string
  updatedTime?: string
}

export interface WorkflowTemplateDraft {
  templateName: string
  businessType: string
  description?: string
  formJson: string
  bpmnXml: string
  status: TemplateStatus
}
