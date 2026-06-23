<template>
  <div class="start-page">
    <header class="page-heading">
      <div>
        <h1>{{ templateDetail?.templateName || '发起业务流程' }}</h1>
        <p>填写申请信息并确认审批路径，提交后可在“我的申请”中查看进度。</p>
      </div>
      <el-tooltip content="刷新可发起流程" placement="bottom">
        <el-button circle :icon="Refresh" :loading="loading" aria-label="刷新可发起流程" @click="loadCatalog" />
      </el-tooltip>
    </header>

    <section class="process-overview">
      <div class="process-select-field">
        <label>申请事项</label>
        <el-select
          v-model="selectedTemplateId"
          filterable
          placeholder="请选择要办理的事项"
          :loading="loading"
          :disabled="Boolean(currentInstance)"
          @change="handleTemplateChange"
        >
          <el-option
            v-for="item in templates"
            :key="item.id"
            :label="item.templateName"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="overview-metric">
        <span>所属业务</span>
        <strong>{{ bizTypeName(templateDetail?.bizTypeId) }}</strong>
      </div>
      <div class="overview-metric">
        <span>流程版本</span>
        <strong>{{ templateDetail ? `v${templateDetail.version || 1}` : '-' }}</strong>
      </div>
      <div class="overview-metric">
        <span>审批节点</span>
        <strong>{{ templateDetail ? `${routePreview?.approvalSteps.length || 0} 个` : '-' }}</strong>
      </div>
      <div class="overview-metric">
        <span>当前状态</span>
        <strong class="available-status">{{ currentStatusText }}</strong>
      </div>
    </section>

    <el-alert
      v-if="message"
      class="page-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="message"
    />

    <el-empty v-if="!selectedTemplateId && !loading" description="请选择一个要办理的事项" />

    <div v-if="templateDetail" class="work-area">
      <section class="form-panel" v-loading="detailLoading || formLoading">
        <div class="panel-head">
          <div>
            <h2>申请信息</h2>
            <p>{{ currentForm ? `请填写“${currentForm.formName}”中的必要信息` : '当前流程暂未配置发起表单' }}</p>
          </div>
          <el-tag v-if="currentInstance" :type="instanceTagType" effect="plain">
            {{ instanceStatusLabel(currentInstance.status) }}
          </el-tag>
        </div>

        <div class="form-content">
          <div class="title-field">
            <label>申请标题 <span>*</span></label>
            <el-input
              v-model="instanceTitle"
              :disabled="isReadonly"
              placeholder="请输入便于识别的申请标题"
            />
          </div>

          <DynamicFormRenderer
            v-if="currentForm"
            ref="formRendererRef"
            v-model="formData"
            :form-schema="currentForm.formSchema"
            :field-list="currentForm.fieldList"
            :readonly="isReadonly"
          />

          <el-empty v-else-if="!formLoading" description="该事项暂未配置可填写表单" :image-size="110" />
        </div>
      </section>

      <aside class="route-panel">
        <div class="panel-head">
          <div>
            <h2>审批信息</h2>
            <p>提交前确认处理人与流转规则</p>
          </div>
        </div>

        <div class="route-tabs" role="tablist" aria-label="审批信息视图">
          <button :class="{ active: routeView === 'path' }" type="button" @click="routeView = 'path'">审批路径</button>
          <button :class="{ active: routeView === 'diagram' }" type="button" @click="routeView = 'diagram'">流程图</button>
        </div>

        <div v-if="routeView === 'path'" class="route-content">
          <div class="route-summary">
            <span>共 {{ routePreview?.approvalSteps.length || 0 }} 个审批节点</span>
            <strong>{{ routePreview ? '审批人已解析' : '正在解析' }}</strong>
          </div>

          <div class="timeline">
            <div class="timeline-step is-start">
              <div class="step-mark"><VideoPlay /></div>
              <div class="step-content">
                <h3>提交申请</h3>
                <p>核对申请信息并启动流程</p>
                <div class="people-row">
                  <span class="person-avatar">{{ initials(applicantName) }}</span>
                  <div><strong>{{ applicantName }}</strong><small>当前申请人</small></div>
                </div>
              </div>
            </div>

            <div
              v-for="(step, index) in routePreview?.approvalSteps || []"
              :key="step.nodeKey"
              class="timeline-step"
            >
              <div class="step-mark">{{ index + 1 }}</div>
              <div class="step-content">
                <div class="step-title-row">
                  <h3>{{ step.nodeName }}</h3>
                  <el-tag size="small" effect="plain">{{ approvalModeLabel(step.approvalMode) }}</el-tag>
                </div>
                <p>{{ strategyLabel(step.assignStrategy) }}</p>
                <div v-if="step.approvers.length" class="approver-list">
                  <div v-for="person in step.approvers" :key="person.userId" class="people-row">
                    <span class="person-avatar is-approver">{{ initials(person.userName) }}</span>
                    <div><strong>{{ person.userName }}</strong><small>审批人</small></div>
                  </div>
                </div>
                <div v-else class="unresolved-person">提交后根据流程规则确定处理人</div>
              </div>
            </div>

            <div class="timeline-step is-end">
              <div class="step-mark"><CircleCheck /></div>
              <div class="step-content"><h3>流程结束</h3><p>结果将通过通知中心告知申请人</p></div>
            </div>
          </div>

          <p class="route-note">审批人根据当前申请人的组织关系和流程角色实时解析，以提交时的组织数据为准。</p>
        </div>

        <div v-else class="diagram-content">
          <BpmnViewerPanel v-if="templateDetail.bpmnXml" :key="templateDetail.id" :bpmn-xml="templateDetail.bpmnXml" />
          <el-empty v-else description="当前流程暂无可预览的流程图" />
        </div>
      </aside>
    </div>

    <div v-if="templateDetail" class="submit-bar">
      <span><i>*</i> 为必填项，提交后将立即进入审批流程。</span>
      <div>
        <el-button :icon="DocumentChecked" :loading="saving" :disabled="isReadonly || !currentForm" @click="saveDraftInstance">
          保存草稿
        </el-button>
        <el-button type="success" :icon="Promotion" :loading="submitting" :disabled="isReadonly || !currentForm" @click="submitApplication">
          提交申请
        </el-button>
        <el-button v-if="isReadonly" type="primary" @click="router.push('/process/instances')">查看我的申请</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, DocumentChecked, Promotion, Refresh, VideoPlay } from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import BpmnViewerPanel from '@/components/ai/BpmnViewerPanel.vue'
