<template>
  <div class="detail-page">
    <section class="page-head">
      <div class="head-copy">
        <div class="title-line">
          <h1>{{ instance?.instanceTitle || '申请详情' }}</h1>
          <el-tag :type="statusTagType" effect="light" size="large">{{ statusLabel(instance?.status) }}</el-tag>
        </div>
        <p>{{ workflowName }}<span v-if="instance?.instanceCode"> · {{ instance.instanceCode }}</span></p>
      </div>
      <div class="head-actions">
        <el-button :icon="Back" @click="router.back()">返回</el-button>
        <el-button v-if="canUrge" type="warning" :icon="Bell" :loading="urgeLoading" @click="urgeCurrentTask">催办</el-button>
        <el-button type="primary" :icon="View" :loading="diagramLoading" @click="viewBpmn">查看流程图</el-button>
        <el-button v-if="instance?.status === 'draft'" type="success" :icon="EditPen" @click="continueEdit">继续填写</el-button>
      </div>
    </section>

    <div v-if="message" class="message-bar">{{ message }}</div>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="申请概览" name="info">
        <section class="overview-panel" v-loading="loading">
          <div class="progress-summary">
            <div class="progress-mark" :class="instance?.status || 'draft'">
              <el-icon><CircleCheckFilled v-if="instance?.status === 'completed'" /><Clock v-else /></el-icon>
            </div>
            <div>
              <span class="summary-label">{{ instance?.status === 'completed' ? '处理结果' : '当前进度' }}</span>
              <strong>{{ currentStep }}</strong>
              <p>{{ statusDescription }}</p>
            </div>
          </div>

          <dl class="summary-grid">
            <div>
              <dt>所属流程</dt>
              <dd>{{ workflowName }}</dd>
            </div>
            <div>
              <dt>提交时间</dt>
              <dd>{{ formatDate(instance?.createTime) }}</dd>
            </div>
            <div>
              <dt>{{ instance?.status === 'completed' ? '完成时间' : '最近更新' }}</dt>
              <dd>{{ formatDate(instance?.updateTime) }}</dd>
            </div>
            <div>
              <dt>处理耗时</dt>
              <dd>{{ elapsedText }}</dd>
            </div>
          </dl>
        </section>
      </el-tab-pane>

      <el-tab-pane label="处理记录" name="timeline">
        <section class="timeline-panel" v-loading="timelineLoading">
          <el-empty v-if="timelineNodes.length === 0" description="暂无处理记录" />
          <div v-else class="timeline">
            <div v-for="(node, idx) in timelineNodes" :key="`${node.nodeName}-${idx}`" class="tl-item">
              <div class="tl-dot" :class="node.type">
                <el-icon v-if="node.type === 'end'"><CircleCheckFilled /></el-icon>
                <el-icon v-else-if="node.type === 'start'"><VideoPlay /></el-icon>
                <el-icon v-else><Clock /></el-icon>
              </div>
              <div v-if="idx < timelineNodes.length - 1" class="tl-line" />
              <div class="tl-content">
                <div class="tl-head">
                  <strong>{{ node.nodeName }}</strong>
                  <el-tag :type="tagType(node)" size="small" effect="plain">{{ node.action }}</el-tag>
                  <span v-if="node.duration" class="tl-duration">耗时 {{ node.duration }}</span>
                </div>
                <div class="tl-meta">
                  <span>{{ node.operatorName || '系统' }}</span>
                  <time>{{ formatDate(node.time) }}</time>
                </div>
                <p v-if="node.comment" class="tl-comment">{{ node.comment }}</p>
              </div>
            </div>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane :label="`申请材料 ${submissions.length || ''}`" name="submissions">
        <section class="submission-panel" v-loading="submissionsLoading">
          <el-empty v-if="submissions.length === 0" description="暂无申请材料" />
          <div v-else class="submission-list">
            <article v-for="row in submissions" :key="row.id" class="submission-row">
              <div class="submission-icon"><el-icon><Document /></el-icon></div>
              <div class="submission-main">
                <strong>{{ row.nodeName || businessTypeLabel(row.businessType) }}</strong>
                <span>{{ businessTypeLabel(row.businessType) }} · {{ formatDate(row.updateTime || row.createTime) }}</span>
              </div>
              <el-tag :type="row.status === 'rejected' ? 'danger' : 'success'" effect="plain">
                {{ submissionStatusLabel(row.status) }}
              </el-tag>
              <el-button text type="primary" @click="showData(row)">查看内容</el-button>
            </article>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="diagramVisible" :title="`${workflowName} · 流程图`" width="88%" top="6vh" destroy-on-close>
      <div class="diagram-shell">
        <BpmnViewerPanel v-if="diagram?.bpmnXml" :bpmn-xml="diagram.bpmnXml" />
        <el-empty v-else description="暂无流程图" />
      </div>
    </el-dialog>

    <el-dialog v-model="dataVisible" title="申请内容" width="640px">
      <div v-if="selectedDataEntries.length" class="data-list">
        <div v-for="item in selectedDataEntries" :key="item.key" class="data-row">
          <span>{{ fieldLabel(item.key) }}</span>
          <strong>{{ formatValue(item.value) }}</strong>
        </div>
      </div>
      <el-empty v-else description="该记录没有可展示的数据" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Bell, CircleCheckFilled, Clock, Document, EditPen, VideoPlay, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BpmnViewerPanel from '@/components/ai/BpmnViewerPanel.vue'
