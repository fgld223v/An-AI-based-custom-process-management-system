<template>
  <div class="my-process-page">
    <section class="page-head">
      <div>
        <h1>我的流程</h1>
        <p>创建并维护我负责的业务流程，发布后用户可在流程发起入口提交申请。</p>
      </div>
      <div class="head-actions">
        <el-button round :icon="Refresh" :loading="loading" @click="loadPageData">刷新</el-button>
        <el-button round type="success" :icon="Plus" @click="openCreateDialog">新建流程</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <div class="stat-card">
        <span>{{ processes.length }}</span>
        <p>版本总数</p>
      </div>
      <div class="stat-card">
        <span>{{ publishedCount }}</span>
        <p>已发布</p>
      </div>
      <div class="stat-card">
        <span>{{ draftCount }}</span>
        <p>草稿/审核中</p>
      </div>
      <div class="stat-card">
        <span>{{ marketCopyCount }}</span>
        <p>市场复制</p>
      </div>
    </section>

    <section class="table-panel">
      <div class="panel-title-row">
        <div>
          <h2>流程列表</h2>
          <p>已发布版本保持只读，修改时创建下一版草稿。</p>
        </div>
        <el-input v-model="keyword" clearable placeholder="搜索流程名称 / 编码" class="search-input" />
      </div>

      <el-table v-loading="loading" :data="filteredProcesses" row-key="id" class="soft-table">
        <el-table-column prop="templateName" label="流程名称" min-width="180" />
        <el-table-column prop="templateCode" label="流程编码" min-width="170" />
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version || 1 }}</template>
        </el-table-column>
        <el-table-column label="业务类型" min-width="140">
          <template #default="{ row }">{{ bizTypeName(row.bizTypeId) }}</template>
        </el-table-column>
        <el-table-column label="绑定表单" min-width="160">
          <template #default="{ row }">{{ formName(row.formId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="480" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canEdit(row.status)" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" :disabled="!canEdit(row.status)" @click="goToDesigner(row)">流程图</el-button>
            <el-button v-if="canCreateVersion(row.status)" link type="primary" @click="handleCreateVersion(row)">新版本</el-button>
            <el-button link type="success" :disabled="!canPublish(row.status)" @click="handlePublish(row)">发布</el-button>
            <el-button link type="danger" :disabled="normalizeStatus(row.status) !== 'published'" @click="handleUnpublish(row)">停用</el-button>
            <el-button link type="primary" :disabled="normalizeStatus(row.status) !== 'published'" @click="startProcess(row)">发起</el-button>
            <el-button link type="danger" :icon="Delete" :disabled="normalizeStatus(row.status) === 'published'" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无我的流程，点击右上角新建流程" />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingProcess ? '编辑流程' : '新建流程'" width="620px">
      <el-form label-position="top">
        <el-form-item label="流程编码">
          <el-input v-model="processForm.templateCode" :disabled="Boolean(editingProcess)" placeholder="例如 reimbursement_flow_v1" />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="processForm.templateName" placeholder="例如 费用报销流程" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="processForm.bizTypeId" clearable filterable placeholder="请选择负责范围内的业务类型" style="width: 100%">
            <el-option v-for="item in visibleBizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认表单">
          <el-select v-model="processForm.formId" clearable filterable placeholder="请选择已发布表单" style="width: 100%">
            <el-option v-for="item in forms" :key="item.id" :label="item.formName" :value="item.id" />
          </el-select>
          <div v-if="forms.length === 0" class="form-empty-hint">暂无已发布表单，可先在表单设计器创建并发布。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="dialogVisible = false">取消</el-button>
        <el-button round type="success" :loading="saving" @click="submitProcess">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { getBizTypes } from '@/api/bizType'
import { getPublishedForms } from '@/api/formDefinition'
import { createMyProcess, createMyProcessVersion, deleteMyProcess, getMyProcesses, publishMyProcess, unpublishMyProcess, updateMyProcess } from '@/api/myProcess'
import { useAuthStore } from '@/stores/auth'
import type { BizType, FormDefinition, ProcessTemplate, ProcessTemplatePayload } from '@/types/workflow'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const editingProcess = ref<ProcessTemplate | null>(null)
const processes = ref<ProcessTemplate[]>([])
const bizTypes = ref<BizType[]>([])
const forms = ref<FormDefinition[]>([])

