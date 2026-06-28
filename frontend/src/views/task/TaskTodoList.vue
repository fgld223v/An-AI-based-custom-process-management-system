<template>
  <!-- 待办任务页面 -->
  <div class="task-page">
    <!-- 页面标题区 -->
    <section class="page-head">
      <div>
        <h1>待办任务</h1>
        <p>处理待审批的任务。数据来源：Flowable ACT_RU_TASK。</p>
      </div>
    </section>

    <!-- 错误/提示信息 -->
    <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />

    <!-- 待办任务表格 -->
    <section class="table-panel" v-loading="loading">
      <el-table :data="tasks" border empty-text="暂无待办任务" @row-click="goDetail" style="cursor:pointer">
        <!-- 任务名称列 -->
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <!-- 流程实例标题 -->
        <el-table-column prop="instanceTitle" label="流程实例" min-width="180" />
        <!-- 实例编号 -->
        <el-table-column prop="instanceCode" label="实例编号" min-width="160" />
        <!-- 审批方式列：会签/或签/单人 -->
        <el-table-column label="审批方式" width="100">
          <template #default="{ row }">
            <template v-if="row.approvalMode === 'ALL'">
              <el-tag type="primary" size="small" effect="plain">会签</el-tag>
            </template>
            <template v-else-if="row.approvalMode === 'ANY'">
              <el-tag type="success" size="small" effect="plain">或签</el-tag>
            </template>
            <template v-else>
              <el-tag type="info" size="small" effect="plain">单人</el-tag>
            </template>
          </template>
        </el-table-column>
        <!-- 审批进度列：显示完成数/总数和进度条 -->
        <el-table-column label="进度" min-width="140">
          <template #default="{ row }">
            <template v-if="row.approvalMode === 'ALL' || row.approvalMode === 'ANY'">
              <div class="approval-progress-cell">
                <span class="progress-text">
                  {{ row.nrOfCompletedInstances ?? 0 }}/{{ row.nrOfInstances ?? '?' }}
                </span>
                <el-progress
                  v-if="row.nrOfInstances && row.nrOfInstances > 0"
                  :percentage="Math.round(((row.nrOfCompletedInstances ?? 0) / row.nrOfInstances) * 100)"
                  :stroke-width="8"
                  :show-text="false"
                  style="width: 60px"
                />
              </div>
            </template>
            <template v-else>
              <span style="color: var(--muted)">—</span>
            </template>
          </template>
        </el-table-column>
        <!-- 任务状态列 -->
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="warning" size="small" effect="plain">{{ row.status === 'active' ? '待处理' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 创建时间 -->
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <!-- 操作列：点击处理跳转到任务详情 -->
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="goDetail(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyTasks } from '@/api/task'
import type { TaskItem } from '@/types/workflow'

const router = useRouter()
/** 表格加载状态 */
const loading = ref(false)
/** 错误消息提示 */
const message = ref('')
/** 待办任务列表 */
const tasks = ref<TaskItem[]>([])

/** 页面挂载时加载待办任务 */
onMounted(() => loadTasks())

/** 加载我的待办任务列表 */
async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await getMyTasks()
  } catch (error) {
    message.value = normalizeError(error, '待办任务加载失败。')
  } finally {
    loading.value = false
  }
}

/** 跳转到任务详情页 */
function goDetail(row: TaskItem) {
  router.push(`/tasks/${row.taskId}`)
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
.task-page { display: flex; flex-direction: column; gap: 18px; }
.page-head, .table-panel {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255,255,255,0.94);
  box-shadow: var(--shadow);
  padding: 24px;
}
.page-head h1 { margin: 10px 0 6px; font-size: 28px; }
.page-head p { margin: 0; color: var(--muted); }
.approval-progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 32px;
}
</style>
