export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface BizType {
  id: number
  parentId?: number | null
  typeCode: string
  typeName: string
  description?: string
  sortOrder?: number
}

export interface FormDefinition {
  id: number
  formCode: string
  formName: string
  bizTypeId?: number | null
  version?: number
  status?: string
  fieldList?: string
  formSchema?: string
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface FormDefinitionPayload {
  formCode?: string
  formName: string
  bizTypeId?: number | null
  version?: number
  fieldList?: string
  formSchema?: string
}

export interface ProcessTemplate {
  id: number
  templateCode: string
  templateName: string
  bizTypeId?: number | null
  formId?: number | null
  version?: number
  status?: string
  sourceType?: string
  bpmnXml?: string
  nodeConfig?: string
  formBindConfig?: string
  flowableDeploymentId?: string
  flowableProcessDefinitionId?: string
  createdBy?: number
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface TemplateFormBinding {
  template: ProcessTemplate
  form: FormDefinition
}

export interface ProcessTemplatePayload {
  templateCode?: string
  templateName?: string
  bizTypeId?: number | null
  formId?: number | null
  sourceType?: string
  bpmnXml?: string
  nodeConfig?: string
  formBindConfig?: string
  createdBy?: number
}

export interface ProcessFragment {
  id: number
  fragmentCode: string
  fragmentName: string
  bizTypeId?: number | null
  description?: string
  fragmentType?: string
  status?: string
  bpmnXml?: string
  nodeConfig?: string
  createdBy?: number
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProcessFragmentPayload {
  fragmentCode?: string
  fragmentName?: string
  bizTypeId?: number | null
  description?: string
  fragmentType?: string
  bpmnXml?: string
  nodeConfig?: string
  createdBy?: number
}

export interface TemplateMarketItem {
  id: number
  sourceId: number
  type?: string
  title: string
  description?: string
  coverUrl?: string
  bizTypeId?: number | null
  publisherId?: number
  useCount?: number
  rating?: number
  tags?: string
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface MarketPublishPayload {
  templateId: number
  publisherId: number
  title: string
  description?: string
  coverUrl?: string
  tags?: string
}

export interface MarketCopyPayload {
  userId: number
  newTemplateName?: string
}
