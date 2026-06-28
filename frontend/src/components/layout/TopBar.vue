<!--
  顶栏导航组件。

  功能：
  - 面包屑导航（PROCESS OS > 分组 > 页面标题）
  - 当前编辑的流程名称 + 编辑中标签
  - AI 助手按钮（切换聊天面板）
  - 通知中心（弹窗预览未读通知 + WebSocket 实时推送 / 定时轮询兜底）
  - 用户下拉菜单（账号设置 / 退出登录）
-->
<template>
  <header class="topbar">
    <!-- 左侧：面包屑 + 流程标题 -->
    <div class="topbar-left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>PROCESS OS</el-breadcrumb-item>
        <el-breadcrumb-item>{{ route.meta.group || '工作区' }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
      <!-- 编辑模式下的流程名称标签 -->
      <div class="flow-title" v-if="templateStore.currentTemplate">
        <span>{{ templateStore.currentFlowName }}</span>
        <el-tag size="small" effect="plain" type="success">编辑中</el-tag>
      </div>
    </div>

    <!-- 右侧：操作区 -->
    <div class="topbar-actions">
      <!-- AI 助手按钮 -->
      <el-button round :icon="MagicStick" @click="chatStore.togglePanel()">AI 助手</el-button>

      <!-- 通知弹窗 -->
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
          <!-- 加载/空态/列表 -->
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

      <!-- 用户菜单 -->
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
/**
 * 顶栏逻辑。
 *
 * 通知机制（WebSocket + 轮询兜底）：
 *  1. 优先通过 WebSocket 接收实时推送
 *  2. WebSocket 连接失败或未就绪时，每 30s 轮询一次
 *  3. 通知加载失败不阻塞页面渲染
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell, MagicStick } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTemplateStore } from '@/stores/template'
import { useChatStore } from '@/stores/chat'
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from '@/api/notification'
import type { NotificationItem } from '@/types/workflow'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const templateStore = useTemplateStore()
const chatStore = useChatStore()
const unreadCount = ref(0)
const notificationLoading = ref(false)
const latestNotifications = ref<NotificationItem[]>([])  // 弹窗中展示的最新通知（最多 5 条）
const websocketReady = ref(false)  // WebSocket 是否已连接
let pollTimer: number | undefined
let socket: WebSocket | null = null
let socketFailed = false  // WebSocket 失败标记，失败后不再重试

/** 面包屑最后一项文本（模板名称 > 路由标题 > 默认值） */
const currentTitle = computed(() => {
  if (templateStore.currentTemplate) {
    return templateStore.currentTemplate.templateName
  }
  return (route.meta.title as string) || '工作台'
})

// 挂载时启动通知获取 + WebSocket 连接 + 轮询兜底
onMounted(() => {
  loadNotificationSnapshot()
  connectNotificationSocket()
  // 30s 轮询兜底：仅在 WebSocket 未就绪时执行
  pollTimer = window.setInterval(() => {
    if (!websocketReady.value) {
      loadNotificationSnapshot(true)
    }
  }, 30000)
})

// 卸载时清理定时器和 WebSocket
onBeforeUnmount(() => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
  }
  if (socket) {
    socket.close()
    socket = null
  }
})

/** 退出登录 → 清除状态，跳转登录页 */
function logout() {
  authStore.logout()
  router.replace('/login')
}

/**
 * 加载通知快照（未读数量 + 最新未读列表）。
 *
 * @param silent 静默模式：不显示 loading 态，失败不覆盖已有数据
 */
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
    latestNotifications.value = unreadItems.slice(0, 5)  // 弹窗最多展示 5 条
  } catch {
    if (!silent) {
      latestNotifications.value = []
    }
  } finally {
    notificationLoading.value = false
  }
}

/** 建立 WebSocket 连接用于实时通知推送 */
function connectNotificationSocket() {
  if (!('WebSocket' in window) || socketFailed) {
    return
  }
  try {
    // 根据当前协议选择 ws 或 wss
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname || 'localhost'
    const accessToken = encodeURIComponent(authStore.token)
    socket = new WebSocket(`${protocol}//${host}:8080/ws/notifications?access_token=${accessToken}`)
    socket.onopen = () => {
      websocketReady.value = true
    }
    socket.onmessage = () => {
      // 收到推送后静默刷新通知
      loadNotificationSnapshot(true)
    }
    socket.onclose = () => {
      websocketReady.value = false
    }
    socket.onerror = () => {
      websocketReady.value = false
      socketFailed = true  // 标记失败，不再尝试重连
      socket?.close()
    }
  } catch {
    websocketReady.value = false
    socketFailed = true
  }
}

/** 打开通知 → 标记已读 + 跳转目标 */
async function openNotification(item: NotificationItem) {
  try {
    await markNotificationRead(item.id)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    // 从弹窗列表中移除已读通知
    latestNotifications.value = latestNotifications.value.filter((notification) => notification.id !== item.id)
  } catch {
    // 跳转优先，已读失败不阻塞查看目标
  }
  const target = resolveTarget(item)
  if (target) {
    router.push(target)
  }
}

/** 跳转到通知中心页面 */
function goNotifications() {
  router.push('/notifications')
}

/** 根据通知内容解析跳转目标路径 */
function resolveTarget(item: NotificationItem) {
  if (item.targetUrl) return item.targetUrl
  // flowable_task 类型 → 任务详情页
  if (item.targetType?.startsWith('flowable_task:')) {
    const taskId = item.targetType.slice('flowable_task:'.length)
    return taskId ? `/tasks/${taskId}` : ''
  }
  // process_instance 类型 → 流程实例详情页
  if (item.targetType === 'process_instance' && item.targetId) {
    return `/process/instances/${item.targetId}`
  }
  return ''
}

/** 通知类型 → 中文标签映射 */
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

/** 通知类型 → Element Plus Tag 类型映射 */
function typeTag(type?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'timeout_warning') return 'danger'   // 超时预警用红色
  if (type === 'approval_result') return 'success'   // 审批结果用绿色
  if (type === 'process_completed') return 'success' // 流程完成用绿色
  if (type === 'task_remind') return 'warning'       // 任务提醒用橙色
  return 'info'
}
</script>

<!-- 顶栏样式：毛玻璃背景 + 通知下拉卡片 + 用户头像 -->
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
