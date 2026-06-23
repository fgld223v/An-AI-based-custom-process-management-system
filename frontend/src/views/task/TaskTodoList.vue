<template>
  <div class="task-page">
    <section class="page-head">
      <div>
        <h1>待办任务</h1>
        <p>处理待审批的任务。数据来源：Flowable ACT_RU_TASK。</p>
      </div>
    </section>

    <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />

    <section class="table-panel" v-loading="loading">
      <el-table :data="tasks" border empty-text="暂无待办任务" @row-click="goDetail" style="cursor:pointer">
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <el-table-column prop="instanceTitle" label="流程实例" min-width="180" />
        <el-table-column prop="instanceCode" label="实例编号" min-width="160" />
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
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="warning" size="small" effect="plain">{{ row.status === 'active' ? '待处理' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
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
const loading = ref(false)
const message = ref('')
const tasks = ref<TaskItem[]>([])

onMounted(() => loadTasks())

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

function goDetail(row: TaskItem) {
  router.push(`/tasks/${row.taskId}`)
}

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
