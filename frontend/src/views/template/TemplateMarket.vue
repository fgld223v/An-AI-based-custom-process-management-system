<template>
  <div class="market-page">
    <!-- Hero -->
    <section class="market-hero">
      <div>
        <h1>可复用流程模板</h1>
        <p>从已上架的流程模板中快速复制，沉淀请假、报销、采购、合同审批等常用流程。</p>
      </div>
      <el-button round :icon="Refresh" @click="loadData">刷新</el-button>
    </section>

    <!-- Filter -->
    <section class="market-filter">
      <el-input v-model="keyword" clearable placeholder="搜索标题或说明" />
      <el-select v-model="selectedBizTypeId" clearable placeholder="业务分类">
        <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
      </el-select>
    </section>

    <!-- Card Grid -->
    <section v-loading="loading" class="market-grid">
      <article v-for="item in filteredItems" :key="item.id" class="market-card" @click="openDetail(item)">
        <div class="cover">
          <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" />
          <div v-else class="cover-fallback">FLOW</div>
        </div>
        <div class="market-card-body">
          <div class="market-card-head">
            <h2>{{ item.title }}</h2>
            <el-tag effect="plain">{{ marketTypeLabel(item.type) }}</el-tag>
          </div>
          <p>{{ item.description || '暂无说明' }}</p>
          <div class="market-meta">
            <span>{{ bizTypeName(item.bizTypeId) }}</span>
            <span>使用 {{ item.useCount || 0 }}</span>
            <span>评分 {{ item.rating ?? 0 }}</span>
          </div>
          <div class="market-tags">
            <el-tag v-for="tag in parseTags(item.tags)" :key="tag" round effect="plain">{{ tag }}</el-tag>
          </div>
          <div class="market-card-footer">
            <small>{{ formatTime(item.publishedAt) }}</small>
            <el-button v-if="canCopyToMyProcess" type="success" round size="small" @click.stop="copyMarketItem(item)">使用模板</el-button>
          </div>
        </div>
      </article>

      <el-empty v-if="!loading && filteredItems.length === 0" description="暂无模板市场数据" class="market-empty" />
    </section>

    <!-- Detail Overlay -->
    <Teleport to="body">
      <div v-if="detailVisible" class="detail-backdrop" @click.self="closeDetail" />
      <div v-if="detailVisible" class="detail-panel">
        <!-- Panel Header -->
        <div class="panel-topbar">
          <div class="topbar-left">
            <div class="brand-mark-sm">AF</div>
            <div>
              <h2>{{ selectedItem?.title || '模板详情' }}</h2>
              <p>{{ marketTypeLabel(selectedItem?.type) }} · {{ bizTypeName(selectedItem?.bizTypeId) }}</p>
            </div>
          </div>
          <el-button circle :icon="Close" @click="closeDetail" />
        </div>

        <!-- Panel Body -->
        <div class="panel-body" v-loading="detailLoading">
          <template v-if="selectedItem">
            <!-- Basic Info -->
            <section class="info-section">
              <div class="info-desc">
                <h3>模板说明</h3>
                <p>{{ selectedItem.description || '暂无说明' }}</p>
              </div>
              <div class="info-meta-grid">
                <div class="meta-item">
                  <span>使用次数</span>
                  <strong>{{ selectedItem.useCount || 0 }}</strong>
                </div>
                <div class="meta-item">
                  <span>评分</span>
                  <strong>{{ selectedItem.rating ?? 0 }}</strong>
                </div>
                <div class="meta-item">
                  <span>标签</span>
                  <strong>{{ parseTags(selectedItem.tags).join('、') || '-' }}</strong>
                </div>
                <div class="meta-item">
                  <span>上架时间</span>
                  <strong>{{ formatTime(selectedItem.publishedAt) }}</strong>
                </div>
              </div>
            </section>

            <!-- Two-column: Form + Flow -->
            <section class="work-area">
              <!-- Left: Form -->
              <div class="form-panel">
                <div class="panel-head">
                  <h3>{{ boundForm ? `表单：${boundForm.formName}` : '流程表单' }}</h3>
                  <p v-if="boundForm">请查看该模板内置的表单内容</p>
                  <p v-else>该模板暂未配置发起表单</p>
                </div>
                <div class="form-content">
                  <DynamicFormRenderer
                    v-if="boundForm"
                    v-model="previewFormData"
                    :form-schema="boundForm.formSchema"
                    :field-list="boundForm.fieldList"
                    :readonly="true"
                  />
                  <el-empty v-else description="暂未配置表单" :image-size="100" />
                </div>
              </div>

              <!-- Right: Process Flow -->
              <aside class="route-panel">
                <div class="panel-head">
                  <h3>流程信息</h3>
                  <p>{{ templateDetail ? `版本 v${templateDetail.version || 1}` : '' }}</p>
                </div>

                <div class="route-tabs">
                  <button :class="{ active: routeView === 'path' }" type="button" @click="routeView = 'path'">审批路径</button>
                  <button :class="{ active: routeView === 'diagram' }" type="button" @click="routeView = 'diagram'">流程图</button>
                </div>

                <!-- Approval Path -->
                <div v-if="routeView === 'path'" class="route-content">
                  <div class="route-summary">
                    <span>共 {{ approvalSteps.length || 0 }} 个审批节点</span>
                    <strong>{{ approvalSteps.length ? '审批人需在流程发起后按规则分配' : '暂无节点数据' }}</strong>
                  </div>

                  <div class="timeline">
                    <div class="timeline-step is-start">
                      <div class="step-mark"><VideoPlay /></div>
                      <div class="step-content">
                        <h3>提交申请</h3>
                        <p>申请人填写表单并启动流程</p>
                      </div>
                    </div>

                    <div v-for="(step, index) in approvalSteps" :key="step.nodeKey || index" class="timeline-step">
                      <div class="step-mark">{{ index + 1 }}</div>
                      <div class="step-content">
                        <div class="step-title-row">
                          <h3>{{ step.nodeName || `节点 ${index + 1}` }}</h3>
                          <el-tag size="small" effect="plain">{{ approvalModeLabel(step.approvalMode) }}</el-tag>
                        </div>
                        <p>{{ strategyLabel(step.assignStrategy) }}</p>
                      </div>
                    </div>

                    <div class="timeline-step is-end">
                      <div class="step-mark"><CircleCheck /></div>
                      <div class="step-content">
                        <h3>流程完成</h3>
                        <p>所有审批节点通过后流程结束</p>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- BPMN Diagram -->
                <div v-if="routeView === 'diagram'" class="diagram-content">
                  <BpmnViewerPanel v-if="templateDetail?.bpmnXml" :bpmn-xml="templateDetail.bpmnXml" />
                  <el-empty v-else description="该模板暂无流程图" :image-size="100" />
                </div>
              </aside>
            </section>
          </template>
        </div>

        <!-- Bottom Bar -->
        <div class="panel-bottombar">
          <span class="bottombar-note">{{ marketActionNote }}</span>
          <div class="bottombar-actions">
            <el-button round @click="closeDetail">关闭</el-button>
            <el-button v-if="selectedItem && canCopyToMyProcess" round type="success" :loading="copyLoading" @click="copyMarketItem(selectedItem)">
              复制到我的流程
            </el-button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Close, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { getBizTypes } from '@/api/bizType'
