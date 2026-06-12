<template>
  <div class="template-page">
    <section class="template-hero">
      <div>
        <el-tag type="success" effect="plain">流程模板管理</el-tag>
        <h1>流程模板库</h1>
        <p>集中管理流程模板，绑定业务类型和表单，完成发布后可上架到模板市场。</p>
      </div>
      <el-button type="success" round :icon="Plus" @click="openCreateDialog">新增模板</el-button>
    </section>

    <section class="template-stats">
      <div class="stat-card">
        <span>{{ templates.length }}</span>
        <p>流程模板数</p>
      </div>
      <div class="stat-card">
        <span>{{ publishedCount }}</span>
        <p>已发布模板</p>
      </div>
      <div class="stat-card">
        <span>{{ draftCount }}</span>
        <p>草稿/审核中</p>
      </div>
      <div class="stat-card">
        <span>{{ forms.length }}</span>
        <p>可绑定表单</p>
      </div>
    </section>

    <section class="template-panel">
      <div class="panel-title-row">
        <div>
          <h2>模板列表</h2>
          <p>数据来自后端 GET /api/process-templates</p>
        </div>
        <el-button round :icon="Refresh" @click="loadPageData">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="templates" class="soft-table" row-key="id">
        <el-table-column prop="templateName" label="模板名称" min-width="180" />
        <el-table-column prop="templateCode" label="模板编码" min-width="160" />
        <el-table-column label="业务类型" min-width="140">
          <template #default="{ row }">{{ bizTypeName(row.bizTypeId) }}</template>
        </el-table-column>
        <el-table-column label="绑定表单" min-width="150">
          <template #default="{ row }">{{ formName(row.formId) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">{{ templateStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Flowable部署" min-width="140">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.flowableProcessDefinitionId"
              placement="top"
              :content="`deploymentId: ${row.flowableDeploymentId || '-'}；processDefinitionId: ${row.flowableProcessDefinitionId}`"
            >
              <el-tag type="success" effect="plain">已部署</el-tag>
            </el-tooltip>
            <el-tag v-else type="info" effect="plain">未部署</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="市场" width="80">
          <template #default="{ row }">
            <el-tag v-if="getMarketItem(row.id)" type="warning" size="small" effect="plain">已上架</el-tag>
            <span v-else style="color:var(--muted);font-size:12px">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canEdit(row.status)" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="success" :disabled="!canPublish(row.status)" @click="handlePublish(row)">发布</el-button>
            <el-button link type="danger" :disabled="normalizeStatus(row.status) !== 'published'" @click="handleUnpublish(row)">撤回</el-button>
            <el-button link type="primary" @click="openPreviewDialog(row)">预览</el-button>
            <el-button link type="warning" :disabled="normalizeStatus(row.status) !== 'published' || Boolean(getMarketItem(row.id))" @click="openMarketDialog(row)">上架</el-button>
            <el-button v-if="getMarketItem(row.id)" link type="danger" @click="handleWithdraw(row)">下架</el-button>
            <el-button link type="primary" @click="goToDesigner(row)">流程图</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无流程模板，点击右上角新增模板" />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="templateDialogVisible" :title="editingTemplate ? '编辑模板' : '新增模板'" width="620px">
      <el-form label-position="top">
        <el-form-item label="模板编码">
          <el-input v-model="templateForm.templateCode" :disabled="Boolean(editingTemplate)" placeholder="例如 leave_approval_v1" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="templateForm.templateName" placeholder="例如 请假审批流程" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="templateForm.bizTypeId" clearable placeholder="请选择业务类型" style="width: 100%">
            <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定表单">
          <el-select v-model="templateForm.formId" clearable placeholder="请选择已发布表单" style="width: 100%">
            <el-option v-for="item in forms" :key="item.id" :label="item.formName" :value="item.id" />
          </el-select>
          <div v-if="forms.length === 0" class="form-empty-hint">暂无已发布表单，可先在表单设计器中创建并发布表单。</div>
        </el-form-item>
        <el-form-item label="BPMN XML">
          <el-input v-model="templateForm.bpmnXml" type="textarea" :rows="4" placeholder="可后续在流程编辑器中完善" />
        </el-form-item>
        <el-form-item label="节点配置 JSON">
          <el-input v-model="templateForm.nodeConfig" type="textarea" :rows="3" placeholder="{}" />
        </el-form-item>
        <el-form-item label="表单绑定配置 JSON">
          <el-input v-model="templateForm.formBindConfig" type="textarea" :rows="3" placeholder="{}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="templateDialogVisible = false">取消</el-button>
        <el-button round type="success" :loading="saving" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="预览绑定表单" width="760px">
      <el-alert
        v-if="previewMessage"
        type="warning"
        :closable="false"
        show-icon
        :title="previewMessage"
      />
      <template v-if="previewBinding">
        <div class="preview-summary">
          <span>{{ previewBinding.template.templateName }}</span>
          <strong>{{ previewBinding.form.formName }}</strong>
        </div>
        <DynamicFormRenderer
          ref="previewRendererRef"
          v-model="previewData"
          :form-schema="previewBinding.form.formSchema"
          :field-list="previewBinding.form.fieldList"
        />
        <div class="preview-actions">
          <el-button round type="success" @click="previewJsonVisible = true">预览 JSON</el-button>
          <el-button round @click="validatePreviewForm">校验表单</el-button>
        </div>
        <pre v-if="previewJsonVisible">{{ formattedPreviewData }}</pre>
      </template>
    </el-dialog>

    <el-dialog v-model="marketDialogVisible" title="上架模板市场" width="520px">
      <el-form label-position="top">
        <el-form-item label="市场标题">
          <el-input v-model="marketForm.title" />
        </el-form-item>
        <el-form-item label="模板说明">
          <el-input v-model="marketForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="封面地址">
          <el-input v-model="marketForm.coverUrl" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="标签 JSON">
          <el-input v-model="marketForm.tags" placeholder='例如 ["审批","人事"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="marketDialogVisible = false">取消</el-button>
        <el-button round type="success" :loading="saving" @click="submitMarket">确认上架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { getBizTypes } from '@/api/bizType'