import {
  getProcessInstanceDetail,
  getProcessInstanceDiagram,
  getProcessInstanceSubmissions,
  getRuntimeState,
  urgeProcessInstance
} from '@/api/processInstance'
import type { FormSubmission, ProcessDiagram, ProcessInstance, RuntimeState } from '@/types/workflow'
import request from '@/api/request'

type TimelineNode = {
  type: string
  nodeName: string
  operatorName?: string
  time?: string
  duration?: string
  action?: string
  comment?: string
}

const route = useRoute()
const router = useRouter()
const activeTab = ref('info')
const loading = ref(false)
const diagramLoading = ref(false)
const submissionsLoading = ref(false)
const timelineLoading = ref(false)
const message = ref('')
const instance = ref<ProcessInstance | null>(null)
const diagram = ref<ProcessDiagram | null>(null)
const submissions = ref<FormSubmission[]>([])
const runtimeState = ref<RuntimeState | null>(null)
const timelineNodes = ref<TimelineNode[]>([])
const diagramVisible = ref(false)
const dataVisible = ref(false)
const selectedData = ref<Record<string, unknown>>({})
const urgeLoading = ref(false)

const workflowName = computed(() => diagram.value?.templateName || '业务流程')
const canUrge = computed(() => instance.value?.status === 'running' && runtimeState.value?.completed !== true)
const currentStep = computed(() => {
  if (instance.value?.status === 'completed') return '流程已完成'
  if (instance.value?.status === 'draft') return '等待提交'
  return runtimeState.value?.currentTaskName || instance.value?.currentNodeName || '等待系统处理'
})
const statusTagType = computed(() => {
  if (instance.value?.status === 'completed') return 'success'
  if (instance.value?.status === 'running') return 'warning'
  if (instance.value?.status === 'draft') return 'info'
  return ''
})
const statusDescription = computed(() => {
  if (instance.value?.status === 'completed') return '该申请已完成全部处理步骤。'
  if (instance.value?.status === 'running') return '申请正在流转，可在处理记录中查看最新进展。'
  if (instance.value?.status === 'submitted') return '申请已提交，正在等待流程启动。'
  return '申请尚未提交，可继续填写和修改。'
})
const elapsedText = computed(() => {
  if (!instance.value?.createTime || !instance.value?.updateTime) return '-'
  const start = new Date(instance.value.createTime).getTime()
  const end = new Date(instance.value.updateTime).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '-'
  const minutes = Math.max(1, Math.round((end - start) / 60000))
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const remain = minutes % 60
  return remain ? `${hours} 小时 ${remain} 分钟` : `${hours} 小时`
})
const selectedDataEntries = computed(() => Object.entries(selectedData.value).map(([key, value]) => ({ key, value })))

onMounted(async () => {
  await Promise.all([loadInstance(), loadDiagram(), loadSubmissions(), loadTimeline()])
})

