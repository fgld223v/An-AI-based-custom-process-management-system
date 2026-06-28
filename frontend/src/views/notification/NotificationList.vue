<template>
  <!-- 站内通知页面：查看流程超时、审批结果与系统提醒 -->
  <div class="notification-page">
    <!-- 页面标题区 -->
    <section class="page-head">
      <div>
        <h1>站内通知</h1>
        <p>查看流程超时、审批结果与系统提醒，支持已读/未读筛选和跳转目标实例。</p>
      </div>
      <el-button round type="success" :icon="Refresh" :loading="loading" @click="loadNotifications">刷新</el-button>
    </section>

    <!-- 查询筛选面板 -->
    <section class="query-panel">
      <el-form :inline="true" :model="query" class="query-form">
        <!-- 关键字搜索 -->
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="标题 / 内容" @keyup.enter="loadNotifications" />
        </el-form-item>
        <!-- 通知类型筛选 -->
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable placeholder="全部类型" style="width: 170px">
            <el-option label="任务提醒" value="task_remind" />
            <el-option label="超时预警" value="timeout_warning" />
            <el-option label="审批结果" value="approval_result" />
            <el-option label="流程完成" value="process_completed" />
            <el-option label="系统通知" value="system_notice" />
          </el-select>
        </el-form-item>
        <!-- 已读/未读状态筛选 -->
        <el-form-item label="状态">
          <el-select v-model="query.readStatus" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="未读" value="unread" />
            <el-option label="已读" value="read" />
          </el-select>
        </el-form-item>
        <!-- 查询/重置按钮 -->
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadNotifications">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 通知列表表格 -->
    <section class="table-panel">
      <el-alert v-if="message" class="stage-alert" type="warning" :closable="false" show-icon :title="message" />
      <el-table
        v-loading="loading"
        :data="notifications"
        border
        empty-text="暂无通知"
        row-key="id"
        @row-click="openNotification"
      >
        <!-- 已读/未读状态 -->
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'info' : 'danger'" size="small" effect="plain">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 通知类型标签 -->
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" effect="plain">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 通知标题（未读加粗） -->
        <el-table-column label="标题" min-width="190">
          <template #default="{ row }">
            <div class="title-cell" :class="{ unread: !row.isRead }">{{ row.title }}</div>
          </template>
        </el-table-column>
        <!-- 通知内容（溢出省略） -->
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <!-- 创建时间 -->
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <!-- 操作：查看 / 标记已读/未读 -->
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="openNotification(row)">查看</el-button>
            <el-button text :type="row.isRead ? 'warning' : 'success'" @click.stop="toggleRead(row)">
              {{ row.isRead ? '标为未读' : '标为已读' }}
            </el-button>
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
import { getNotifications, markNotificationRead, markNotificationUnread } from '@/api/notification'
import type { NotificationItem } from '@/types/workflow'

const router = useRouter()
/** 表格加载状态 */
const loading = ref(false)
/** 错误消息提示 */
const message = ref('')
/** 通知列表 */
const notifications = ref<NotificationItem[]>([])
/** 查询条件 */
const query = reactive<{ keyword: string; type: string; readStatus: '' | 'read' | 'unread' }>({
  keyword: '',
  type: '',
  readStatus: ''
})

/** 页面挂载时加载通知列表 */
onMounted(() => loadNotifications())

/** 根据当前查询条件加载通知列表 */
async function loadNotifications() {
  loading.value = true
  message.value = ''
  try {
    notifications.value = await getNotifications({
      keyword: query.keyword.trim() || undefined,
      type: query.type || undefined,
      isRead: query.readStatus ? query.readStatus === 'read' : undefined
    })
  } catch (error) {
    message.value = normalizeError(error, '通知加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并重新加载 */
function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.readStatus = ''
  loadNotifications()
}

/**
 * 打开通知详情
 * 未读通知先标记为已读，然后根据 targetType 解析跳转目标路由
 */
async function openNotification(row: NotificationItem) {
  if (!row.isRead) {
    try {
      const updated = await markNotificationRead(row.id)
      replaceNotification(updated)
    } catch {
      // 跳转优先，不让已读状态失败阻塞查看目标。
    }
  }
  const target = resolveTarget(row)
  if (target) {
    router.push(target)
  }
}

/** 切换通知的已读/未读状态 */
async function toggleRead(row: NotificationItem) {
  try {
    const updated = row.isRead
      ? await markNotificationUnread(row.id)
      : await markNotificationRead(row.id)
    replaceNotification(updated)
    ElMessage.success(row.isRead ? '已标为未读' : '已标为已读')
  } catch (error) {
    ElMessage.error(normalizeError(error, '状态更新失败'))
  }
}

/** 在本地列表中替换更新后的通知对象 */
function replaceNotification(item: NotificationItem) {
  const index = notifications.value.findIndex((notification) => notification.id === item.id)
  if (index >= 0) {
    notifications.value[index] = item
  }
}

/**
 * 解析通知的跳转目标路由
 * 优先使用 targetUrl，其次根据 targetType 拼接路由
 */
function resolveTarget(row: NotificationItem) {
  if (row.targetUrl) return row.targetUrl
  if (row.targetType?.startsWith('flowable_task:')) {
    const taskId = row.targetType.slice('flowable_task:'.length)
    return taskId ? `/tasks/${taskId}` : ''
  }
  if (row.targetType === 'process_instance' && row.targetId) {
    return `/process/instances/${row.targetId}`
  }
  return ''
}

/** 通知类型转中文标签 */
function typeLabel(type?: string) {
  const map: Record<string, string> = {
    task_remind: '任务提醒',
    timeout_warning: '超时预警',
    approval_result: '审批结果',
    process_completed: '流程完成',
    system_notice: '系统通知'
  }
  return map[type || ''] || type || '-'
}

/** 通知类型对应的 Element UI Tag 类型 */
function typeTag(type?: string) {
  if (type === 'timeout_warning') return 'danger'
  if (type === 'approval_result') return 'success'
  if (type === 'process_completed') return 'success'
  if (type === 'task_remind') return 'warning'
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
.notification-page {
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

.title-cell {
  color: var(--text);
}

.title-cell.unread {
  font-weight: 700;
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