import { getBizTypes } from '@/api/bizType'
import { getFormDetail } from '@/api/formDefinition'
import {
  createProcessInstanceDraft,
  getProcessInstanceDetail,
  getProcessInstanceSubmissions,
  saveNodeForm,
  submitProcessInstance
} from '@/api/processInstance'
import {
  getAvailableProcessDetail,
  getAvailableProcesses,
  getAvailableProcessRoutePreview
} from '@/api/processCatalog'
import { useAuthStore } from '@/stores/auth'
import type {
  BizType,
  FormDefinition,
  FormSubmission,
  ProcessInstance,
  ProcessRoutePreview,
  ProcessTemplate
} from '@/types/workflow'

type FormBindingMode = 'none' | 'template_default' | 'node_form'

interface PreviewNodeConfig {
  nodeId: string
  nodeName: string
  businessType: string
  formBindingMode: FormBindingMode
  formId?: number | null
  useTemplateFallback?: boolean
}

const FORM_PREVIEW_TYPES = ['start', 'form_fill']
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const detailLoading = ref(false)
const formLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const templates = ref<ProcessTemplate[]>([])
const bizTypes = ref<BizType[]>([])
const templateDetail = ref<ProcessTemplate | null>(null)
const routePreview = ref<ProcessRoutePreview | null>(null)
const selectedTemplateId = ref<number | null>(null)
const selectedNode = ref<PreviewNodeConfig | null>(null)
const currentForm = ref<FormDefinition | null>(null)
const currentInstance = ref<ProcessInstance | null>(null)
const submissions = ref<FormSubmission[]>([])
const instanceTitle = ref('')
const formData = ref<Record<string, unknown>>({})
const message = ref('')
const routeView = ref<'path' | 'diagram'>('path')
const formRendererRef = ref<InstanceType<typeof DynamicFormRenderer> | null>(null)

const isReadonly = computed(() => Boolean(currentInstance.value && currentInstance.value.status !== 'draft'))
const applicantName = computed(() => routePreview.value?.applicantName || authStore.username)
const currentStatusText = computed(() => currentInstance.value
  ? instanceStatusLabel(currentInstance.value.status)
  : templateDetail.value ? '可发起' : '-')
const instanceTagType = computed(() => currentInstance.value?.status === 'draft' ? 'warning' : 'success')

onMounted(async () => {
  await loadCatalog()
  if (route.query.instanceId) {
    await restoreInstance(Number(route.query.instanceId))
  } else if (route.query.templateId) {
    selectedTemplateId.value = Number(route.query.templateId)
    await handleTemplateChange(selectedTemplateId.value)
  }
})

