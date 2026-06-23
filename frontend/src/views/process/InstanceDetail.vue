<template>
  <div class="detail-page">
    <section class="page-head">
      <div>
        <h1>{{ instance?.instanceTitle || '流程实例详情' }}</h1>
        <p>查看流程实例、Flowable 关联信息与节点表单提交记录。</p>
      </div>
      <div class="head-actions">
        <el-button round @click="router.back()">返回</el-button>
        <el-button v-if="canUrge" round type="warning" :icon="Bell" :loading="urgeLoading" @click="urgeCurrentTask">催办</el-button>
        <el-button v-if="instance?.templateId" round type="primary" @click="viewBpmn">查看流程图</el-button>
        <el-button v-if="instance?.status === 'draft'" round type="success" @click="continueEdit">继续编辑</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- Tab 1: 基本信息 -->
      <el-tab-pane label="基本信息" name="info">
        <el-alert v-if="instance?.status === 'running'" type="success" show-icon :closable="false" title="流程运行中。可在「待办任务」页面处理当前审批节点。" />
        <el-alert v-else-if="instance?.status === 'completed'" type="info" show-icon :closable="false" title="流程已结束。可在「已办任务」页面查看历史审批记录。" />
        <el-alert v-else-if="instance?.status === 'submitted'" type="warning" show-icon :closable="false" title="当前实例已提交，待启动流程引擎，仅支持查看。" />
        <el-alert v-else-if="instance?.status === 'draft'" type="info" show-icon :closable="false" title="当前实例为草稿，可返回发起预览页继续编辑。" />
        <el-alert v-if="runtimeState?.completed" type="info" show-icon :closable="false" title="Flowable 流程已结束，无活跃任务。" />
        <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />

        <section class="info-panel" v-loading="loading">
          <div class="info-grid">
            <div class="info-card"><span>实例编号</span><strong>{{ instance?.instanceCode || '-' }}</strong></div>
            <div class="info-card"><span>实例标题</span><strong>{{ instance?.instanceTitle || '-' }}</strong></div>
            <div class="info-card"><span>状态</span><strong>{{ statusLabel(instance?.status) }}</strong></div>
            <div class="info-card"><span>模板ID</span><strong>{{ instance?.templateId || '-' }}</strong></div>
            <div class="info-card">
              <span>当前节点ID<el-tag v-if="runtimeState && !runtimeState.completed" type="success" size="small" effect="plain">实时</el-tag><el-tag v-else-if="runtimeState?.completed" type="info" size="small" effect="plain">已结束</el-tag></span>
              <strong>{{ (runtimeState && !runtimeState.completed ? runtimeState.currentTaskKey : null) || instance?.currentNodeKey || '-' }}</strong>
            </div>
            <div class="info-card">
              <span>当前节点名称<el-tag v-if="runtimeState && !runtimeState.completed" type="success" size="small" effect="plain">实时</el-tag><el-tag v-else-if="runtimeState?.completed" type="info" size="small" effect="plain">已结束</el-tag></span>
              <strong>{{ (runtimeState && !runtimeState.completed ? runtimeState.currentTaskName : null) || instance?.currentNodeName || '-' }}</strong>
            </div>
            <div class="info-card"><span>当前业务类型</span><strong>{{ businessTypeLabel(instance?.currentBusinessType) }}</strong></div>
            <div class="info-card"><span>创建时间</span><strong>{{ instance?.createTime || '-' }}</strong></div>
            <div class="info-card"><span>更新时间</span><strong>{{ instance?.updateTime || '-' }}</strong></div>
            <div class="info-card"><span>Flowable流程实例ID</span><strong>{{ instance?.flowableProcessInstanceId || '未启动' }}</strong></div>
            <div class="info-card"><span>Flowable流程定义ID</span><strong>{{ instance?.flowableDefinitionId || '未启动' }}</strong></div>
            <div class="info-card"><span>Flowable部署ID</span><strong>{{ instance?.flowableDeploymentId || '未启动' }}</strong></div>
          </div>
        </section>
      </el-tab-pane>

      <!-- Tab 2: 流转时间线 -->
      <el-tab-pane label="流转时间线" name="timeline">
        <section class="timeline-panel" v-loading="timelineLoading">
          <el-empty v-if="timelineNodes.length === 0" description="暂无流转记录" />
          <div v-else class="timeline">
            <div v-for="(node, idx) in timelineNodes" :key="idx" class="tl-item">
              <div class="tl-dot" :class="node.type">
                <el-icon v-if="node.type === 'start'"><CircleCheck /></el-icon>
                <el-icon v-else-if="node.type === 'end'"><CircleCheckFilled /></el-icon>
                <el-icon v-else><Clock /></el-icon>
              </div>
              <div class="tl-line" v-if="idx < timelineNodes.length - 1"></div>
              <div class="tl-content">
                <div class="tl-head">
                  <span class="tl-node-name">{{ node.nodeName }}</span>
                  <el-tag :type="tagType(node)" size="small">{{ node.action }}</el-tag>
                  <span v-if="node.duration" class="tl-duration">⏱ {{ node.duration }}</span>
                </div>
                <div class="tl-meta">
                  <span>{{ node.operatorName }}</span>
                  <span class="tl-time">{{ node.time }}</span>
                </div>
                <div v-if="node.comment" class="tl-comment">「{{ node.comment }}」</div>
              </div>
            </div>
          </div>
        </section>
      </el-tab-pane>

      <!-- Tab 3: 表单提交记录 -->
      <el-tab-pane label="表单提交" name="submissions">
        <section class="table-panel">
          <div class="section-title"><h2>节点表单提交记录</h2><el-tag effect="plain">{{ submissions.length }} 条</el-tag></div>
          <el-table v-loading="submissionsLoading" :data="submissions" border empty-text="暂无提交记录">
            <el-table-column prop="nodeName" label="节点名称" min-width="150" />
            <el-table-column prop="nodeKey" label="节点ID" min-width="150" />
            <el-table-column label="业务类型" min-width="120"><template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template></el-table-column>
            <el-table-column prop="formId" label="表单ID" width="90" />
            <el-table-column label="状态" width="110"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="180" />
            <el-table-column prop="updateTime" label="更新时间" min-width="180" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }"><el-button text type="primary" @click="showJson(row)">查看 JSON</el-button></template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="jsonVisible" title="formData JSON" width="680px">
      <el-alert v-if="jsonParseError" type="warning" show-icon :closable="false" title="表单数据不是合法 JSON，已展示原始内容。" />
      <pre>{{ selectedJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, CircleCheck, CircleCheckFilled, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getProcessInstanceDetail, getProcessInstanceSubmissions, getRuntimeState, urgeProcessInstance } from '@/api/processInstance'
import type { FormSubmission, ProcessInstance, RuntimeState } from '@/types/workflow'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const activeTab = ref('info')
const loading = ref(false)
const submissionsLoading = ref(false)
const timelineLoading = ref(false)
const message = ref('')
const instance = ref<ProcessInstance | null>(null)
const submissions = ref<FormSubmission[]>([])
const runtimeState = ref<RuntimeState | null>(null)
const timelineNodes = ref<any[]>([])
const jsonVisible = ref(false)
const selectedJson = ref('')
const jsonParseError = ref(false)
const urgeLoading = ref(false)

const canUrge = computed(() =>
  instance.value?.status === 'running' && runtimeState.value?.completed !== true
)

onMounted(async () => {
  await Promise.all([loadInstance(), loadSubmissions(), loadTimeline()])
})

async function loadInstance() {
  const id = Number(route.params.id)
  if (!id) { message.value = '流程实例详情加载失败。'; return }
  loading.value = true
  try {
    instance.value = await getProcessInstanceDetail(id)
    if (instance.value?.status === 'running') {
      try { runtimeState.value = await getRuntimeState(id) } catch { runtimeState.value = null }
    }
  } catch (error) {
    message.value = normalizeError(error, '流程实例详情加载失败。')
  } finally { loading.value = false }
}

async function loadSubmissions() {
  const id = Number(route.params.id)
  if (!id) return
  submissionsLoading.value = true
  try { submissions.value = await getProcessInstanceSubmissions(id) }
  catch (error) { message.value = normalizeError(error, '表单提交记录加载失败。') }
  finally { submissionsLoading.value = false }
}

async function loadTimeline() {
  const id = Number(route.params.id)
  if (!id) return
  timelineLoading.value = true
  try {
    const res: any = await request.get(`/api/process-instances/${id}/timeline`)
    timelineNodes.value = res?.nodes ?? []
  } catch { /* empty */ }
  finally { timelineLoading.value = false }
}

function tagType(node: any) {
  if (node.action === '驳回') return 'danger'
  if (node.action === '通过' || node.action === '办结') return 'success'
  if (node.action === '发起流程') return 'info'
  return ''
}

function continueEdit() {
  if (!instance.value) return
  router.push(`/process/start-preview?instanceId=${instance.value.id}`)
}

function viewBpmn() {
  if (!instance.value?.templateId) return
  router.push(`/process-designer?templateId=${instance.value.templateId}`)
}

async function urgeCurrentTask() {
  if (!instance.value) return
  urgeLoading.value = true
  try {
    await urgeProcessInstance(instance.value.id)
    ElMessage.success('已发送催办通知')
  } catch (error) {
    ElMessage.error(normalizeError(error, '催办失败，请稍后重试。'))
  } finally { urgeLoading.value = false }
}

function showJson(row: FormSubmission) {
  jsonParseError.value = false
  const value = row.formDataJson || '{}'
  try { selectedJson.value = JSON.stringify(JSON.parse(value), null, 2) }
  catch { selectedJson.value = value; jsonParseError.value = true }
  jsonVisible.value = true
}

function statusLabel(status?: string) {
  if (status === 'draft') return '草稿'
  if (status === 'submitted') return '已提交，待启动流程引擎'
  if (status === 'running') return '流程运行中'
  return status || '-'
}

function businessTypeLabel(type?: string | null) {
  const map: Record<string, string> = { start: '开始/发起', form_fill: '表单填写', approval: '审批处理', generic_task: '人工任务' }
  return map[type || ''] || type || '-'
}

function normalizeError(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  return fallback
}
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 18px; }

