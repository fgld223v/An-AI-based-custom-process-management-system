<template>
  <header class="topbar">
    <div class="topbar-left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>PROCESS OS</el-breadcrumb-item>
        <el-breadcrumb-item>{{ route.meta.group || '工作区' }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="flow-title" v-if="templateStore.currentTemplate">
        <span>{{ templateStore.currentFlowName }}</span>
        <el-tag size="small" effect="plain" type="success">编辑中</el-tag>
      </div>
    </div>

    <div class="topbar-actions">
      <el-button round :icon="MagicStick" @click="showComingSoon">AI助手</el-button>
      <el-popover
        placement="bottom-end"
        trigger="click"
        width="360"
        popper-class="notification-popover"
        @show="loadNotificationSnapshot"
      >
        <template #reference>
          <el-badge :is-dot="unreadCount > 0" class="notification-badge">
            <el-button circle :icon="Bell" aria-label="通知中心" />
          </el-badge>
        </template>
        <div class="notification-dropdown">
          <div class="notification-dropdown-head">
            <strong>通知中心</strong>
            <el-button text type="primary" @click="goNotifications">查看全部</el-button>
          </div>
          <div v-if="notificationLoading" class="notification-empty">加载中...</div>
          <div v-else-if="latestNotifications.length === 0" class="notification-empty">暂无未读通知</div>
          <template v-else>
            <button
              v-for="item in latestNotifications"
              :key="item.id"
              class="notification-item"
              type="button"
              @click="openNotification(item)"
            >
              <span class="notification-dot" />
              <span class="notification-main">
                <span class="notification-title">{{ item.title }}</span>
                <span class="notification-content">{{ item.content || typeLabel(item.type) }}</span>
              </span>
              <el-tag size="small" :type="typeTag(item.type)" effect="plain">{{ typeLabel(item.type) }}</el-tag>
            </button>
          </template>
        </div>
      </el-popover>
      <el-dropdown>
        <div class="user-chip">
          <el-avatar :size="32">{{ authStore.username.slice(0, 1) }}</el-avatar>
          <span>{{ authStore.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/settings')">账号设置</el-dropdown-item>
            <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell, MagicStick } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTemplateStore } from '@/stores/template'
import { showComingSoon } from '@/utils/feedback'
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from '@/api/notification'
import type { NotificationItem } from '@/types/workflow'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const templateStore = useTemplateStore()
const unreadCount = ref(0)
const notificationLoading = ref(false)
const latestNotifications = ref<NotificationItem[]>([])
const websocketReady = ref(false)
let pollTimer: number | undefined
let socket: WebSocket | null = null
let socketFailed = false

const currentTitle = computed(() => {
  if (templateStore.currentTemplate) {
    return templateStore.currentTemplate.templateName
  }
  return (route.meta.title as string) || '工作台'
})

onMounted(() => {
  loadNotificationSnapshot()
  connectNotificationSocket()
  pollTimer = window.setInterval(() => {
    if (!websocketReady.value) {
      loadNotificationSnapshot(true)
    }
  }, 30000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
  }
  if (socket) {
    socket.close()
    socket = null
  }
})

function logout() {
  authStore.logout()
  router.replace('/login')
}

async function loadNotificationSnapshot(silent = false) {
  if (!silent) {
    notificationLoading.value = true
  }
  try {
    const [countResult, unreadItems] = await Promise.all([
      getUnreadNotificationCount(),
      getNotifications({ isRead: false })
    ])
    unreadCount.value = countResult?.count || 0
    latestNotifications.value = unreadItems.slice(0, 5)
  } catch {
    if (!silent) {
      latestNotifications.value = []
    }
  } finally {
    notificationLoading.value = false
  }
}

function connectNotificationSocket() {
  if (!('WebSocket' in window) || socketFailed) {
    return
  }
  try {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname || 'localhost'
    socket = new WebSocket(`${protocol}//${host}:8080/ws/notifications`)
    socket.onopen = () => {
      websocketReady.value = true
    }
    socket.onmessage = () => {
      loadNotificationSnapshot(true)
    }
    socket.onclose = () => {
      websocketReady.value = false
    }
    socket.onerror = () => {
      websocketReady.value = false
      socketFailed = true
      socket?.close()
    }
  } catch {
    websocketReady.value = false
    socketFailed = true
  }
}

async function openNotification(item: NotificationItem) {
  try {
    await markNotificationRead(item.id)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    latestNotifications.value = latestNotifications.value.filter((notification) => notification.id !== item.id)
  } catch {
    // 跳转优先，已读失败不阻塞查看目标。
  }
  const target = resolveTarget(item)
  if (target) {
    router.push(target)
  }
}

function goNotifications() {
  router.push('/notifications')
}

function resolveTarget(item: NotificationItem) {
  if (item.targetUrl) return item.targetUrl
  if (item.targetType?.startsWith('flowable_task:')) {
    const taskId = item.targetType.slice('flowable_task:'.length)
    return taskId ? `/tasks/${taskId}` : ''
  }
  if (item.targetType === 'process_instance' && item.targetId) {
    return `/process/instances/${item.targetId}`
  }
  return ''
}

function typeLabel(type?: string) {
  const map: Record<string, string> = {
    task_remind: '任务提醒',
    timeout_warning: '超时预警',
    approval_result: '审批结果',
    system_notice: '系统通知'
  }
  return map[type || ''] || type || '-'
}

function typeTag(type?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'timeout_warning') return 'danger'
  if (type === 'approval_result') return 'success'
  if (type === 'task_remind') return 'warning'
  return 'info'
}
</script>

<style scoped>
:global(.notification-popover) {
  max-width: calc(100vw - 24px);
}

.notification-badge {
  display: inline-flex;
}

.notification-dropdown {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-dropdown-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 4px;
}

.notification-empty {
  padding: 20px 6px;
  color: var(--muted);
  text-align: center;
}

.notification-item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 8px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--text);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.notification-item:hover {
  border-color: var(--line);
  background: rgba(14, 165, 139, 0.08);
}

.notification-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #ef4444;
}

.notification-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.notification-title,
.notification-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-title {
  font-weight: 700;
}

.notification-content {
  color: var(--muted);
  font-size: 12px;
}
</style>