const processForm = reactive<ProcessTemplatePayload>({
  templateCode: '',
  templateName: '',
  bizTypeId: null,
  formId: null,
  sourceType: 'manual',
  bpmnXml: '',
  nodeConfig: '{}',
  formBindConfig: '{}'
})

const managedBizTypeIds = computed(() => parseIdList(authStore.user?.managedBizTypeIds))
const visibleBizTypes = computed(() => {
  if (authStore.user?.systemRole === 'super_admin' || managedBizTypeIds.value.length === 0) {
    return bizTypes.value
  }
  return bizTypes.value.filter(item => managedBizTypeIds.value.includes(item.id))
})
const filteredProcesses = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return processes.value
  return processes.value.filter(item =>
    item.templateName.toLowerCase().includes(key) ||
    item.templateCode.toLowerCase().includes(key)
  )
})
const publishedCount = computed(() => processes.value.filter(item => normalizeStatus(item.status) === 'published').length)
const draftCount = computed(() => processes.value.filter(item => ['draft', 'reviewing'].includes(normalizeStatus(item.status))).length)
const marketCopyCount = computed(() => processes.value.filter(item => normalizeSource(item.sourceType) === 'market_copy').length)

onMounted(loadPageData)

async function loadPageData() {
  loading.value = true
  try {
    const [processList, bizTypeList, formList] = await Promise.all([
      getMyProcesses(),
      getBizTypes(),
      getPublishedForms()
    ])
    processes.value = processList
    bizTypes.value = bizTypeList
    forms.value = formList
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingProcess.value = null
  Object.assign(processForm, {
    templateCode: generateProcessCode(),
    templateName: '',
    bizTypeId: visibleBizTypes.value[0]?.id ?? null,
    formId: null,
    sourceType: 'manual',
    bpmnXml: '',
    nodeConfig: '{}',
    formBindConfig: '{}'
  })
  dialogVisible.value = true
}

function openEditDialog(row: ProcessTemplate) {
  if (!canEdit(row.status)) {
    ElMessage.warning('只有草稿或审核中的流程允许编辑')
    return
  }
  editingProcess.value = row
  Object.assign(processForm, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    bizTypeId: row.bizTypeId ?? null,
    formId: row.formId ?? null,
    sourceType: row.sourceType || 'manual',
    bpmnXml: row.bpmnXml || '',
    nodeConfig: row.nodeConfig || '{}',
    formBindConfig: row.formBindConfig || '{}'
  })
  dialogVisible.value = true
}

async function submitProcess() {
  if (!processForm.templateName?.trim()) {
    ElMessage.warning('请输入流程名称')
    return
  }
  if (!editingProcess.value && !processForm.templateCode?.trim()) {
    ElMessage.warning('请输入流程编码')
    return
  }

  saving.value = true
  try {
    if (editingProcess.value) {
      await updateMyProcess(editingProcess.value.id, {
        templateName: processForm.templateName,
        bizTypeId: processForm.bizTypeId,
        formId: processForm.formId,
        bpmnXml: processForm.bpmnXml,
        nodeConfig: processForm.nodeConfig,
        formBindConfig: processForm.formBindConfig
      })
      ElMessage.success('流程已更新')
    } else {
      const created = await createMyProcess(processForm)
      ElMessage.success('流程已创建')
      void router.push(`/process-designer?templateId=${created.id}&scope=my`)
    }
    dialogVisible.value = false
    await loadPageData()
  } finally {
    saving.value = false
  }
}

async function handlePublish(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(`确认发布“${row.templateName}”吗？发布后用户可发起该流程。`, '发布流程', { type: 'warning' })
    await publishMyProcess(row.id)
    ElMessage.success('流程发布成功')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '发布失败')
  }
}