watch(
  () => route.query.instanceId,
  async value => {
    if (value && Number(value) !== currentInstance.value?.id) await restoreInstance(Number(value))
  }
)

async function loadCatalog() {
  loading.value = true
  message.value = ''
  try {
    const [processList, bizTypeList] = await Promise.all([getAvailableProcesses(), getBizTypes()])
    templates.value = processList
    bizTypes.value = bizTypeList
  } catch (error) {
    message.value = normalizeError(error, '可发起流程加载失败')
  } finally {
    loading.value = false
  }
}

async function handleTemplateChange(id?: number) {
  resetProcessState()
  if (!id) return
  detailLoading.value = true
  try {
    templateDetail.value = await getAvailableProcessDetail(id)
    instanceTitle.value = `${templateDetail.value.templateName}-${formatDate(new Date())}`
    const nodes = parsePreviewNodes(templateDetail.value.nodeConfig)
    selectedNode.value = nodes[0] || null
    await Promise.all([loadRoutePreview(id), loadStartForm()])
  } catch (error) {
    message.value = normalizeError(error, '流程详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadRoutePreview(templateId: number) {
  try {
    routePreview.value = await getAvailableProcessRoutePreview(templateId)
  } catch (error) {
    routePreview.value = null
    message.value = normalizeError(error, '审批路径解析失败')
  }
}

async function loadStartForm() {
  if (!templateDetail.value) return
  const formId = resolveFormId(templateDetail.value, selectedNode.value)
  currentForm.value = null
  formData.value = {}
  if (!formId) {
    message.value = '该流程尚未配置发起表单，请联系流程负责人'
    return
  }
  formLoading.value = true
  try {
    currentForm.value = await getFormDetail(formId)
  } catch (error) {
    message.value = normalizeError(error, '发起表单加载失败')
  } finally {
    formLoading.value = false
  }
}

async function restoreInstance(instanceId: number) {
  if (!instanceId) return
  detailLoading.value = true
  try {
    const detail = await getProcessInstanceDetail(instanceId)
    selectedTemplateId.value = detail.templateId
    await handleTemplateChange(detail.templateId)
    currentInstance.value = detail
    instanceTitle.value = detail.instanceTitle
    submissions.value = await getProcessInstanceSubmissions(detail.id)
    const submission = submissions.value.find(item => item.nodeKey === selectedNode.value?.nodeId) || submissions.value[0]
    formData.value = parseFormData(submission?.formDataJson)
  } catch (error) {
    message.value = normalizeError(error, '申请草稿恢复失败')
  } finally {
    detailLoading.value = false
  }
}

async function saveDraftInstance(showSuccess = true): Promise<boolean> {
  if (isReadonly.value || !templateDetail.value || !selectedNode.value || !currentForm.value) return false
  if (!instanceTitle.value.trim()) {
    ElMessage.warning('请填写申请标题')
    return false
  }
  if (!(formRendererRef.value?.validate() ?? false)) {
    ElMessage.warning('请检查必填项或字段格式')
    return false
  }
  const formId = resolveFormId(templateDetail.value, selectedNode.value)
  if (!formId) return false

  saving.value = true
  try {
    if (!currentInstance.value) {
      currentInstance.value = await createProcessInstanceDraft({
        templateId: templateDetail.value.id,
        instanceTitle: instanceTitle.value.trim(),
        startNodeKey: selectedNode.value.nodeId,
        startNodeName: selectedNode.value.nodeName,
        businessType: selectedNode.value.businessType,
        formId,
        formDataJson: JSON.stringify(formData.value),
        status: 'draft'
      })
    } else {
      await saveNodeForm({
        processInstanceId: currentInstance.value.id,
        templateId: templateDetail.value.id,
        nodeKey: selectedNode.value.nodeId,
        nodeName: selectedNode.value.nodeName,
        businessType: selectedNode.value.businessType,
        formId,
        formDataJson: JSON.stringify(formData.value),
        status: 'draft'
      })
    }
    if (showSuccess) ElMessage.success('草稿已保存')
    return true
  } catch (error) {
    message.value = normalizeError(error, '草稿保存失败')
    return false
  } finally {
    saving.value = false
  }
}

async function submitApplication() {
  if (isReadonly.value) return
  const saved = await saveDraftInstance(false)
  if (!saved || !currentInstance.value) return
  submitting.value = true
  try {
    currentInstance.value = await submitProcessInstance(currentInstance.value.id)
    ElMessage.success('申请已提交并进入审批流程')
  } catch (error) {
    message.value = normalizeError(error, '申请提交失败')
  } finally {
    submitting.value = false
  }
}

function parsePreviewNodes(nodeConfig?: string): PreviewNodeConfig[] {
  if (!nodeConfig) return []
  try {
    const parsed = JSON.parse(nodeConfig)
    const entries: Array<[string, unknown]> = Array.isArray(parsed)
      ? parsed.map(item => [item?.nodeKey || item?.nodeId || '', item])
      : Object.entries(parsed?.nodes && typeof parsed.nodes === 'object' ? parsed.nodes : parsed)
    return entries
      .map(([nodeId, raw]) => normalizeNode(nodeId, raw))
      .filter((node): node is PreviewNodeConfig => Boolean(node && FORM_PREVIEW_TYPES.includes(node.businessType)))
  } catch {
    message.value = '流程节点配置无法解析，请联系流程负责人'
    return []
  }
}

function normalizeNode(nodeId: string, raw: unknown): PreviewNodeConfig | null {
  if (!raw || typeof raw !== 'object') return null
  const value = raw as Record<string, unknown>
  const businessType = String(value.businessType || value.nodeType || '').trim()
  if (!businessType) return null
  const rawFormId = value.formId === '' || value.formId == null ? null : Number(value.formId)
  return {
    nodeId: String(value.nodeId || value.nodeKey || nodeId),
    nodeName: String(value.nodeName || value.name || '提交申请'),
    businessType,
    formBindingMode: String(value.formBindingMode || (rawFormId ? 'node_form' : 'none')) as FormBindingMode,
    formId: rawFormId !== null && Number.isFinite(rawFormId) ? rawFormId : null,
    useTemplateFallback: Boolean(value.useTemplateFallback)
  }
}

function resolveFormId(template: ProcessTemplate, node: PreviewNodeConfig | null) {
  if (node?.formBindingMode === 'node_form' && node.formId) return node.formId
  if (node?.formBindingMode === 'template_default' || node?.useTemplateFallback) return node.formId || template.formId || null
  return node?.formId || template.formId || null
}

function resetProcessState() {
  templateDetail.value = null
  routePreview.value = null
  selectedNode.value = null
  currentForm.value = null
  currentInstance.value = null
  submissions.value = []
  instanceTitle.value = ''
  formData.value = {}
  message.value = ''
  routeView.value = 'path'
}

function bizTypeName(id?: number | null) {
  if (!id) return '-'
  return bizTypes.value.find(item => item.id === id)?.typeName || '未分类'
}

function strategyLabel(strategy?: string) {
  const labels: Record<string, string> = {
    DEPARTMENT_MANAGER: '申请人所在部门负责人审批',
    DIRECT_SUPERVISOR: '申请人的直属上级审批',
    SPECIFIC_USERS: '指定人员审批',
    SPECIFIED_DEPARTMENT_MANAGER: '指定部门负责人审批',
    ROLE_IN_APPLICANT_DEPT: '申请人部门内的流程角色审批',
    ROLE_IN_SPECIFIED_DEPT: '指定部门内的流程角色审批',
    GLOBAL_ROLE: '全局流程角色审批',
    ROLE: '流程角色审批'
  }
  return labels[(strategy || '').toUpperCase()] || '按流程规则分配审批人'
}

function approvalModeLabel(mode?: string) {
  const labels: Record<string, string> = { SINGLE: '单人审批', ALL: '会签', ANY: '或签' }
  return labels[(mode || 'SINGLE').toUpperCase()] || '审批'
}

function instanceStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    draft: '草稿', submitted: '已提交', running: '审批中', completed: '已完成', cancelled: '已取消'
  }
  return labels[(status || '').toLowerCase()] || status || '-'
}