import { getProcessTemplateBoundForm, getProcessTemplateDetail } from '@/api/processTemplate'
import { copyTemplateFromMarket, getTemplateMarketList } from '@/api/templateMarket'
import BpmnViewerPanel from '@/components/ai/BpmnViewerPanel.vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { useAuthStore } from '@/stores/auth'
import type { BizType, FormDefinition, ProcessTemplate, TemplateMarketItem } from '@/types/workflow'

const loading = ref(false)
const authStore = useAuthStore()
const keyword = ref('')
const selectedBizTypeId = ref<number | null>(null)
const marketItems = ref<TemplateMarketItem[]>([])
const bizTypes = ref<BizType[]>([])

// Detail state
const selectedItem = ref<TemplateMarketItem | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const copyLoading = ref(false)
const templateDetail = ref<ProcessTemplate | null>(null)
const boundForm = ref<FormDefinition | null>(null)
const previewFormData = ref<Record<string, unknown>>({})
const routeView = ref<'path' | 'diagram'>('path')
const canCopyToMyProcess = computed(() => authStore.user?.systemRole === 'biz_admin')
const marketActionNote = computed(() => canCopyToMyProcess.value
  ? '将此模板复制到你的流程中以开始使用'
  : '系统管理员可预览模板内容，并在流程模板管理中维护模板')

const filteredItems = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return marketItems.value.filter((item) => {
    const matchKeyword =
      !key || item.title.toLowerCase().includes(key) || (item.description || '').toLowerCase().includes(key)
    const matchBizType = !selectedBizTypeId.value || item.bizTypeId === selectedBizTypeId.value
    return matchKeyword && matchBizType
  })
})