import { getPublishedForms } from '@/api/formDefinition'
import {
  createProcessTemplate,
  getProcessTemplateBoundForm,
  getProcessTemplates,
  publishProcessTemplate,
  unpublishProcessTemplate,
  updateProcessTemplate
} from '@/api/processTemplate'
import { getTemplateMarketList, publishTemplateToMarket, withdrawFromMarket } from '@/api/templateMarket'
import type { BizType, FormDefinition, ProcessTemplate, ProcessTemplatePayload, TemplateFormBinding, TemplateMarketItem } from '@/types/workflow'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const templates = ref<ProcessTemplate[]>([])
const bizTypes = ref<BizType[]>([])
const forms = ref<FormDefinition[]>([])
const marketItems = ref<TemplateMarketItem[]>([])
const templateDialogVisible = ref(false)
const marketDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const previewJsonVisible = ref(false)
const editingTemplate = ref<ProcessTemplate | null>(null)
const marketTemplate = ref<ProcessTemplate | null>(null)
const previewBinding = ref<TemplateFormBinding | null>(null)
const previewMessage = ref('')
const previewData = ref<Record<string, unknown>>({})
const previewRendererRef = ref<InstanceType<typeof DynamicFormRenderer> | null>(null)

const templateForm = reactive<ProcessTemplatePayload>({
  templateCode: '',
  templateName: '',
  bizTypeId: null,
  formId: null,
  sourceType: 'manual',
  bpmnXml: '',
  nodeConfig: '{}',
  formBindConfig: '{}',
  createdBy: 1
})

const marketForm = reactive({
  title: '',
  description: '',
  coverUrl: '',
  tags: '[]'
})

const publishedCount = computed(() => templates.value.filter((item) => normalizeStatus(item.status) === 'published').length)
const draftCount = computed(() => templates.value.filter((item) => ['draft', 'reviewing'].includes(normalizeStatus(item.status))).length)
const formattedPreviewData = computed(() => JSON.stringify(previewData.value, null, 2))

onMounted(loadPageData)

async function loadPageData() {
  loading.value = true
  try {
    const [templateList, bizTypeList, formList, marketList] = await Promise.all([
      getProcessTemplates(),
      getBizTypes(),
      getPublishedForms(),
      getTemplateMarketList()
    ])
    templates.value = templateList
    bizTypes.value = bizTypeList
    forms.value = formList
    marketItems.value = marketList
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingTemplate.value = null
  Object.assign(templateForm, {
    templateCode: '',
    templateName: '',
    bizTypeId: null,
    formId: null,
    sourceType: 'manual',
    bpmnXml: '',
    nodeConfig: '{}',
    formBindConfig: '{}',
    createdBy: 1
  })
  templateDialogVisible.value = true
}

function openEditDialog(row: ProcessTemplate) {
  if (!canEdit(row.status)) {
    ElMessage.warning('只有草稿或审核中的模板允许编辑')
    return
  }
  editingTemplate.value = row
  Object.assign(templateForm, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    bizTypeId: row.bizTypeId ?? null,
    formId: row.formId ?? null,
    sourceType: row.sourceType || 'manual',
    bpmnXml: row.bpmnXml || '',
    nodeConfig: row.nodeConfig || '{}',
    formBindConfig: row.formBindConfig || '{}',
    createdBy: row.createdBy || 1
  })
  templateDialogVisible.value = true
}

async function submitTemplate() {
  if (!templateForm.templateName?.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!editingTemplate.value && !templateForm.templateCode?.trim()) {
    ElMessage.warning('请输入模板编码')
    return
  }

  saving.value = true
  try {
    if (editingTemplate.value) {
      await updateProcessTemplate(editingTemplate.value.id, {
        templateName: templateForm.templateName,
        bizTypeId: templateForm.bizTypeId,
        formId: templateForm.formId,
        bpmnXml: templateForm.bpmnXml,
        nodeConfig: templateForm.nodeConfig,
        formBindConfig: templateForm.formBindConfig
      })
      ElMessage.success('模板已更新')
    } else {
      await createProcessTemplate(templateForm)
      ElMessage.success('模板已创建')
    }
    templateDialogVisible.value = false
    await loadPageData()
  } finally {
    saving.value = false
  }
}

