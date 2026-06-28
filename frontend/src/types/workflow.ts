/**
 * 工作流相关类型定义。
 *
 * 涵盖：
 *  - 基础类型：ApiResponse, BizType
 *  - 表单：FormDefinition
 *  - 流程模板：ProcessTemplate, ProcessFragment
 *  - 流程实例：ProcessInstance, BusinessProcessInstance
 *  - 任务：TaskItem, TaskCompletePayload
 *  - 通知：NotificationItem, NotificationQuery
 *  - 模板市场：TemplateMarketItem
 *  - 审批路由：ProcessRoutePreview
 */

/** 通用 API 响应包装 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 业务类型（用于流程/表单分类） */
export interface BizType {
  id: number
  parentId?: number | null
  typeCode: string
  typeName: string
  description?: string
  sortOrder?: number
}

// ==================== 表单 ====================

/** 表单定义 */
export interface FormDefinition {
  id: number
  formCode: string
  formName: string
  bizTypeId?: number | null
  version?: number
  status?: string
  fieldList?: string            // JSON 格式的字段列表
  formSchema?: string           // JSON 格式的表单 Schema
  createdBy?: number | null
  sourceType?: string
  sourceFormId?: number | null
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

/** 创建/更新表单的请求体 */
export interface FormDefinitionPayload {
  formCode?: string
  formName: string
  bizTypeId?: number | null
  version?: number
  fieldList?: string
  formSchema?: string
}

// ==================== 流程模板 ====================

/** 流程模板 */
export interface ProcessTemplate {
  id: number
  templateCode: string
  templateName: string
  bizTypeId?: number | null
  formId?: number | null
  version?: number
  status?: string
  sourceType?: string
  resourceType?: 'system_template' | 'business_process'
  bpmnXml?: string                    // BPMN 2.0 XML 定义
  nodeConfig?: string                 // 节点配置 JSON
  formBindConfig?: string             // 表单绑定配置 JSON
  flowableDeploymentId?: string       // Flowable 部署 ID
  flowableProcessDefinitionId?: string// Flowable 流程定义 ID
  createdBy?: number
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

/** 模板与表单的绑定关系 */
export interface TemplateFormBinding {
  template: ProcessTemplate
  form: FormDefinition
}

/** 创建/更新流程模板的请求体 */
export interface ProcessTemplatePayload {
  templateCode?: string
  templateName?: string
  bizTypeId?: number | null
  formId?: number | null
  sourceType?: string
  resourceType?: 'system_template' | 'business_process'
  bpmnXml?: string
  nodeConfig?: string
  formBindConfig?: string
  createdBy?: number
}

// ==================== 审批路由预览 ====================

/** 审批人 */
export interface ProcessRouteApprover {
  userId: number
  userName: string
  departmentId?: number | null
}

/** 审批步骤 */
export interface ProcessRouteApprovalStep {
  nodeKey: string
  nodeName: string
  approvalMode: string       // 审批模式
  assignStrategy: string     // 分配策略
  approvers: ProcessRouteApprover[]
}

/** 流程审批路由预览 */
export interface ProcessRoutePreview {
  templateId: number
  applicantId: number
  applicantName: string
  approvalSteps: ProcessRouteApprovalStep[]
}

// ==================== 流程片段 ====================

/** 流程片段（可复用的子流程） */
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

/** 创建/更新流程片段的请求体 */
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

// ==================== 模板市场 ====================

/** 模板市场条目 */
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

/** 发布到模板市场的请求体 */
export interface MarketPublishPayload {
  templateId: number
  publisherId: number
  title: string
  description?: string
  coverUrl?: string
  tags?: string
}

/** 从市场复制模板的请求体 */
export interface MarketCopyPayload {
  userId?: number
  newTemplateName?: string
}

// ==================== 流程实例 ====================

/** 流程实例列表查询参数 */
export interface ProcessInstanceListParams {
  templateId?: number | null
  status?: string
  keyword?: string
}

/** 流程实例（发起者视角） */
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

/** 流程 BPMN 图 */
export interface ProcessDiagram {
  templateId: number
  templateName: string
  bpmnXml: string
}

/** 业务流程实例列表查询参数 */
export interface BusinessProcessInstanceListParams {
  templateId?: number | null
  status?: string
  keyword?: string
}

/** 业务流程实例（管理者视角，含更多字段） */
export interface BusinessProcessInstance {
  id: number
  instanceCode: string
  instanceTitle: string
  status: string
  anomaly?: boolean                 // 是否异常
  anomalyReason?: string | null     // 异常原因
  templateId: number
  templateCode?: string | null
  templateName?: string | null
  templateVersion?: number | null
  templateStatus?: string | null
  processOwnerId?: number | null
  processOwnerName?: string | null
  applicantId: number
  applicantUsername?: string | null
  applicantName: string
  applicantDepartmentId?: number | null
  bizTypeId?: number | null
  formId?: number | null
  currentNodeKey?: string | null
  currentNodeName?: string | null
  currentBusinessType?: string | null
  flowableProcessInstanceId?: string | null
  startedAt?: string | null
  endedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

// ==================== 流程时间线 ====================

/** 流程时间线节点 */
export interface ProcessTimelineNode {
  type: 'start' | 'approval' | 'end' | string
  nodeName: string
  operatorName?: string | null
  time?: string | null
  duration?: string | null
  action?: string | null         // 审批动作（同意/驳回等）
  comment?: string | null        // 审批意见
}

/** 流程时间线 */
export interface ProcessTimeline {
  nodes: ProcessTimelineNode[]
}

// ==================== 表单提交 ====================

/** 节点上的表单提交记录 */
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

/** 发起流程的请求体 */
export interface StartProcessPreviewPayload {
  templateId: number
  instanceTitle: string
  startNodeKey: string
  startNodeName: string
  businessType: string
  formId: number
  formDataJson: string
  status?: 'draft' | 'submitted'  // 草稿 或 提交
}

/** 保存节点表单的请求体 */
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

// ==================== 运行时状态 ====================

/** 流程运行时状态 */
export interface RuntimeState {
  businessInstanceId: number
  flowableProcessInstanceId: string
  currentTaskKey?: string
  currentTaskName?: string
  formId?: number | null
  completed?: boolean
}

// ==================== 任务 ====================

/**
 * 任务项（来自 TaskDTO）。
 *
 * 支持会签/或签多实例：
 *  - nrOfInstances / nrOfCompletedInstances / nrOfActiveInstances
 *  - allAssignees 列出所有审批人
 */
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
  dueDate?: string | null         // 截止时间
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

/** 完成任务请求体 */
export interface TaskCompletePayload {
  instanceId: number
  nodeKey: string
  formId?: number | null
  formData: Record<string, unknown>  // 审批表单数据
}

// ==================== 通知 ====================

/** 通知条目 */
export interface NotificationItem {
  id: number
  receiverId: number
  type: string             // 通知类型：task_remind / timeout_warning / approval_result 等
  title: string
  content?: string | null
  targetType?: string | null   // 跳转目标类型（如 flowable_task:xxx）
  targetId?: number | null
  targetUrl?: string | null    // 跳转目标 URL
  isRead: boolean
  readAt?: string | null
  createTime?: string
  updateTime?: string
}

/** 通知查询参数 */
export interface NotificationQuery {
  receiverId?: number
  type?: string
  isRead?: boolean
  keyword?: string
}
