<template>
  <div class="monitor-page">
    <section class="monitor-head">
      <div>
        <el-tag :type="isGlobal ? 'warning' : 'success'" effect="plain">
          {{ isGlobal ? '全局运行视角' : '负责人视角' }}
        </el-tag>
        <h1>{{ isGlobal ? '运行监控' : '业务监控' }}</h1>
        <p>{{ isGlobal ? '监控系统内全部业务流程及其实例运行状态。' : '监控由当前账号负责的流程及其实例运行状态。' }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadInstances">刷新</el-button>
    </section>

    <section class="overview-grid">
      <div class="metric-block">
        <span>实例总数</span>
        <strong>{{ scopedInstances.length }}</strong>
      </div>
      <div class="metric-block running">
        <span>运行中</span>
        <strong>{{ runningCount }}</strong>
      </div>
      <div class="metric-block completed">
        <span>已完成</span>
        <strong>{{ completedCount }}</strong>
      </div>
      <div class="metric-block anomaly">
        <span>异常实例</span>
        <strong>{{ anomalyCount }}</strong>
      </div>
      <div class="metric-block">
        <span>平均办结耗时</span>
        <strong>{{ averageDuration }}</strong>
      </div>
    </section>

    <section class="monitor-body">
      <div class="filter-bar">
        <span v-if="isGlobal" class="filter-label">监控范围</span>
        <el-select v-if="isGlobal" v-model="bizTypeFilter" clearable filterable placeholder="全部业务类型" class="biz-type-filter">
          <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
        </el-select>
        <el-select v-if="isGlobal" v-model="ownerFilter" clearable filterable placeholder="全部负责人" class="owner-filter">
          <el-option v-for="item in ownerOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <el-select v-model="processFilter" clearable filterable placeholder="全部流程" class="process-filter">
          <el-option v-for="item in processOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
        <el-radio-group v-model="statusFilter">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="running">运行中</el-radio-button>
          <el-radio-button value="completed">已完成</el-radio-button>
          <el-radio-button value="anomaly">异常</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" clearable placeholder="搜索实例、流程或申请人" class="keyword-input" />
      </div>

      <el-table v-loading="loading" :data="pagedInstances" row-key="id" class="monitor-table" empty-text="暂无符合条件的实例">
        <el-table-column label="流程" min-width="190">
          <template #default="{ row }">
            <div class="primary-cell">{{ row.templateName || '-' }}</div>
            <div class="secondary-cell">{{ row.templateCode || '-' }} · v{{ row.templateVersion || 1 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="实例" min-width="190">
          <template #default="{ row }">
            <div class="primary-cell">{{ row.instanceTitle }}</div>
            <div class="secondary-cell">{{ row.instanceCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请人" min-width="120">
          <template #default="{ row }">{{ row.applicantName }}</template>
        </el-table-column>
        <el-table-column v-if="isGlobal" label="业务类型" min-width="120">
          <template #default="{ row }">{{ bizTypeName(row.bizTypeId) }}</template>
        </el-table-column>
        <el-table-column v-if="isGlobal" label="负责人" min-width="120">
          <template #default="{ row }">{{ row.processOwnerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            <el-tooltip v-if="row.anomaly" :content="row.anomalyReason || '异常实例'">
              <el-tag class="anomaly-tag" type="danger" effect="plain">异常</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="150">
          <template #default="{ row }">{{ row.currentNodeName || row.currentNodeKey || (row.status === 'completed' ? '已结束' : '-') }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ durationText(row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <span>共 {{ filteredInstances.length }} 条</span>
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" layout="prev, pager, next" :total="filteredInstances.length" />
      </div>
    </section>

    <el-drawer v-model="detailVisible" :title="detail?.instanceTitle || '实例详情'" size="min(760px, 100%)" destroy-on-close>
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detail">
          <section class="detail-summary">
            <div><span>实例编码</span><strong>{{ detail.instanceCode }}</strong></div>
            <div><span>流程版本</span><strong>{{ detail.templateName }} · v{{ detail.templateVersion || 1 }}</strong></div>
            <div><span>业务类型</span><strong>{{ bizTypeName(detail.bizTypeId) }}</strong></div>
            <div><span>申请人</span><strong>{{ detail.applicantName }}</strong></div>
            <div><span>负责人</span><strong>{{ detail.processOwnerName || '-' }}</strong></div>
            <div><span>当前节点</span><strong>{{ detail.currentNodeName || detail.currentNodeKey || '-' }}</strong></div>
            <div><span>运行耗时</span><strong>{{ durationText(detail) }}</strong></div>
            <div><span>实例状态</span><strong>{{ statusLabel(detail.status) }}</strong></div>
          </section>

          <el-tabs v-model="detailTab" class="detail-tabs">
            <el-tab-pane label="实例时间线" name="timeline">
              <el-empty v-if="timeline.length === 0" description="暂无时间线记录" />
              <div v-else class="timeline-list">
                <div v-for="(node, index) in timeline" :key="`${node.type}-${index}`" class="timeline-item">
                  <div class="timeline-marker" :class="node.type"></div>
                  <div class="timeline-main">
                    <div class="timeline-title">
                      <strong>{{ node.nodeName }}</strong>
                      <el-tag size="small" effect="plain">{{ node.action || '-' }}</el-tag>
                    </div>
                    <div class="timeline-meta">{{ node.operatorName || '-' }} · {{ node.time || '-' }}<span v-if="node.duration"> · {{ node.duration }}</span></div>
                    <p v-if="node.comment">{{ node.comment }}</p>
                  </div>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="表单数据" name="forms">
              <el-empty v-if="submissions.length === 0" description="暂无表单提交数据" />
              <el-collapse v-else accordion>
                <el-collapse-item v-for="submission in submissions" :key="submission.id" :name="submission.id">
                  <template #title>
                    <div class="submission-title">
                      <strong>{{ submission.nodeName || submission.nodeKey }}</strong>
                      <span>{{ formatTime(submission.updateTime) }}</span>
                    </div>
                  </template>
                  <div class="form-data-grid">
                    <div v-for="item in submissionFields(submission)" :key="item.key" class="form-data-row">
                      <span>{{ item.key }}</span>
                      <strong>{{ item.value }}</strong>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import {
  getBusinessProcessInstanceDetail,
  getBusinessProcessInstances,
  getBusinessProcessInstanceSubmissions,
  getBusinessProcessInstanceTimeline
} from '@/api/businessMonitor'
import {
  getGlobalProcessInstanceDetail,
  getGlobalProcessInstances,
  getGlobalProcessInstanceSubmissions,
  getGlobalProcessInstanceTimeline
} from '@/api/runtimeMonitor'
import { getBizTypes } from '@/api/bizType'
import type { BizType, BusinessProcessInstance, FormSubmission, ProcessTimelineNode } from '@/types/workflow'

const props = defineProps<{ scope: 'business' | 'global' }>()
const isGlobal = computed(() => props.scope === 'global')
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const instances = ref<BusinessProcessInstance[]>([])
const bizTypes = ref<BizType[]>([])
const detail = ref<BusinessProcessInstance | null>(null)
const timeline = ref<ProcessTimelineNode[]>([])
const submissions = ref<FormSubmission[]>([])
const processFilter = ref<number | null>(null)
const bizTypeFilter = ref<number | null>(null)
const ownerFilter = ref<number | null>(null)
const statusFilter = ref('all')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const detailTab = ref('timeline')

const runningCount = computed(() => scopedInstances.value.filter(item => ['running', 'submitted'].includes(item.status)).length)
const completedCount = computed(() => scopedInstances.value.filter(item => item.status === 'completed').length)
const anomalyCount = computed(() => scopedInstances.value.filter(item => item.anomaly).length)
const averageDuration = computed(() => {
  const durations = scopedInstances.value
    .filter(item => item.status === 'completed' && item.startedAt && item.endedAt)
    .map(item => new Date(item.endedAt!).getTime() - new Date(item.startedAt!).getTime())
    .filter(value => value >= 0)
  if (durations.length === 0) return '-'
  return formatDuration(durations.reduce((sum, value) => sum + value, 0) / durations.length)
})
const processOptions = computed(() => uniqueOptions(instances.value
  .filter(item => !bizTypeFilter.value || item.bizTypeId === bizTypeFilter.value)
  .filter(item => !ownerFilter.value || item.processOwnerId === ownerFilter.value)
  .map(item => ({
  id: item.templateId,
  label: `${item.templateName || item.templateCode || '未命名流程'} · v${item.templateVersion || 1}`
}))))
const ownerOptions = computed(() => uniqueOptions(instances.value
  .filter(item => !bizTypeFilter.value || item.bizTypeId === bizTypeFilter.value)
  .filter(item => item.processOwnerId)
  .map(item => ({ id: item.processOwnerId!, label: item.processOwnerName || `用户#${item.processOwnerId}` }))))
const scopedInstances = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return instances.value.filter(item => {
    if (bizTypeFilter.value && item.bizTypeId !== bizTypeFilter.value) return false
    if (processFilter.value && item.templateId !== processFilter.value) return false
    if (ownerFilter.value && item.processOwnerId !== ownerFilter.value) return false
    if (!search) return true
    return [item.instanceTitle, item.instanceCode, item.templateName, item.templateCode, item.applicantName]
      .some(value => value?.toLowerCase().includes(search))
  })
})
const filteredInstances = computed(() => scopedInstances.value.filter(item => {
  if (statusFilter.value === 'running') return ['running', 'submitted'].includes(item.status)
  if (statusFilter.value === 'completed') return item.status === 'completed'
  if (statusFilter.value === 'anomaly') return Boolean(item.anomaly)
  return true
}))
const pagedInstances = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredInstances.value.slice(start, start + pageSize.value)
})

watch(bizTypeFilter, () => {
  ownerFilter.value = null
  processFilter.value = null
})
watch(ownerFilter, () => { processFilter.value = null })
watch([bizTypeFilter, processFilter, ownerFilter, statusFilter, keyword], () => { currentPage.value = 1 })
onMounted(loadInstances)

async function loadInstances() {
  loading.value = true
  try {
    const [instanceList, bizTypeList] = await Promise.all([
      isGlobal.value ? getGlobalProcessInstances() : getBusinessProcessInstances(),
      bizTypes.value.length > 0 ? Promise.resolve(bizTypes.value) : getBizTypes()
    ])
    instances.value = instanceList
    bizTypes.value = bizTypeList
  } finally {
    loading.value = false
  }
}

async function openDetail(row: BusinessProcessInstance) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'timeline'
  try {
    if (isGlobal.value) {
      const [instance, timelineResult, submissionResult] = await Promise.all([
        getGlobalProcessInstanceDetail(row.id),
        getGlobalProcessInstanceTimeline(row.id),
        getGlobalProcessInstanceSubmissions(row.id)
      ])
      detail.value = instance
      timeline.value = timelineResult.nodes || []
      submissions.value = submissionResult
    } else {
      const [instance, timelineResult, submissionResult] = await Promise.all([
        getBusinessProcessInstanceDetail(row.id),
        getBusinessProcessInstanceTimeline(row.id),
        getBusinessProcessInstanceSubmissions(row.id)
      ])
      detail.value = instance
      timeline.value = timelineResult.nodes || []
      submissions.value = submissionResult
    }
  } finally {
    detailLoading.value = false
  }
}