async function loadInstance() {
  const id = Number(route.params.id)
  if (!id) { message.value = '申请详情加载失败。'; return }
  loading.value = true
  try {
    instance.value = await getProcessInstanceDetail(id)
    if (instance.value?.status === 'running') {
      try { runtimeState.value = await getRuntimeState(id) } catch { runtimeState.value = null }
    }
  } catch (error) {
    message.value = normalizeError(error, '申请详情加载失败。')
  } finally { loading.value = false }
}

async function loadDiagram() {
  const id = Number(route.params.id)
  if (!id) return
  diagramLoading.value = true
  try { diagram.value = await getProcessInstanceDiagram(id) }
  catch (error) { message.value = normalizeError(error, '流程图加载失败。') }
  finally { diagramLoading.value = false }
}

async function loadSubmissions() {
  const id = Number(route.params.id)
  if (!id) return
  submissionsLoading.value = true
  try { submissions.value = await getProcessInstanceSubmissions(id) }
  catch (error) { message.value = normalizeError(error, '申请材料加载失败。') }
  finally { submissionsLoading.value = false }
}

async function loadTimeline() {
  const id = Number(route.params.id)
  if (!id) return
  timelineLoading.value = true
  try {
    const res: any = await request.get(`/api/process-instances/${id}/timeline`)
    timelineNodes.value = res?.nodes ?? []
  } catch { timelineNodes.value = [] }
  finally { timelineLoading.value = false }
}

function viewBpmn() {
  if (!diagram.value?.bpmnXml) {
    ElMessage.warning('该申请暂无可查看的流程图')
    return
  }
  diagramVisible.value = true
}

function continueEdit() {
  if (instance.value) router.push(`/process/start-preview?instanceId=${instance.value.id}`)
}

async function urgeCurrentTask() {
  if (!instance.value) return
  urgeLoading.value = true
  try {
    await urgeProcessInstance(instance.value.id)
    ElMessage.success('催办通知已发送')
  } catch (error) {
    ElMessage.error(normalizeError(error, '催办失败，请稍后重试。'))
  } finally { urgeLoading.value = false }
}

function showData(row: FormSubmission) {
  try { selectedData.value = JSON.parse(row.formDataJson || '{}') }
  catch { selectedData.value = { 内容: row.formDataJson || '-' } }
  dataVisible.value = true
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    draft: '草稿', submitted: '已提交', running: '处理中', completed: '已完成', rejected: '已驳回', terminated: '已终止'
  }
  return labels[status || ''] || status || '-'
}

function submissionStatusLabel(status?: string) {
  return status === 'rejected' ? '已驳回' : status === 'draft' ? '草稿' : '已提交'
}

function businessTypeLabel(type?: string | null) {
  const labels: Record<string, string> = {
    start: '发起申请', form_fill: '补充材料', approval: '审批处理', generic_task: '人工处理'
  }
  return labels[type || ''] || '申请记录'
}

function tagType(node: TimelineNode) {
  if (node.action === '驳回') return 'danger'
  if (node.action === '通过' || node.action === '办结') return 'success'
  if (node.type === 'start') return 'info'
  return 'warning'
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date)
}

function fieldLabel(key: string) {
  const labels: Record<string, string> = {
    leaveType: '请假类型', startDate: '开始日期', endDate: '结束日期', leaveDays: '请假天数', reason: '申请原因',
    expenseType: '费用类型', amount: '金额', expenseDate: '费用日期', description: '说明',
    itemName: '采购物品', quantity: '数量', estimatedAmount: '预计金额', expectedDate: '期望日期', purpose: '用途',
    approvalResult: '处理结果', approvalComment: '审批意见', rejectReason: '驳回原因'
  }
  return labels[key] || key.replace(/([A-Z])/g, ' $1').trim()
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.map(formatValue).join('、') || '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function normalizeError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 16px; }

.page-head, .overview-panel, .timeline-panel, .submission-panel {
  border: 1px solid var(--line); border-radius: 8px; background: rgba(255,255,255,.96); box-shadow: var(--shadow);
}
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 24px 28px; }
.head-copy { min-width: 0; }
.title-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.title-line h1 { margin: 0; font-size: 28px; line-height: 1.25; letter-spacing: 0; overflow-wrap: anywhere; }
.head-copy p { margin: 8px 0 0; color: var(--muted); }
.head-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; flex-shrink: 0; }