function initials(name?: string) {
  return (name || '用户').trim().slice(0, 1)
}

function parseFormData(value?: string | null) {
  if (!value) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function normalizeError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.start-page { display: flex; flex-direction: column; gap: 18px; padding-bottom: 12px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.page-heading h1 { margin: 10px 0 6px; font-size: 28px; }
.page-heading p, .panel-head p { margin: 0; color: var(--muted); }

.process-overview {
  display: grid;
  grid-template-columns: minmax(280px, 1.5fr) repeat(4, minmax(110px, .55fr));
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, .96);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.process-select-field { padding: 18px 20px; }
.process-select-field label { display: block; margin-bottom: 8px; color: #53615c; font-size: 13px; font-weight: 700; }
.process-select-field .el-select { width: 100%; }
.overview-metric { min-width: 0; padding: 20px 16px; border-left: 1px solid var(--line); }
.overview-metric span { display: block; color: var(--muted); font-size: 12px; }
.overview-metric strong { display: block; margin-top: 9px; overflow-wrap: anywhere; font-size: 15px; }
.available-status { color: var(--brand); }
.page-alert { margin: 0; }

.work-area { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 18px; align-items: start; }
.form-panel, .route-panel, .submit-bar {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, .96);
  box-shadow: var(--shadow);
}
.route-panel { position: sticky; top: 18px; overflow: hidden; }
.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 20px 22px 16px; border-bottom: 1px solid var(--line); }
.panel-head h2 { margin: 0 0 6px; font-size: 18px; }
.panel-head p { font-size: 13px; }
.form-content { padding: 22px; }
.title-field { margin-bottom: 20px; }
.title-field label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 600; }
.title-field label span, .submit-bar i { color: var(--el-color-danger); font-style: normal; }