async function handlePublish(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(`确认发布「${row.templateName}」吗？发布时会部署 BPMN 到 Flowable。`, '发布模板', { type: 'warning' })
    await publishProcessTemplate(row.id)
    ElMessage.success('模板发布成功，并已部署到 Flowable 流程引擎。')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '模板发布失败：Flowable 部署失败。')
  }
}

async function openPreviewDialog(row: ProcessTemplate) {
  previewDialogVisible.value = true
  previewBinding.value = null
  previewMessage.value = ''
  previewData.value = {}
  previewJsonVisible.value = false
  try {
    const result = await getProcessTemplateBoundForm(row.id)
    if (result) {
      previewBinding.value = result
    }
  } catch (error) {
    previewMessage.value = error instanceof Error ? error.message : '当前流程模板未绑定表单。'
  }
}

function validatePreviewForm() {
  const passed = previewRendererRef.value?.validate() ?? false
  if (passed) {
    ElMessage.success('表单校验通过')
  } else {
    ElMessage.warning('表单校验未通过，请检查必填项或数字字段')
  }
}

function openMarketDialog(row: ProcessTemplate) {
  marketTemplate.value = row
  marketForm.title = row.templateName
  marketForm.description = `${row.templateName} 流程模板`
  marketForm.coverUrl = ''
  marketForm.tags = '[]'
  marketDialogVisible.value = true
}

async function submitMarket() {
  if (!marketTemplate.value) return
  saving.value = true
  try {
    await publishTemplateToMarket({
      templateId: marketTemplate.value.id,
      publisherId: 1,
      title: marketForm.title,
      description: marketForm.description,
      coverUrl: marketForm.coverUrl,
      tags: marketForm.tags
    })
    ElMessage.success('模板已上架市场')
    marketDialogVisible.value = false
    await loadPageData()
  } finally {
    saving.value = false
  }
}

function getMarketItem(templateId: number): TemplateMarketItem | undefined {
  return marketItems.value.find(m => m.sourceId === templateId)
}

async function handleUnpublish(row: ProcessTemplate) {
  try {
    await ElMessageBox.confirm(`确认撤回「${row.templateName}」吗？模板将回到草稿状态，Flowable 部署信息将被清除。`, '撤回模板', { type: 'warning' })
    await unpublishProcessTemplate(row.id)
    ElMessage.success('模板已撤回为草稿')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '撤回失败')
  }
}

async function handleWithdraw(row: ProcessTemplate) {
  const marketItem = getMarketItem(row.id)
  if (!marketItem) return
  try {
    await ElMessageBox.confirm(`确认从市场下架「${row.templateName}」吗？`, '下架模板', { type: 'warning' })
    await withdrawFromMarket(marketItem.id)
    ElMessage.success('已从市场下架')
    await loadPageData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '下架失败')
  }
}

function goToDesigner(row: ProcessTemplate) {
  router.push(`/process-designer?templateId=${row.id}`)
}

function bizTypeName(id?: number | null) {
  return bizTypes.value.find((item) => item.id === id)?.typeName || '未分类'
}

function formName(id?: number | null) {
  if (!id) return '未绑定'
  return forms.value.find((item) => item.id === id)?.formName || '表单不存在'
}

function normalizeStatus(status?: string) {
  return (status || '').toLowerCase()
}

function canEdit(status?: string) {
  return ['draft', 'reviewing'].includes(normalizeStatus(status))
}

function canPublish(status?: string) {
  return ['draft', 'reviewing'].includes(normalizeStatus(status))
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

function sourceTypeLabel(sourceType?: string) {
  const map: Record<string, string> = {
    ai_generated: 'AI生成',
    manual: '手动创建',
    market_copy: '市场复制',
    fragment_combo: '片段组合'
  }
  return map[(sourceType || '').toLowerCase()] || sourceType || '-'
}

function statusTagType(status?: string) {
  const normalized = normalizeStatus(status)
  if (normalized === 'published') return 'success'
  if (normalized === 'reviewing') return 'warning'
  if (normalized === 'disabled') return 'info'
  return ''
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<style scoped>
.template-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.template-hero,
.template-panel,
.stat-card {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}

.template-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 26px;
  border-radius: 24px;
}

.template-hero h1 {
  margin: 10px 0 6px;
  font-size: 30px;
}

.template-hero p,
.form-empty-hint {
  margin: 0;
  color: var(--muted);
}

.template-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  padding: 20px;
  border-radius: 18px;
}

.stat-card span {
  display: block;
  font-size: 28px;
  font-weight: 800;
}

.stat-card p {
  margin: 6px 0 0;
  color: var(--muted);
}

.template-panel {
  padding: 22px;
  border-radius: 22px;
}

.soft-table {
  border-radius: 16px;
  overflow: hidden;
}

.form-empty-hint {
  margin-top: 8px;
  font-size: 13px;
}

.preview-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.preview-summary span {
  color: var(--muted);
}

.preview-actions {
  display: flex;
  gap: 12px;
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

@media (max-width: 960px) {
  .template-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .template-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