.page-head, .info-panel, .table-panel, .timeline-panel {
  border: 1px solid var(--line); border-radius: 18px;
  background: rgba(255,255,255,0.94); box-shadow: var(--shadow);
}

.page-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 24px; }
.page-head h1 { margin: 10px 0 6px; font-size: 28px; }
.page-head p { margin: 0; color: var(--muted); }
.head-actions, .section-title { display: flex; align-items: center; gap: 10px; }

.info-panel, .table-panel, .timeline-panel { padding: 18px; }

.info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.info-card { padding: 14px; border: 1px solid var(--line); border-radius: 8px; background: var(--el-fill-color-lighter); }
.info-card span { display: block; margin-bottom: 6px; color: var(--muted); font-size: 13px; }
.info-card strong { display: block; overflow-wrap: anywhere; }

.section-title { justify-content: space-between; margin-bottom: 14px; }
.section-title h2 { margin: 0; font-size: 18px; }

/* ---- 时间线 ---- */
.timeline { padding: 8px 0; }
.tl-item { position: relative; display: flex; gap: 16px; padding-bottom: 24px; }
.tl-item:last-child { padding-bottom: 0; }
.tl-dot {
  width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; z-index: 1; font-size: 16px;
}
.tl-dot.start { background: #409eff1a; color: #409eff; }
.tl-dot.approval { background: #e6a23c1a; color: #e6a23c; }
.tl-dot.end { background: #67c23a1a; color: #67c23a; }
.tl-line {
  position: absolute; left: 15px; top: 36px; bottom: 4px; width: 2px;
  background: linear-gradient(180deg, #dcdfe6, #e4e7ed);
  z-index: 0;
}
.tl-content { flex: 1; padding-top: 4px; }
.tl-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tl-node-name { font-weight: 700; font-size: 15px; }
.tl-duration { font-size: 12px; color: #909399; background: #f0f2f5; padding: 1px 8px; border-radius: 10px; }
.tl-meta { margin-top: 4px; font-size: 13px; color: #606266; display: flex; gap: 16px; }
.tl-time { color: #909399; }
.tl-comment { margin-top: 6px; font-size: 13px; color: #606266; font-style: italic; background: #f5f7fa; padding: 6px 12px; border-radius: 6px; }

.detail-tabs { margin-top: -4px; }

pre { margin: 14px 0 0; padding: 16px; border-radius: 8px; background: #111827; color: #e5e7eb; overflow: auto; }

@media (max-width: 900px) { .info-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .page-head { align-items: flex-start; flex-direction: column; } .info-grid { grid-template-columns: 1fr; } }
</style>