function uniqueOptions(items: Array<{ id: number; label: string }>) {
  return Array.from(new Map(items.map(item => [item.id, item])).values())
}

function bizTypeName(id?: number | null) {
  return bizTypes.value.find(item => item.id === id)?.typeName || '未分类'
}

function statusLabel(status: string) {
  return ({ draft: '草稿', submitted: '已提交', running: '运行中', completed: '已完成', terminated: '已终止' } as Record<string, string>)[status] || status
}

function statusTag(status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  if (status === 'completed') return 'success'
  if (['running', 'submitted'].includes(status)) return 'warning'
  if (['failed', 'timeout', 'terminated'].includes(status)) return 'danger'
  return 'info'
}

function durationText(item: BusinessProcessInstance) {
  const start = item.startedAt || item.createdAt
  if (!start) return '-'
  const end = item.endedAt || new Date().toISOString()
  return formatDuration(Math.max(0, new Date(end).getTime() - new Date(start).getTime()))
}

function formatDuration(milliseconds: number) {
  const minutes = Math.floor(milliseconds / 60000)
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const restMinutes = minutes % 60
  if (hours < 24) return restMinutes ? `${hours}小时${restMinutes}分` : `${hours}小时`
  const days = Math.floor(hours / 24)
  return `${days}天${hours % 24}小时`
}

function formatTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function submissionFields(submission: FormSubmission) {
  try {
    const data = JSON.parse(submission.formDataJson || '{}') as Record<string, unknown>
    return Object.entries(data).map(([key, value]) => ({
      key,
      value: typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '-')
    }))
  } catch {
    return [{ key: '原始数据', value: submission.formDataJson || '-' }]
  }
}
</script>

<style scoped>
.monitor-page { display: flex; flex-direction: column; gap: 16px; }
.monitor-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 22px 24px; border-bottom: 1px solid var(--line); background: var(--panel); }
.monitor-head h1 { margin: 8px 0 4px; font-size: 26px; letter-spacing: 0; }
.monitor-head p { margin: 0; color: var(--muted); }
.overview-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border: 1px solid var(--line); background: var(--panel); }
.metric-block { min-width: 0; padding: 18px 20px; border-right: 1px solid var(--line); }
.metric-block:last-child { border-right: 0; }
.metric-block span { display: block; color: var(--muted); font-size: 13px; }
.metric-block strong { display: block; margin-top: 8px; font-size: 25px; overflow-wrap: anywhere; }
.metric-block.running strong { color: #b7791f; }
.metric-block.completed strong { color: #16805b; }
.metric-block.anomaly strong { color: #c2413a; }
.monitor-body { padding: 18px; border: 1px solid var(--line); background: var(--panel); }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-label { color: var(--muted); font-size: 13px; white-space: nowrap; }
.biz-type-filter { width: 180px; }
.process-filter { width: 230px; }
.owner-filter { width: 170px; }
.keyword-input { width: 240px; margin-left: auto; }
.primary-cell { font-weight: 650; color: var(--text); }
.secondary-cell { margin-top: 3px; color: var(--muted); font-size: 12px; }
.anomaly-tag { margin-left: 5px; }
.pagination-row { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; color: var(--muted); font-size: 13px; }
.detail-content { min-height: 280px; }
.detail-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border: 1px solid var(--line); }
.detail-summary > div { min-width: 0; padding: 13px 15px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.detail-summary > div:nth-child(2n) { border-right: 0; }
.detail-summary > div:nth-last-child(-n+2) { border-bottom: 0; }
.detail-summary span { display: block; color: var(--muted); font-size: 12px; }
.detail-summary strong { display: block; margin-top: 5px; overflow-wrap: anywhere; }
.detail-tabs { margin-top: 20px; }
.timeline-list { padding: 6px 0; }
.timeline-item { display: grid; grid-template-columns: 18px 1fr; gap: 12px; position: relative; padding-bottom: 22px; }
.timeline-item:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 13px; bottom: 0; width: 1px; background: var(--line); }
.timeline-marker { width: 11px; height: 11px; margin-top: 5px; border-radius: 50%; background: #7a8793; z-index: 1; }
.timeline-marker.start { background: #16805b; }
.timeline-marker.approval { background: #b7791f; }
.timeline-marker.end { background: #2474a6; }
.timeline-title { display: flex; align-items: center; gap: 8px; }
.timeline-meta { margin-top: 5px; color: var(--muted); font-size: 12px; }
.timeline-main p { margin: 8px 0 0; padding: 8px 10px; background: var(--el-fill-color-light); color: var(--text); }
.submission-title { display: flex; width: 100%; justify-content: space-between; padding-right: 12px; }
.submission-title span { color: var(--muted); font-size: 12px; }
.form-data-grid { border-top: 1px solid var(--line); }
.form-data-row { display: grid; grid-template-columns: minmax(120px, 0.35fr) 1fr; border-bottom: 1px solid var(--line); }
.form-data-row span, .form-data-row strong { padding: 10px 12px; overflow-wrap: anywhere; }
.form-data-row span { color: var(--muted); border-right: 1px solid var(--line); font-weight: 400; }
.form-data-row strong { font-weight: 500; }
@media (max-width: 1100px) { .overview-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .metric-block { border-bottom: 1px solid var(--line); } }
@media (max-width: 760px) { .monitor-head { align-items: flex-start; } .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filter-label { width: 100%; } .keyword-input, .biz-type-filter, .process-filter, .owner-filter { width: 100%; margin-left: 0; } .filter-bar :deep(.el-radio-group) { width: 100%; display: grid; grid-template-columns: repeat(4, 1fr); } .filter-bar :deep(.el-radio-button__inner) { width: 100%; padding: 8px 5px; } .detail-summary { grid-template-columns: 1fr; } .detail-summary > div { border-right: 0; } }
</style>
