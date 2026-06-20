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
  userId?: number
  newTemplateName?: string
}

export interface ProcessInstanceListParams {
  templateId?: number | null
  status?: string
  keyword?: string
}

export interface ProcessInstance {
  id: number
  templateId: number
  instanceCode: string
  instanceTitle: string
  status: string
  currentNodeKey?: string | null
  currentNodeName?: string | null
  currentBusinessType?: string | null
  flowableProcessInstanceId?: string | null
  flowableDefinitionId?: string | null
  flowableDeploymentId?: string | null
  createTime?: string
  updateTime?: string
}

export interface FormSubmission {
  id: number
  processInstanceId: number
  templateId: number
  nodeKey: string
  nodeName?: string | null
  businessType?: string | null
  formId: number
  formDataJson?: string | null
  status: string
  createTime?: string
  updateTime?: string
}

export interface StartProcessPreviewPayload {
  templateId: number
  instanceTitle: string
  startNodeKey: string
  startNodeName: string
  businessType: string
  formId: number
  formDataJson: string
  status?: 'draft' | 'submitted'
}

export interface SaveNodeFormPayload {
  processInstanceId: number
  templateId: number
  nodeKey: string
  nodeName: string
  businessType: string
  formId: number
  formDataJson: string
  status?: 'draft' | 'submitted'
}

export interface RuntimeState {
  businessInstanceId: number
  flowableProcessInstanceId: string
  currentTaskKey?: string
  currentTaskName?: string
  formId?: number | null
  completed?: boolean
}

/** 任务项 — 来自 TaskDTO */
export interface TaskItem {
  taskId: string
  taskName: string
  taskDefinitionKey: string
  processInstanceId: string
  businessInstanceId: number
  instanceCode: string
  instanceTitle: string
  assignee?: string | null
  createTime?: string
  dueDate?: string | null
  endTime?: string | null
  status: string
  formId?: number | null
  /** 节点业务类型：start / form_fill / approval / notify / condition / end */
  businessType?: string | null
  /** 审批方式：SINGLE（单人）、ALL（会签）、ANY（或签） */
  approvalMode?: string | null
  /** 多实例总数（会签/或签时有值） */
  nrOfInstances?: number | null
  /** 多实例已完成数 */
  nrOfCompletedInstances?: number | null
  /** 多实例进行中数 */
  nrOfActiveInstances?: number | null
  /** 所有审批人 ID（逗号分隔） */
  allAssignees?: string | null
  /** 审批进度描述 */
  approvalProgress?: string | null
}

/** 完成任务请求 */
export interface TaskCompletePayload {
  instanceId: number
  nodeKey: string
  formId?: number | null
  formData: Record<string, unknown>
}

export interface NotificationItem {
  id: number
  receiverId: number
  type: string
  title: string
  content?: string | null
  targetType?: string | null
  targetId?: number | null
  targetUrl?: string | null
  isRead: boolean
  readAt?: string | null
  createTime?: string
  updateTime?: string
}

export interface NotificationQuery {
  receiverId?: number
  type?: string
  isRead?: boolean
  keyword?: string
}
