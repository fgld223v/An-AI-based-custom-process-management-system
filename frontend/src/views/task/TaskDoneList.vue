<template>
  <div class="task-page">
    <section class="page-head">
      <div>
        <h1>已办任务</h1>
        <p>已完成的历史任务。数据来源：Flowable ACT_HI_TASKINST。</p>
      </div>
      <div class="head-actions">
        <el-button round @click="router.push('/tasks/todo')">← 返回待办</el-button>
      </div>
    </section>

    <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />

    <section class="table-panel" v-loading="loading">
      <el-table :data="tasks" border empty-text="暂无已办任务" @row-click="goDetail" style="cursor:pointer">
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <el-table-column prop="instanceTitle" label="流程实例" min-width="180" />
        <el-table-column prop="instanceCode" label="实例编号" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="success" size="small" effect="plain">{{ row.status === 'completed' ? '已完成' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="完成时间" min-width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="goDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDoneTasks } from '@/api/task'
import type { TaskItem } from '@/types/workflow'

const router = useRouter()
const loading = ref(false)
const message = ref('')
const tasks = ref<TaskItem[]>([])

onMounted(() => loadTasks())

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await getDoneTasks()
  } catch (error) {
    message.value = normalizeError(error, '已办任务加载失败。')
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
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h1 { margin: 10px 0 6px; font-size: 28px; }
.page-head p { margin: 0; color: var(--muted); }
.head-actions { display: flex; align-items: center; gap: 10px; }
</style>