/** Approximate approval steps parsed from nodeConfig JSON */
const approvalSteps = computed(() => {
  if (!templateDetail.value?.nodeConfig) return []
  try {
    const config = JSON.parse(templateDetail.value.nodeConfig)
    if (Array.isArray(config)) return config
    // nodeConfig may be an object with a steps/nodes array
    if (config.steps) return config.steps
    if (config.nodes) return config.nodes
    if (config.approvalSteps) return config.approvalSteps
    return Object.values(config).filter((item: any) => item?.businessType === 'approval')
  } catch {
    return []
  }
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [items, types] = await Promise.all([getTemplateMarketList(), getBizTypes()])
    marketItems.value = items
    bizTypes.value = types
  } finally {
    loading.value = false
  }
}

async function openDetail(item: TemplateMarketItem) {
  selectedItem.value = item
  detailVisible.value = true
  detailLoading.value = true
  templateDetail.value = null
  boundForm.value = null
  previewFormData.value = {}
  routeView.value = 'path'

  try {
    const [tmpl, binding] = await Promise.all([
      getProcessTemplateDetail(item.sourceId),
      getProcessTemplateBoundForm(item.sourceId).catch(() => null)
    ])
    templateDetail.value = tmpl
    if (binding) {
      boundForm.value = binding.form
    }
  } catch {
    // 模板可能已被删除
    ElMessage.warning('模板详情加载失败，可能已被下架')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  selectedItem.value = null
  templateDetail.value = null
  boundForm.value = null
}

async function copyMarketItem(item: TemplateMarketItem) {
  if (!canCopyToMyProcess.value) {
    ElMessage.warning('只有业务管理员可以复制模板到我的流程')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入复制后的模板名称', '使用模板', {
    inputValue: `${item.title}-副本`,
    confirmButtonText: '复制',
    cancelButtonText: '取消'
  })
  copyLoading.value = true
  try {
    await copyTemplateFromMarket(item.id, { newTemplateName: value })
    ElMessage.success('模板已复制到我的流程')
    closeDetail()
    await loadData()
  } finally {
    copyLoading.value = false
  }
}

function bizTypeName(id?: number | null) {
  return bizTypes.value.find((item) => item.id === id)?.typeName || '未分类'
}

function marketTypeLabel(type?: string) {
  const map: Record<string, string> = { template: '流程模板', fragment: '流程片段' }
  return map[(type || '').toLowerCase()] || type || '-'
}

function parseTags(tags?: string) {
  if (!tags) return []
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return tags.split(',').map((item) => item.trim()).filter(Boolean)
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function approvalModeLabel(mode?: string) {
  const map: Record<string, string> = { SINGLE: '单人审批', ALL: '会签', ANY: '或签', OR: '或签' }
  return map[mode?.toUpperCase() || ''] || mode || '单人审批'
}

function strategyLabel(strategy?: string) {
  const map: Record<string, string> = {
    DIRECT: '指定审批人',
    BY_ROLE: '按角色分配',
    BY_DEPARTMENT: '按部门分配',
    BY_SUPERVISOR: '直属上级审批'
  }
  return map[strategy?.toUpperCase() || ''] || strategy || '待分配'
}
</script>

<style scoped>
/* ── Page layout ── */
.market-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.market-hero,
.market-filter,
.market-card {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}

/* ── Hero ── */
.market-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 26px;
  border-radius: 24px;
}
.market-hero h1 { margin: 10px 0 6px; font-size: 30px; }
.market-hero p { margin: 0; color: var(--muted); line-height: 1.7; }

/* ── Filter ── */
.market-filter {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
}

/* ── Card grid ── */
.market-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  min-height: 280px;
}
.market-card {
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: 0.18s ease;
}
.market-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 48px rgba(28, 55, 47, 0.12);
}
.cover {
  height: 132px;
  background: linear-gradient(135deg, #e8f7f1, #ffffff);
}
.cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-fallback {
  display: grid; place-items: center; height: 100%;
  color: var(--primary-dark); font-size: 28px; font-weight: 800;
}
.market-card-body {
  display: flex; flex-direction: column; gap: 12px; padding: 18px;
}
.market-card-head,
.market-card-footer,
.market-meta { display: flex; align-items: center; gap: 10px; }
.market-card-head { justify-content: space-between; }
.market-card-head h2 { margin: 0; font-size: 18px; }
.market-card-body p { min-height: 44px; margin: 0; color: var(--muted); line-height: 1.6; }
.market-meta { flex-wrap: wrap; color: var(--muted); font-size: 13px; }
.market-tags { display: flex; flex-wrap: wrap; gap: 6px; min-height: 26px; }
.market-card-footer { justify-content: space-between; padding-top: 4px; }
.market-card-footer small { color: var(--muted); }
.market-empty { grid-column: 1 / -1; }

/* ── Detail backdrop ── */
.detail-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(4px);
}