.route-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; margin: 16px 18px 0; padding: 4px; border-radius: 7px; background: #eef3f1; }
.route-tabs button { height: 34px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.route-tabs button.active { background: #fff; color: var(--text); box-shadow: 0 1px 5px rgba(35, 62, 53, .12); font-weight: 700; }
.route-content { padding: 18px 20px 20px; }
.route-summary { display: flex; justify-content: space-between; gap: 10px; padding: 11px 12px; border-radius: 6px; background: #fff6e8; color: #7a5722; font-size: 13px; }
.timeline { margin-top: 20px; }
.timeline-step { position: relative; display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: 12px; padding-bottom: 24px; }
.timeline-step:not(:last-child)::after { content: ''; position: absolute; left: 17px; top: 36px; bottom: 0; width: 2px; background: #dbe5e1; }
.step-mark { z-index: 1; display: grid; place-items: center; width: 36px; height: 36px; border: 2px solid #b4c5bf; border-radius: 50%; background: #fff; color: #64736e; font-size: 12px; font-weight: 700; }
.step-mark svg { width: 16px; height: 16px; }
.is-start .step-mark { border-color: var(--brand); background: #e9f6f1; color: var(--brand); }
.step-content { min-width: 0; }
.step-content h3 { margin: 2px 0 5px; font-size: 15px; }
.step-content p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.5; }
.step-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.people-row { display: flex; align-items: center; gap: 9px; margin-top: 10px; }
.person-avatar { display: grid; place-items: center; flex: 0 0 28px; width: 28px; height: 28px; border-radius: 50%; background: #e7f4ef; color: var(--brand); font-size: 12px; font-weight: 700; }
.person-avatar.is-approver { background: #e9f0fb; color: #3971bf; }
.people-row strong, .people-row small { display: block; }
.people-row strong { font-size: 13px; }
.people-row small { margin-top: 2px; color: var(--muted); font-size: 11px; }
.approver-list { display: grid; grid-template-columns: 1fr 1fr; gap: 0 8px; }
.unresolved-person { margin-top: 10px; color: #9a681d; font-size: 12px; }
.route-note { margin: 0; padding-top: 14px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; line-height: 1.6; }
.diagram-content { min-height: 460px; padding: 12px; }
.diagram-content :deep(.bpmn-canvas) { height: 440px; }

.submit-bar { position: sticky; z-index: 8; bottom: 12px; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; }
.submit-bar > span { color: var(--muted); font-size: 13px; }
.submit-bar > div { display: flex; gap: 10px; }

@media (max-width: 1180px) {
  .process-overview { grid-template-columns: 1fr 1fr 1fr; }
  .process-select-field { grid-column: 1 / -1; }
  .overview-metric { border-top: 1px solid var(--line); }
  .overview-metric:nth-of-type(2) { border-left: 0; }
  .work-area { grid-template-columns: minmax(0, 1fr) 340px; }
}

@media (max-width: 860px) {
  .page-heading { align-items: center; }
  .work-area { grid-template-columns: 1fr; }
  .route-panel { position: static; }
  .submit-bar { align-items: stretch; flex-direction: column; bottom: 6px; }
  .submit-bar > div { display: grid; grid-template-columns: 1fr 1fr; }
  .submit-bar .el-button { margin: 0; }
}

@media (max-width: 560px) {
  .page-heading h1 { font-size: 24px; }
  .process-overview { grid-template-columns: 1fr 1fr; }
  .overview-metric { border-left: 0; border-top: 1px solid var(--line); }
  .overview-metric:nth-child(even) { border-left: 1px solid var(--line); }
  .panel-head, .form-content { padding-left: 16px; padding-right: 16px; }
  .approver-list { grid-template-columns: 1fr; }
  .submit-bar > span { display: none; }
}
</style>
