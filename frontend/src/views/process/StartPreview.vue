<template>
  <div class="start-preview-page">
    <section class="preview-head">
      <div>
        <el-tag type="success" effect="plain">运行时预览</el-tag>
        <h1>节点表单运行预览</h1>
        <p>选择流程模板和节点后，按“节点表单优先、模板默认表单兜底”的规则动态加载表单，并保存为轻量级流程实例草稿。</p>
      </div>
      <el-button round :icon="Refresh" :loading="loading" @click="loadTemplates">刷新模板</el-button>
    </section>

    <section class="preview-panel">
      <div class="panel-title-row">
        <div>
          <h2>选择预览范围</h2>
          <p>当前页面提交后会启动 Flowable 流程实例，任务处理与待办中心将在后续版本开发。</p>
        </div>
        <el-tag effect="plain">ProcessTemplate.nodeConfig</el-tag>
      </div>

      <el-form label-position="top">
        <el-form-item label="流程模板">
          <el-select
            v-model="selectedTemplateId"
            filterable
            clearable
            placeholder="请选择流程模板"
            :loading="loading"
            style="width: 100%"
            @change="handleTemplateChange"
          >
            <el-option
              v-for="item in templates"
              :key="item.id"
              :label="`${item.templateName} (${templateStatusLabel(item.status)})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="templateDetail" label="流程标题">
          <el-input v-model="instanceTitle" placeholder="请输入流程标题" :disabled="isSubmitted" />
        </el-form-item>

        <el-form-item v-if="templateDetail" label="预览节点">
          <el-select
            v-model="selectedNodeId"
            filterable
            clearable
            placeholder="请选择支持表单预览的节点"
            style="width: 100%"
            @change="handleNodeChange"
          >
            <el-option
              v-for="node in previewNodes"
              :key="node.nodeId"
              :label="`${node.nodeName} / ${businessTypeLabel(node.businessType)} / ${formBindingModeLabel(node.formBindingMode)}`"
              :value="node.nodeId"
            />
          </el-select>
          <div v-if="previewNodes.length === 0" class="form-empty-hint">当前流程暂无可预览表单节点。</div>
        </el-form-item>
      </el-form>

      <el-alert
        class="stage-alert"
        type="info"
        :closable="false"
        show-icon
        title="运行时规则：节点绑定表单优先；否则在允许回退时使用模板默认表单；都没有则当前节点无表单。"
      />

      <el-alert
        v-if="message"
        class="stage-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="message"
      />

      <el-empty v-if="!selectedTemplateId" description="请选择一个流程模板开始预览" />

      <template v-if="templateDetail">
        <div class="info-grid">
          <div class="info-card">
            <span>模板名称</span>
            <strong>{{ templateDetail.templateName }}</strong>
          </div>
          <div class="info-card">
            <span>模板编码</span>
            <strong>{{ templateDetail.templateCode }}</strong>
          </div>
          <div class="info-card">
            <span>模板状态</span>
            <strong>{{ templateStatusLabel(templateDetail.status) }}</strong>
          </div>
          <div class="info-card">
            <span>模板默认表单</span>
            <strong>{{ templateDetail.formId || '未绑定' }}</strong>
          </div>
        </div>

        <div v-if="selectedNode" class="info-grid node-grid">
          <div class="info-card">
            <span>当前节点</span>
            <strong>{{ selectedNode.nodeName }}</strong>
          </div>
          <div class="info-card">
            <span>节点ID</span>
            <strong>{{ selectedNode.nodeId }}</strong>
          </div>
          <div class="info-card">
            <span>业务类型</span>
            <strong>{{ businessTypeLabel(selectedNode.businessType) }}</strong>
          </div>
          <div class="info-card">
            <span>表单来源</span>
            <strong>{{ resolvedSource.sourceText }}</strong>
          </div>
        </div>

        <div v-if="currentInstance" class="info-grid instance-grid">
          <div class="info-card">
            <span>实例ID</span>
            <strong>{{ currentInstance.id }}</strong>
          </div>
          <div class="info-card">
            <span>实例标题</span>
            <strong>{{ currentInstance.instanceTitle }}</strong>
          </div>
          <div class="info-card">
            <span>实例状态</span>
            <strong>{{ instanceStatusLabel(currentInstance.status) }}</strong>
          </div>
          <div class="info-card">
            <span>当前节点</span>
            <strong>{{ currentInstance.currentNodeName || currentInstance.currentNodeKey || '-' }}</strong>
          </div>
          <div class="info-card">
            <span>Flowable实例ID</span>
            <strong>{{ currentInstance.flowableProcessInstanceId || '未启动' }}</strong>
          </div>
        </div>
      </template>

      <el-alert
        v-if="isSubmitted"
        class="stage-alert"
        type="success"
        :closable="false"
        show-icon
        title="当前实例已提交或已启动 Flowable 流程实例，暂不可继续编辑。"
      />

      <section v-if="currentForm" class="form-preview-block">
        <div class="block-head">
          <div>
            <h2>{{ currentForm.formName }}</h2>
            <p>{{ currentForm.formCode }} / {{ formStatusLabel(currentForm.status) }}</p>
          </div>
          <el-tag :type="resolvedSource.source === 'node_form' ? 'success' : 'info'" effect="plain">
            {{ resolvedSource.sourceText }}
          </el-tag>
        </div>
        <DynamicFormRenderer
          ref="formRendererRef"
          v-model="formData"
          :form-schema="currentForm.formSchema"
          :field-list="currentForm.fieldList"
          :readonly="isSubmitted"
        />
        <div class="preview-actions">
          <el-button round type="success" :icon="View" @click="previewData">预览表单数据</el-button>
          <el-button round :icon="CircleCheck" @click="validateForm">校验表单</el-button>
          <el-button round type="primary" :icon="DocumentChecked" :loading="saving" :disabled="isSubmitted" @click="saveDraftInstance">保存为草稿</el-button>
          <el-button round type="warning" :icon="Upload" :loading="submitting" :disabled="!currentInstance || isSubmitted" @click="submitPreviewInstance">提交并启动流程</el-button>
        </div>
      </section>
    </section>

    <section v-if="submissions.length > 0" class="json-panel">
      <div class="json-title">
        <h2>表单提交记录</h2>
        <el-tag effect="plain">{{ submissions.length }} 条</el-tag>
      </div>
      <el-table :data="submissions" size="small" border>
        <el-table-column prop="nodeName" label="节点名称" min-width="150" />
        <el-table-column prop="nodeKey" label="节点ID" min-width="150" />
        <el-table-column label="业务类型" min-width="120">
          <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="formId" label="表单ID" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ instanceStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" @click="showSubmissionJson(row)">查看JSON</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="jsonVisible" class="json-panel">
      <div class="json-title">
        <h2>formData JSON</h2>
        <el-button text type="success" @click="jsonVisible = false">收起</el-button>
      </div>
      <pre>{{ formattedData }}</pre>
    </section>

    <el-dialog v-model="submissionJsonVisible" title="提交记录 JSON" width="640px">
      <pre>{{ selectedSubmissionJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, DocumentChecked, Refresh, Upload, View } from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { getFormDetail } from '@/api/formDefinition'
import {
  createProcessInstanceDraft,
  getProcessInstanceDetail,
  getProcessInstanceSubmissions,
  saveNodeForm,
  submitProcessInstance
} from '@/api/processInstance'
import { getProcessTemplateDetail, getProcessTemplates } from '@/api/processTemplate'
import type { FormDefinition, FormSubmission, ProcessInstance, ProcessTemplate } from '@/types/workflow'

type FormBindingMode = 'none' | 'template_default' | 'node_form'
type NodeFormSource = 'none' | 'template_default' | 'node_form'

interface PreviewNodeConfig {
  nodeId: string
  nodeName: string
  bpmnType?: string
  businessType: string
  formBindingMode: FormBindingMode
  formId?: number | null
  useTemplateFallback?: boolean
  formMode?: string
}

interface ResolvedNodeForm {
  formId: number | null
  source: NodeFormSource
  sourceText: string
}

const FORM_PREVIEW_TYPES = ['start', 'form_fill']
const route = useRoute()

const loading = ref(false)
const detailLoading = ref(false)
const formLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const templates = ref<ProcessTemplate[]>([])
const templateDetail = ref<ProcessTemplate | null>(null)
const previewNodes = ref<PreviewNodeConfig[]>([])
const selectedTemplateId = ref<number | null>(null)
const selectedNodeId = ref<string>('')
const selectedNode = ref<PreviewNodeConfig | null>(null)
const currentForm = ref<FormDefinition | null>(null)
const currentInstance = ref<ProcessInstance | null>(null)
const submissions = ref<FormSubmission[]>([])
const instanceTitle = ref('')
const resolvedSource = ref<ResolvedNodeForm>({ formId: null, source: 'none', sourceText: '当前节点无表单' })
const message = ref('')
const formData = ref<Record<string, unknown>>({})
const jsonVisible = ref(false)
const submissionJsonVisible = ref(false)
const selectedSubmissionJson = ref('')
const formRendererRef = ref<InstanceType<typeof DynamicFormRenderer> | null>(null)

const formattedData = computed(() => JSON.stringify(formData.value, null, 2))
const isSubmitted = computed(() => ['submitted', 'running'].includes(currentInstance.value?.status || ''))
const submissionMap = computed(() => {
  const map = new Map<string, FormSubmission>()
  submissions.value.forEach((item) => {
    if (item.nodeKey) {
      map.set(item.nodeKey, item)
    }
  })
  return map
})

onMounted(async () => {
  await loadTemplates()
  await selectTemplateFromQuery()
  await restoreInstanceFromQuery()
})

watch(
  () => route.query.instanceId,
  async (newValue, oldValue) => {
    if (newValue && newValue !== oldValue) {
      await restoreInstanceFromQuery()
    }
  }
)

async function loadTemplates() {
  loading.value = true
  message.value = ''
  try {
    templates.value = await getProcessTemplates()
  } catch (error) {
    message.value = normalizeError(error, '流程模板加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}
async function restoreInstanceFromQuery() {
  const instanceId = Number(route.query.instanceId)
  if (!instanceId) return

  try {
    const detail = await getProcessInstanceDetail(instanceId)
    currentInstance.value = detail
    selectedTemplateId.value = detail.templateId
    instanceTitle.value = detail.instanceTitle

    await handleTemplateChange(detail.templateId)
    currentInstance.value = detail
    instanceTitle.value = detail.instanceTitle
    await refreshSubmissions()

    const targetNodeKey = findRestoreNodeKey(detail)
    if (targetNodeKey) {
      selectedNodeId.value = targetNodeKey
      await handleNodeChange(targetNodeKey)
    } else if (previewNodes.value.length === 0) {
      message.value = '当前流程模板暂无可编辑表单节点。'
    }

    message.value = detail.status === 'submitted'
      ? '当前实例已提交，仅支持查看，不支持编辑。'
      : '已恢复草稿实例，可继续编辑当前节点表单。'
  } catch (error) {
    message.value = normalizeError(error, '流程实例详情加载失败。')
  }
}

async function selectTemplateFromQuery() {
  const templateId = Number(route.query.templateId)
  if (!templateId || route.query.instanceId) return
  selectedTemplateId.value = templateId
  await handleTemplateChange(templateId)
}

async function handleTemplateChange(id?: number) {
  resetPreviewState()
  if (!id) return

  detailLoading.value = true
  try {
    const detail = await getProcessTemplateDetail(id)
    templateDetail.value = detail
    instanceTitle.value = `${detail.templateName}-${formatDate(new Date())}`
    previewNodes.value = parsePreviewNodes(detail.nodeConfig)
    // 如果模板有默认表单，将 formBindingMode=none 的节点升级为 template_default
    if (detail.formId) {
      previewNodes.value = previewNodes.value.map(node => {
        if (node.formBindingMode === 'none') {
          return { ...node, formBindingMode: 'template_default' as FormBindingMode, formId: detail.formId ?? node.formId }
        }
        return node
      })
    }
    if (previewNodes.value.length === 0) {
      message.value = detail.nodeConfig ? '当前流程暂无可预览表单节点。' : '当前流程模板暂无节点配置。'
    }
    await loadTemplateDefaultForm(detail)
  } catch (error) {
    message.value = normalizeError(error, '流程模板详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

async function handleNodeChange(nodeId?: string) {
  currentForm.value = null
  selectedNode.value = null
  resolvedSource.value = { formId: null, source: 'none', sourceText: '当前节点无表单' }
  formData.value = {}
  jsonVisible.value = false
  message.value = ''

  if (!nodeId || !templateDetail.value) {
    if (templateDetail.value) await loadTemplateDefaultForm(templateDetail.value)
    return
  }

  const node = previewNodes.value.find((item) => item.nodeId === nodeId) || null
  selectedNode.value = node
  if (!node) return

  const resolved = resolveNodeForm(templateDetail.value, node)
  resolvedSource.value = resolved
  await loadResolvedForm(resolved)
  applySubmissionDataForNode(node.nodeId)
}

async function loadTemplateDefaultForm(template: ProcessTemplate) {
  selectedNode.value = null
  selectedNodeId.value = ''
  const resolved: ResolvedNodeForm = template.formId
    ? { formId: template.formId, source: 'template_default', sourceText: '模板默认表单' }
    : { formId: null, source: 'none', sourceText: '当前节点无表单' }
  resolvedSource.value = resolved
  await loadResolvedForm(resolved)
}

async function loadResolvedForm(resolved: ResolvedNodeForm) {
  currentForm.value = null
  formData.value = {}
  jsonVisible.value = false
  if (!resolved.formId) {
    message.value = '当前节点无表单。'
    return
  }

  formLoading.value = true
  try {
    currentForm.value = await getFormDetail(resolved.formId)
  } catch (error) {
    message.value = normalizeError(error, '表单不存在或已被删除。')
  } finally {
    formLoading.value = false
  }
}

async function saveDraftInstance() {
  if (isSubmitted.value) {
    ElMessage.warning('当前实例已提交或已启动流程，不允许继续编辑。')
    return
  }
  if (!templateDetail.value) {
    ElMessage.warning('请先选择流程模板。')
    return
  }
  // 如果没有选中节点但有模板默认表单，使用 start 节点兜底
  if (!selectedNode.value) {
    const firstNode = previewNodes.value[0]
    if (firstNode && resolvedSource.value.formId) {
      selectedNode.value = firstNode
      selectedNodeId.value = firstNode.nodeId
    } else {
      ElMessage.warning('请先选择预览节点。（若无可选节点，请确认模板已发布且包含 start 或 form_fill 节点）')
      return
    }
  }
  if (!resolvedSource.value.formId && !templateDetail.value.formId) {
    ElMessage.warning('当前节点无表单且模板无默认表单，无法保存。请先在流程设计器中绑定表单。')
    return
  }
  if (!validateBeforeSave()) return

  saving.value = true
  try {
    const payloadBase = {
      templateId: templateDetail.value.id,
      nodeKey: selectedNode.value.nodeId,
      nodeName: selectedNode.value.nodeName,
      businessType: selectedNode.value.businessType,
      formId: resolvedSource.value.formId,
      formDataJson: JSON.stringify(formData.value),
      status: 'draft' as const
    }

    // 确保 formId 不为 null（类型守卫已在函数开头检查）
    const formId: number = payloadBase.formId ?? templateDetail.value.formId ?? 0

    if (!currentInstance.value) {
      currentInstance.value = await createProcessInstanceDraft({
        templateId: payloadBase.templateId,
        instanceTitle: instanceTitle.value || `${templateDetail.value.templateName}-${formatDate(new Date())}`,
        startNodeKey: payloadBase.nodeKey,
        startNodeName: payloadBase.nodeName,
        businessType: payloadBase.businessType,
        formId,
        formDataJson: payloadBase.formDataJson,
        status: 'draft'
      })
    } else {
      await saveNodeForm({
        processInstanceId: currentInstance.value.id,
        templateId: payloadBase.templateId,
        nodeKey: payloadBase.nodeKey,
        nodeName: payloadBase.nodeName,
        businessType: payloadBase.businessType,
        formId,
        formDataJson: payloadBase.formDataJson,
        status: 'draft' as const
      })
    }

    await refreshSubmissions()
    await refreshCurrentInstance()
    applySubmissionDataForNode(selectedNode.value.nodeId)
    ElMessage.success('草稿保存成功')
  } catch (error) {
    message.value = normalizeError(error, '保存草稿失败。')
  } finally {
    saving.value = false
  }
}

async function submitPreviewInstance() {
  if (!currentInstance.value) {
    ElMessage.warning('请先保存为草稿。')
    return
  }
  if (isSubmitted.value) {
    ElMessage.warning('当前实例已提交或已启动流程，不能重复提交。')
    return
  }
  submitting.value = true
  try {
    currentInstance.value = await submitProcessInstance(currentInstance.value.id)
    await refreshSubmissions()
    await refreshCurrentInstance()
    applySubmissionDataForNode(selectedNode.value?.nodeId)
    ElMessage.success('提交成功，已启动 Flowable 流程实例。')
  } catch (error) {
    message.value = normalizeError(error, '提交预览实例失败。')
  } finally {
    submitting.value = false
  }
}

async function refreshSubmissions() {
  if (!currentInstance.value) return
  try {
    submissions.value = await getProcessInstanceSubmissions(currentInstance.value.id)
  } catch (error) {
    message.value = normalizeError(error, '表单提交记录加载失败。')
  }
}

async function refreshCurrentInstance() {
  if (!currentInstance.value) return
  try {
    currentInstance.value = await getProcessInstanceDetail(currentInstance.value.id)
  } catch (error) {
    message.value = normalizeError(error, '流程实例详情加载失败。')
  }
}

function validateBeforeSave() {
  const passed = formRendererRef.value?.validate() ?? false
  if (!passed) {
    ElMessage.warning('请检查必填项或字段格式。')
  }
  return passed
}

function parsePreviewNodes(nodeConfig?: string): PreviewNodeConfig[] {
  if (!nodeConfig) return []
  try {
    const parsed = JSON.parse(nodeConfig)
    // 兼容两种格式：
    //   Map 格式: { "NodeId": {...}, ... }
    //   Array 格式: [{ "nodeKey": "...", ... }, ...]
    let entries: Array<[string, unknown]> = []

    if (Array.isArray(parsed)) {
      // Array 格式：以 nodeKey/nodeId 作为 key
      entries = parsed.map((item: any) => [item?.nodeKey || item?.nodeId || '', item])
    } else if (parsed && typeof parsed === 'object') {
      const source = parsed?.nodes && typeof parsed.nodes === 'object' ? parsed.nodes : parsed
      if (source && typeof source === 'object' && !Array.isArray(source)) {
        entries = Object.entries(source)
      }
    }

    return entries
      .map(([nodeId, raw]) => normalizeNodeConfig(nodeId, raw))
      .filter((node): node is PreviewNodeConfig => Boolean(node && FORM_PREVIEW_TYPES.includes(node.businessType)))
  } catch {
    message.value = '节点配置解析失败，无法进行节点表单预览。'
    return []
  }
}

function normalizeNodeConfig(nodeId: string, raw: unknown): PreviewNodeConfig | null {
  if (!raw || typeof raw !== 'object') return null
  const value = raw as Record<string, unknown>
  const businessType = String(value.businessType || value.nodeType || '').trim()
  if (!businessType) return null
  const formIdValue = value.formId === '' || value.formId === undefined || value.formId === null ? null : Number(value.formId)
  const formBindingMode = String(value.formBindingMode || (formIdValue ? 'node_form' : 'none')) as FormBindingMode
  return {
    nodeId: String(value.nodeId || nodeId),
    nodeName: String(value.nodeName || value.name || nodeId),
    bpmnType: String(value.bpmnType || ''),
    businessType,
    formBindingMode,
    formId: formIdValue !== null && Number.isFinite(formIdValue) ? formIdValue : null,
    useTemplateFallback: Boolean(value.useTemplateFallback),
    formMode: String(value.formMode || '')
  }
}

function resolveNodeForm(template: ProcessTemplate, node: PreviewNodeConfig): ResolvedNodeForm {
  if (node.formBindingMode === 'node_form' && node.formId) {
    return {
      formId: node.formId,
      source: 'node_form',
      sourceText: '节点绑定表单'
    }
  }
  // template_default：使用模板默认表单（或节点已升级到 template_default 时用模板 formId）
  if ((node.formBindingMode === 'template_default' || node.useTemplateFallback) && (node.formId || template.formId)) {
    return {
      formId: (node.formId || template.formId)!,
      source: 'template_default',
      sourceText: '模板默认表单'
    }
  }

  if (node.formBindingMode === 'template_default' || node.useTemplateFallback) {
    if (template.formId) {
      return {
        formId: template.formId,
        source: 'template_default',
        sourceText: '模板默认表单'
      }
    }
  }

  return {
    formId: null,
    source: 'none',
    sourceText: '当前节点无表单'
  }
}

function resetPreviewState() {
  templateDetail.value = null
  previewNodes.value = []
  selectedNodeId.value = ''
  selectedNode.value = null
  currentForm.value = null
  currentInstance.value = null
  submissions.value = []
  instanceTitle.value = ''
  resolvedSource.value = { formId: null, source: 'none', sourceText: '当前节点无表单' }
  message.value = ''
  formData.value = {}
  jsonVisible.value = false
  submissionJsonVisible.value = false
}

function previewData() {
  jsonVisible.value = true
}

function validateForm() {
  if (validateBeforeSave()) {
    ElMessage.success('表单校验通过')
  }
}

function parseFormDataJson(value: string) {
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    ElMessage.warning('表单数据解析失败，已使用空表单。')
    return {}
  }
}
function findRestoreNodeKey(instance: ProcessInstance) {
  if (instance.currentNodeKey && previewNodes.value.some((node) => node.nodeId === instance.currentNodeKey)) {
    return instance.currentNodeKey
  }

  const firstSavedNode = submissions.value.find((item) =>
    previewNodes.value.some((node) => node.nodeId === item.nodeKey)
  )
  if (firstSavedNode?.nodeKey) {
    return firstSavedNode.nodeKey
  }

  return previewNodes.value[0]?.nodeId || ''
}

function applySubmissionDataForNode(nodeId?: string) {
  if (!nodeId) {
    formData.value = {}
    return
  }

  const submission = submissionMap.value.get(nodeId)
  if (!submission?.formDataJson) {
    formData.value = {}
    return
  }

  formData.value = parseFormDataJson(submission.formDataJson)
}
function showSubmissionJson(row: FormSubmission) {
  selectedSubmissionJson.value = formatJsonText(row.formDataJson)
  submissionJsonVisible.value = true
}

function formatJsonText(value?: string | null) {
  if (!value) return '{}'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatDate(date: Date) {
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

function normalizeStatus(status?: string) {
  return (status || '').toLowerCase()
}

function templateStatusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    reviewing: '审核中',
    published: '已发布',
    disabled: '已停用'
  }
  return map[normalizeStatus(status)] || status || '-'
}

function formStatusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    published: '已发布',
    disabled: '已停用'
  }
  return map[normalizeStatus(status)] || status || '-'
}

function instanceStatusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    submitted: '已提交，待启动流程引擎',
    running: '流程运行中',
    completed: '已完成',
    cancelled: '已取消'
  }
  return map[normalizeStatus(status)] || status || '-'
}

function businessTypeLabel(type?: string) {
  const map: Record<string, string> = {
    start: '开始/发起',
    form_fill: '表单填写',
    approval: '审批处理',
    generic_task: '人工任务'
  }
  return map[type || ''] || type || '-'
}

function formBindingModeLabel(mode?: string) {
  const map: Record<string, string> = {
    none: '不使用表单',
    template_default: '模板默认表单',
    node_form: '节点绑定表单'
  }
  return map[mode || ''] || mode || '-'
}

function normalizeError(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  return fallback
}
</script>

<style scoped>
.start-preview-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.preview-head,
.preview-panel,
.json-panel {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 26px;
  border-radius: 24px;
}

.preview-head h1 {
  margin: 10px 0 6px;
  font-size: 30px;
}

.preview-head p,
.panel-title-row p,
.block-head p,
.form-empty-hint {
  margin: 0;
  color: var(--muted);
}

.preview-panel,
.json-panel {
  padding: 22px;
  border-radius: 22px;
}

.panel-title-row,
.block-head,
.preview-actions,
.json-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title-row {
  margin-bottom: 18px;
}

.panel-title-row h2,
.block-head h2,
.json-title h2 {
  margin: 0;
  font-size: 18px;
}

.stage-alert {
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 22px;
}

.node-grid,
.instance-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.info-card {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.info-card span {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 13px;
}

.info-card strong {
  display: block;
  overflow-wrap: anywhere;
}

.form-preview-block {
  padding-top: 4px;
}

.block-head {
  margin-bottom: 14px;
}

.preview-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 8px;
}

pre {
  margin: 16px 0 0;
  padding: 16px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}

@media (max-width: 1100px) {
  .info-grid,
  .node-grid,
  .instance-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .preview-head,
  .panel-title-row,
  .block-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .info-grid,
  .node-grid,
  .instance-grid {
    grid-template-columns: 1fr;
  }
}
</style>
