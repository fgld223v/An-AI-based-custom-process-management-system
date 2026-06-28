<template>
  <!-- 我的申请页面：查看发起的流程草稿、已提交、运行中与已完成申请 -->
  <div class="instance-page">
    <!-- 页面标题区：标题说明 + 刷新按钮 -->
    <section class="page-head">
      <div>
        <h1>我的申请</h1>
        <p>查看我发起的流程草稿、已提交、运行中与已完成申请，可继续编辑草稿或进入详情追踪处理进度。</p>
      </div>
      <el-button round type="success" :icon="Refresh" :loading="loading" @click="loadInstances">刷新</el-button>
    </section>

    <!-- 查询筛选面板：关键词、状态、流程模板 -->
    <section class="query-panel">
      <el-form :inline="true" :model="query" class="query-form">
        <!-- 关键词搜索（实例标题/编号） -->
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="实例标题 / 编号" @keyup.enter="loadInstances" />
        </el-form-item>
        <!-- 状态筛选 -->
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="草稿" value="draft" />
            <el-option label="已提交" value="submitted" />
            <el-option label="流程运行中" value="running" />
          </el-select>
        </el-form-item>
        <!-- 流程模板筛选 -->
        <el-form-item label="流程模板">
          <el-select v-model="query.templateId" clearable filterable placeholder="全部模板" style="width: 220px">
            <el-option v-for="item in templates" :key="item.id" :label="item.templateName" :value="item.id" />
          </el-select>
        </el-form-item>
        <!-- 查询与重置按钮 -->
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadInstances">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 实例列表表格 -->
    <section class="table-panel">
      <!-- 错误提示 -->
      <el-alert v-if="message" class="stage-alert" type="warning" :closable="false" show-icon :title="message" />
      <el-table v-loading="loading" :data="instances" border empty-text="暂无申请记录">
        <!-- 实例编号 -->
        <el-table-column prop="instanceCode" label="实例编号" min-width="180" />
        <!-- 实例标题 -->
        <el-table-column prop="instanceTitle" label="实例标题" min-width="220" />
        <!-- 流程模板名称 -->
        <el-table-column label="流程模板" min-width="180">
          <template #default="{ row }">{{ templateLabel(row.templateId) }}</template>
        </el-table-column>
        <!-- 实例状态标签 -->
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 当前审批节点 -->
        <el-table-column label="当前节点" min-width="160">
          <template #default="{ row }">{{ row.currentNodeName || row.currentNodeKey || '-' }}</template>
        </el-table-column>
        <!-- 创建时间 -->
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <!-- 更新时间 -->
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <!-- 操作：查看详情 / 继续编辑草稿 -->
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
/** 表格加载状态 */
const loading = ref(false)
/** 错误消息 */
const message = ref('')
/** 流程实例列表 */
const instances = ref<ProcessInstance[]>([])
/** 流程模板列表（用于筛选下拉框） */
const templates = ref<ProcessTemplate[]>([])
/** 查询条件 */
const query = reactive<{ keyword: string; status: string; templateId: number | null }>({
  keyword: '',
  status: '',
  templateId: null
})

/** 页面挂载时并行加载模板和实例列表 */
onMounted(async () => {
  await Promise.all([loadTemplates(), loadInstances()])
})

/** 加载流程模板列表，用于筛选下拉 */
async function loadTemplates() {
  try {
    templates.value = await getProcessTemplates()
  } catch {
    ElMessage.warning('流程模板加载失败，请检查后端服务。')
  }
}

/** 根据当前查询条件加载流程实例列表 */
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

/** 重置查询条件并重新加载 */
function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.templateId = null
  loadInstances()
}

/** 跳转到实例详情页 */
function viewDetail(id: number) {
  router.push(`/process/instances/${id}`)
}

/** 继续编辑草稿实例，跳转到流程发起预览页 */
function continueEdit(id: number) {
  router.push(`/process/start-preview?instanceId=${id}`)
}

/** 根据模板 ID 查找模板名称，用于表格展示 */
function templateLabel(templateId?: number) {
  const template = templates.value.find((item) => item.id === templateId)
  return template ? `${template.templateName} / ${template.id}` : templateId || '-'
}

/** 将实例状态码转为中文标签 */
function statusLabel(status?: string) {
  if (status === 'draft') return '草稿'
  if (status === 'submitted') return '已提交，待启动流程引擎'
  if (status === 'running') return '流程运行中'
  return status || '-'
}

/** 根据状态返回 Element UI 标签类型 */
function statusTagType(status?: string) {
  if (status === 'draft') return 'info'
  if (status === 'submitted') return 'warning'
  if (status === 'running') return 'success'
  return 'info'
}

/**
 * 标准化错误信息
 * @param error - 原始错误对象
 * @param fallback - 兜底提示文案
 * @returns 可读的错误消息字符串
 */
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
