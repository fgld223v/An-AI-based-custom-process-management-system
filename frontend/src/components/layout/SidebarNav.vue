<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">AF</div>
      <div>
        <div class="brand-title">AI Flow</div>
        <div class="brand-subtitle">PROCESS OS</div>
      </div>
    </div>

    <nav class="nav-groups">
      <section v-for="group in menuGroups" :key="group.title" class="nav-group">
        <div class="group-title">{{ group.title }}</div>
        <button
          v-for="item in group.items"
          :key="item.label"
          class="nav-item"
          :class="{ active: route.path === item.path }"
          type="button"
          @click="handleClick(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="item-badge">{{ item.badge }}</span>
        </button>
      </section>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CirclePlus,
  Cpu,
  DataAnalysis,
  Files,
  MagicStick,
  Monitor,
  Bell,
  Operation,
  Setting,
  Share,
  Tickets,
  Timer,
  Tools,
  TrendCharts,
  UserFilled,
  OfficeBuilding
} from '@element-plus/icons-vue'
import { showComingSoon } from '@/utils/feedback'
import { getMyTasks } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import type { SystemRole } from '@/types/auth'

interface MenuItem {
  label: string
  path: string
  icon: object
  available?: boolean
  badge?: string
  roles?: SystemRole[]
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const todoCount = ref<number | null>(null)

/** 过滤菜单项：当前用户角色可见的项 */
function filterByRole(items: MenuItem[]): MenuItem[] {
  const userRole = authStore.user?.systemRole
  return items.filter(item => {
    if (!item.roles || item.roles.length === 0) return true
    return userRole ? item.roles.includes(userRole) : false
  })
}

const allMenuGroups: MenuGroup[] = [
  {
    title: '概览',
    items: [
      { label: '工作台', path: '/workbench', icon: DataAnalysis, available: true }
    ]
  },
  {
    title: '流程',
    items: [
      { label: '工作台', path: '/workbench', icon: DataAnalysis, available: true },
      { label: '流程编辑器', path: '/process-designer', icon: Share, available: true }
    ]
  },
  {
    title: 'AI',
    items: [
      { label: 'AI生成流程', path: '/ai/generate-process', icon: MagicStick, available: true },
      { label: 'AI生成表单', path: '/ai/generate-form', icon: MagicStick, available: true },
      { label: 'AI审批建议', path: '/tasks/todo', icon: MagicStick }
    ]
  },
  {
    title: '运行',
    items: [
      { label: '运行监控', path: '/dashboard', icon: Monitor, available: true },
      { label: '流程发起', path: '/process/start-preview', icon: CirclePlus, available: true },
      { label: '流程实例', path: '/process/instances', icon: Tickets, available: true },
      { label: '待办任务', path: '/tasks/todo', icon: Tickets, available: true, badge: '' },
      { label: '已办任务', path: '/tasks/done', icon: Tickets, available: true },
      { label: '通知中心', path: '/notifications', icon: Bell, available: true },
      { label: '执行追踪', path: '/placeholder/tracing', icon: TrendCharts }
    ]
  },
  {
    title: '资源',
    items: [
      { label: '流程模板管理', path: '/templates', icon: Files, available: true, roles: ['super_admin', 'biz_admin'] as SystemRole[] },
      { label: '模板市场', path: '/template-market', icon: Files, available: true, roles: ['super_admin', 'biz_admin'] as SystemRole[] },
      { label: '节点/工具库', path: '/placeholder/tools', icon: Tools }
    ]
  },
  {
    title: '系统',
    items: [
      { label: '用户管理', path: '/admin/users', icon: UserFilled, available: true, roles: ['super_admin'] as SystemRole[] },
      { label: '部门管理', path: '/admin/departments', icon: OfficeBuilding, available: true, roles: ['super_admin'] as SystemRole[] },
      { label: '自动化策略', path: '/settings/automation', icon: Operation, available: true, roles: ['super_admin', 'biz_admin'] as SystemRole[] },
      { label: '个人设置', path: '/settings', icon: Setting, available: true },
      { label: 'AI资源池', path: '/placeholder/ai-pool', icon: Cpu },
      { label: '定时任务', path: '/placeholder/schedule', icon: Timer }
    ]
  }
]

/** 按角色过滤后的菜单组 */
const menuGroups = computed(() =>
  allMenuGroups
    .map(group => ({ ...group, items: filterByRole(group.items) }))
    .filter(group => group.items.length > 0)
)

onMounted(async () => {
  try {
    const tasks = await getMyTasks()
    todoCount.value = tasks.length
    const runGroup = allMenuGroups.find(g => g.title === '运行')
    if (runGroup) {
      const todoItem = runGroup.items.find(i => i.path === '/tasks/todo')
      if (todoItem && tasks.length > 0) {
        todoItem.badge = String(tasks.length)
      }
    }
  } catch {
    // badge 获取失败不阻塞侧边栏渲染
  }
})

function handleClick(item: MenuItem) {
  if (item.available) {
    router.push(item.path)
  } else {
    showComingSoon()
  }
}
</script>
