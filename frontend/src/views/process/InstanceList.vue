<template>
  <div class="instance-page">
    <section class="page-head">
      <div>
        <el-tag type="success" effect="plain">轻量实例</el-tag>
        <h1>流程实例</h1>
        <p>查看通过发起预览创建的草稿与已提交实例。当前阶段仅展示业务表单数据，不启动流程引擎。</p>
      </div>
      <el-button round type="success" :icon="Refresh" :loading="loading" @click="loadInstances">刷新</el-button>
    </section>

    <section class="query-panel">
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="实例标题 / 编号" @keyup.enter="loadInstances" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="草稿" value="draft" />
            <el-option label="已提交" value="submitted" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程模板">
          <el-select v-model="query.templateId" clearable filterable placeholder="全部模板" style="width: 220px">
            <el-option v-for="item in templates" :key="item.id" :label="item.templateName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadInstances">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-alert v-if="message" class="stage-alert" type="warning" :closable="false" show-icon :title="message" />
      <el-table v-loading="loading" :data="instances" border empty-text="暂无流程实例">
        <el-table-column prop="instanceCode" label="实例编号" min-width="180" />
        <el-table-column prop="instanceTitle" label="实例标题" min-width="220" />
        <el-table-column label="流程模板" min-width="180">
          <template #default="{ row }">{{ templateLabel(row.templateId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'draft' ? 'info' : 'success'" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="160">
          <template #default="{ row }">{{ row.currentNodeName || row.currentNodeKey || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewDetail(row.id)">查看详情</el-button>
            <el-button v-if="row.status === 'draft'" text type="success" @click="continueEdit(row.id)">继续编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getProcessInstanceList } from '@/api/processInstance'
import { getProcessTemplates } from '@/api/processTemplate'
import type { ProcessInstance, ProcessTemplate } from '@/types/workflow'

const router = useRouter()
const loading = ref(false)
const message = ref('')
const instances = ref<ProcessInstance[]>([])
const templates = ref<ProcessTemplate[]>([])
const query = reactive<{ keyword: string; status: string; templateId: number | null }>({
  keyword: '',
  status: '',
  templateId: null
})

onMounted(async () => {
  await Promise.all([loadTemplates(), loadInstances()])
})

async function loadTemplates() {
  try {
    templates.value = await getProcessTemplates()
  } catch {
    ElMessage.warning('流程模板加载失败，请检查后端服务。')
  }
}

async function loadInstances() {
  loading.value = true
  message.value = ''
  try {
    instances.value = await getProcessInstanceList({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
      templateId: query.templateId || undefined
    })
  } catch (error) {
    message.value = normalizeError(error, '流程实例加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.templateId = null
  loadInstances()
}

function viewDetail(id: number) {
  router.push(`/process/instances/${id}`)
}

function continueEdit(id: number) {
  router.push(`/process/start-preview?instanceId=${id}`)
}

function templateLabel(templateId?: number) {
  const template = templates.value.find((item) => item.id === templateId)
  return template ? `${template.templateName} / ${template.id}` : templateId || '-'
}

function statusLabel(status?: string) {
  if (status === 'draft') return '草稿'
  if (status === 'submitted') return '已提交'
  return status || '-'
}

function normalizeError(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  return fallback
}
</script>

<style scoped>
.instance-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head,
.query-panel,
.table-panel {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
}

.page-head h1 {
  margin: 10px 0 6px;
  font-size: 28px;
}

.page-head p {
  margin: 0;
  color: var(--muted);
}

.query-panel,
.table-panel {
  padding: 18px;
}

.query-form {
  row-gap: 8px;
}

.stage-alert {
  margin-bottom: 12px;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
