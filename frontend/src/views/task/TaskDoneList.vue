<template>
  <!-- 已办任务页面 -->
  <div class="task-page">
    <!-- 页面标题区，包含返回待办按钮 -->
    <section class="page-head">
      <div>
        <h1>已办任务</h1>
        <p>已完成的历史任务。数据来源：Flowable ACT_HI_TASKINST。</p>
      </div>
      <div class="head-actions">
        <el-button round @click="router.push('/tasks/todo')">← 返回待办</el-button>
      </div>
    </section>

    <!-- 错误/提示信息 -->
    <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />

    <!-- 已办任务表格 -->
    <section class="table-panel" v-loading="loading">
      <el-table :data="tasks" border empty-text="暂无已办任务" @row-click="goDetail" style="cursor:pointer">
        <!-- 任务名称 -->
        <el-table-column prop="taskName" label="任务名称" min-width="140" />
        <!-- 流程实例标题 -->
        <el-table-column prop="instanceTitle" label="流程实例" min-width="180" />
        <!-- 实例编号 -->
        <el-table-column prop="instanceCode" label="实例编号" min-width="160" />
        <!-- 任务状态：已完成的用绿色标签 -->
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="success" size="small" effect="plain">{{ row.status === 'completed' ? '已完成' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 完成时间 -->
        <el-table-column prop="endTime" label="完成时间" min-width="170" />
        <!-- 操作列：查看已完成任务详情 -->
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
/** 表格加载状态 */
const loading = ref(false)
/** 错误消息提示 */
const message = ref('')
/** 已办任务列表 */
const tasks = ref<TaskItem[]>([])

/** 页面挂载时加载已办任务 */
onMounted(() => loadTasks())

/** 加载已办任务列表（历史任务） */
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
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h1 { margin: 10px 0 6px; font-size: 28px; }
.page-head p { margin: 0; color: var(--muted); }
.head-actions { display: flex; align-items: center; gap: 10px; }
</style>