/* ── Detail panel ── */
.detail-panel {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 2001;
  display: flex;
  flex-direction: column;
  width: min(1100px, 88vw);
  height: 100vh;
  background: #f5f7fa;
  box-shadow: -8px 0 40px rgba(0, 0, 0, 0.18);
}

.panel-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 28px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand-mark-sm {
  width: 42px; height: 42px; border-radius: 12px;
  background: linear-gradient(135deg, #00a3ff, #19c37d);
  color: #fff; display: grid; place-items: center;
  font-weight: 800; font-size: 16px;
  box-shadow: 0 8px 20px rgba(0, 163, 255, 0.22);
}
.panel-topbar h2 { margin: 0; font-size: 20px; }
.panel-topbar p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }

/* ── Panel body ── */
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* ── Info section ── */
.info-section {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 22px;
}
.info-desc {
  padding: 20px 24px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}
.info-desc h3 { margin: 0 0 10px; font-size: 15px; }
.info-desc p { margin: 0; color: var(--muted); line-height: 1.7; }
.info-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.meta-item {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}
.meta-item span { display: block; color: var(--muted); font-size: 12px; margin-bottom: 6px; }
.meta-item strong { font-size: 15px; color: var(--text); }

/* ── Work area (form + route) ── */
.work-area {
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 22px;
  align-items: flex-start;
}

.form-panel,
.route-panel {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.panel-head {
  padding: 18px 22px;
  border-bottom: 1px solid var(--line);
}
.panel-head h3 { margin: 0; font-size: 16px; }
.panel-head p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }

.form-content {
  padding: 22px;
  max-height: 520px;
  overflow-y: auto;
}

/* ── Route panel ── */
.route-panel {
  position: sticky;
  top: 0;
}

.route-tabs {
  display: flex;
  border-bottom: 1px solid var(--line);
}
.route-tabs button {
  flex: 1;
  padding: 12px 0;
  border: 0;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  transition: 0.15s;
  border-bottom: 2px solid transparent;
}
.route-tabs button.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
}

.route-content,
.diagram-content {
  padding: 18px 22px;
  max-height: 520px;
  overflow-y: auto;
}

.route-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(0, 163, 255, 0.06);
  font-size: 13px;
}
.route-summary span { color: var(--muted); }
.route-summary strong { color: var(--brand); }

/* ── Timeline ── */
.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.timeline-step {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  position: relative;
}
.timeline-step + .timeline-step {
  border-top: 1px dashed var(--line);
}
.step-mark {
  width: 36px; height: 36px; border-radius: 10px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  display: grid; place-items: center;
  font-weight: 700; font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}
.timeline-step.is-start .step-mark { background: rgba(0, 163, 255, 0.12); color: #0576b9; }
.timeline-step.is-end .step-mark { background: rgba(25, 195, 125, 0.12); color: #19c37d; }
.step-content h3 { margin: 0; font-size: 14px; }
.step-content > p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.step-title-row { display: flex; align-items: center; gap: 10px; }

/* ── Diagram ── */
.diagram-content {
  min-height: 300px;
}

/* ── Bottom bar ── */
.panel-bottombar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 28px;
  background: rgba(255, 255, 255, 0.98);
  border-top: 1px solid var(--line);
  flex-shrink: 0;
}
.bottombar-note { color: var(--muted); font-size: 13px; }
.bottombar-actions { display: flex; gap: 10px; }

/* ── Responsive ── */
@media (max-width: 1100px) {
  .market-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-panel { width: 100vw; }
  .info-section { grid-template-columns: 1fr; }
  .work-area { grid-template-columns: 1fr; }
  .route-panel { position: static; }
}

@media (max-width: 760px) {
  .market-hero,
  .market-filter {
    align-items: flex-start;
    grid-template-columns: 1fr;
    flex-direction: column;
  }
  .market-grid { grid-template-columns: 1fr; }
  .panel-bottombar { flex-direction: column; gap: 10px; align-items: stretch; }
  .bottombar-actions { justify-content: flex-end; }
}
</style>