async function handleUnpublish(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(`确认停用“${row.templateName}”v${row.version || 1}吗？历史实例仍保留该版本。`, '停用流程版本', { type: 'warning' })
    await unpublishMyProcess(row.id)
    ElMessage.success('流程版本已停用')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '停用失败')
  }
}

async function handleCreateVersion(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(`将基于“${row.templateName}”创建下一版草稿，是否继续？`, '创建新版本', { type: 'info' })
    const draft = await createMyProcessVersion(row.id)
    ElMessage.success(`已准备 v${draft.version || 1} 草稿`)
    await loadPageData()
    router.push(`/process-designer?templateId=${draft.id}&scope=my`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '创建新版本失败')
  }
}

async function handleDelete(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(
      `确认删除“${row.templateName}”v${row.version || 1}吗？仅从未发起过的流程版本可以删除。`,
      '删除流程版本',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
    await deleteMyProcess(row.id)
    ElMessage.success('流程版本已删除')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    // API 错误由统一请求拦截器提示，避免同一错误重复弹出。
  }
}

function goToDesigner(row: ProcessTemplate) {
  router.push(`/process-designer?templateId=${row.id}&scope=my`)
}

function startProcess(row: ProcessTemplate) {
  router.push(`/process/start-preview?templateId=${row.id}`)
}

function bizTypeName(id?: number | null) {
  return bizTypes.value.find(item => item.id === id)?.typeName || '未分类'
}

function formName(id?: number | null) {
  if (!id) return '未绑定'
  return forms.value.find(item => item.id === id)?.formName || '表单不存在'
}

function normalizeStatus(status?: string) {
  return (status || '').toLowerCase()
}

function normalizeSource(source?: string) {
  return (source || '').toLowerCase()
}

function canEdit(status?: string) {
  return ['draft', 'reviewing'].includes(normalizeStatus(status))
}

function canPublish(status?: string) {
  return ['draft', 'reviewing'].includes(normalizeStatus(status))
}

function canCreateVersion(status?: string) {
  return ['published', 'disabled'].includes(normalizeStatus(status))
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    reviewing: '审核中',
    published: '已发布',
    disabled: '已停用'
  }
  return map[normalizeStatus(status)] || status || '-'
}

function statusTagType(status?: string) {
  const normalized = normalizeStatus(status)
  if (normalized === 'published') return 'success'
  if (normalized === 'reviewing') return 'warning'
  if (normalized === 'disabled') return 'info'
  return ''
}

function sourceTypeLabel(sourceType?: string) {
  const map: Record<string, string> = {
    ai_generated: 'AI生成',
    manual: '手动创建',
    market_copy: '市场复制',
    fragment_combo: '片段组合'
  }
  return map[normalizeSource(sourceType)] || sourceType || '-'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function generateProcessCode() {
  return `my_flow_${Date.now()}`
}

function parseIdList(value?: string | null) {
  if (!value) return []
  return value
    .replace('[', '')
    .replace(']', '')
    .replace(/"/g, '')
    .split(',')
    .map((item: string) => Number(item.trim()))
    .filter(Number.isFinite)
}
</script>

<style scoped>
.my-process-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head,
.table-panel,
.stat-card {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border-radius: 18px;
}

.page-head h1 {
  margin: 10px 0 6px;
  font-size: 28px;
}

.page-head p,
.panel-title-row p,
.form-empty-hint {
  margin: 0;
  color: var(--muted);
}

.head-actions,
.panel-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  padding: 18px;
  border-radius: 16px;
}

.stat-card span {
  display: block;
  font-size: 26px;
  font-weight: 800;
}

.stat-card p {
  margin: 6px 0 0;
  color: var(--muted);
}

.table-panel {
  padding: 18px;
  border-radius: 18px;
}

.panel-title-row {
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-title-row h2 {
  margin: 0;
  font-size: 18px;
}

.search-input {
  width: 260px;
}

.soft-table {
  border-radius: 12px;
  overflow: hidden;
}

.form-empty-hint {
  margin-top: 8px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .page-head,
  .panel-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-input {
    width: 100%;
  }
}
</style>