.message-bar { padding: 11px 14px; border-left: 3px solid #e6a23c; background: #fdf6ec; color: #8a5a12; }
.detail-tabs { min-height: 420px; }
.overview-panel, .timeline-panel, .submission-panel { padding: 24px; }

.progress-summary { display: flex; align-items: center; gap: 16px; padding-bottom: 22px; border-bottom: 1px solid var(--line); }
.progress-mark { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: #ecf5ff; color: #409eff; font-size: 24px; flex: 0 0 auto; }
.progress-mark.running { background: #fdf6ec; color: #e6a23c; }
.progress-mark.completed { background: #f0f9eb; color: #67c23a; }
.progress-summary .summary-label { display: block; margin-bottom: 3px; color: var(--muted); font-size: 13px; }
.progress-summary strong { display: block; font-size: 20px; }
.progress-summary p { margin: 5px 0 0; color: var(--muted); }

.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; padding-top: 22px; }
.summary-grid > div { min-width: 0; padding: 0 20px; border-right: 1px solid var(--line); }
.summary-grid > div:first-child { padding-left: 0; }
.summary-grid > div:last-child { border-right: 0; }
.summary-grid dt { margin-bottom: 7px; color: var(--muted); font-size: 13px; }
.summary-grid dd { margin: 0; font-weight: 650; overflow-wrap: anywhere; }

.timeline { padding: 4px 0; }
.tl-item { position: relative; display: flex; gap: 16px; padding-bottom: 26px; }
.tl-item:last-child { padding-bottom: 0; }
.tl-dot { position: relative; z-index: 1; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; flex: 0 0 auto; background: #fdf6ec; color: #e6a23c; }
.tl-dot.start { background: #ecf5ff; color: #409eff; }
.tl-dot.end { background: #f0f9eb; color: #67c23a; }
.tl-line { position: absolute; left: 16px; top: 36px; bottom: 2px; width: 2px; background: var(--line); }
.tl-content { min-width: 0; flex: 1; padding-top: 4px; }
.tl-head { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.tl-duration { color: var(--muted); font-size: 12px; }
.tl-meta { display: flex; gap: 16px; margin-top: 6px; color: var(--muted); font-size: 13px; }
.tl-comment { margin: 10px 0 0; padding: 9px 12px; border-left: 3px solid #c0c4cc; background: #f5f7fa; color: #606266; }

.submission-list { display: flex; flex-direction: column; }
.submission-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto auto; align-items: center; gap: 14px; padding: 15px 4px; border-bottom: 1px solid var(--line); }
.submission-row:last-child { border-bottom: 0; }
.submission-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 6px; background: #ecf5ff; color: #409eff; }
.submission-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.submission-main span { color: var(--muted); font-size: 13px; }

.diagram-shell { min-height: 480px; border: 1px solid var(--line); }
.data-list { border-top: 1px solid var(--line); }
.data-row { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 18px; padding: 13px 4px; border-bottom: 1px solid var(--line); }
.data-row span { color: var(--muted); }
.data-row strong { font-weight: 500; overflow-wrap: anywhere; white-space: pre-wrap; }

@media (max-width: 1000px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px 0; }
  .summary-grid > div:nth-child(2) { border-right: 0; }
}
@media (max-width: 720px) {
  .page-head { align-items: flex-start; flex-direction: column; padding: 20px; }
  .head-actions { width: 100%; }
  .title-line h1 { font-size: 24px; }
  .summary-grid { grid-template-columns: 1fr; }
  .summary-grid > div { padding: 0 0 14px; border-right: 0; border-bottom: 1px solid var(--line); }
  .summary-grid > div:last-child { padding-bottom: 0; border-bottom: 0; }
  .submission-row { grid-template-columns: 38px minmax(0, 1fr) auto; }
  .submission-row .el-button { grid-column: 2 / -1; justify-self: start; }
  .data-row { grid-template-columns: 1fr; gap: 5px; }
}
</style>
