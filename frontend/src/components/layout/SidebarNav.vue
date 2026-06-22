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
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
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
  Bell,
  CirclePlus,
  Cpu,
  DataAnalysis,
  Files,
  MagicStick,
  Monitor,
  OfficeBuilding,
  Operation,
  Setting,
  Share,
  Tickets,
  Timer,
  Tools,
  TrendCharts,
  UserFilled
} from '@element-plus/icons-vue'
import { getMyTasks } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import { showComingSoon } from '@/utils/feedback'
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

// 确保 systemRole 已加载（localStorage 旧数据可能缺失）
if (authStore.isLoggedIn && !authStore.user?.systemRole) {
  authStore.fetchMe().catch(() => authStore.logout())
}

const adminRoles: SystemRole[] = ['super_admin', 'biz_admin']
const superAdminOnly: SystemRole[] = ['super_admin']
const bizAdminOnly: SystemRole[] = ['biz_admin']

const allMenuGroups: MenuGroup[] = [
  {
    title: '概览',
    items: [
      { label: '工作台', path: '/workbench', icon: DataAnalysis, available: true ,roles: superAdminOnly }
    ]
  },
  {
    title: '流程',
    items: [
      { label: '我的流程', path: '/my-processes', icon: Share, available: true, roles: bizAdminOnly },
      { label: '表单设计器', path: '/form-designer', icon: Files, available: true, roles: adminRoles },
      { label: '流程编辑器', path: '/process-designer', icon: Share, available: true, roles: adminRoles }
    ]
  },
  {
    title: 'AI',
    items: [
      { label: 'AI 生成流程', path: '/ai/generate-process', icon: MagicStick, available: true, roles: adminRoles },
      { label: 'AI 生成表单', path: '/ai/generate-form', icon: MagicStick, available: true, roles: adminRoles },
      { label: 'AI 流程优化', path: '/ai/optimize', icon: TrendCharts, available: true, roles: adminRoles }
    ]
  },
  {
    title: '运行',
    items: [
      { label: '业务监控', path: '/business-monitor', icon: Monitor, available: true, roles: bizAdminOnly },
      { label: '运行监控', path: '/runtime-monitor', icon: Monitor, available: true, roles: superAdminOnly },
      { label: '流程发起', path: '/process/start-preview', icon: CirclePlus, available: true },
      { label: '我的申请', path: '/process/instances', icon: Tickets, available: true },
      { label: '待办任务', path: '/tasks/todo', icon: Tickets, available: true, badge: '' },
      { label: '已办任务', path: '/tasks/done', icon: Tickets, available: true },
      { label: '通知中心', path: '/notifications', icon: Bell, available: true },
      { label: '执行追踪', path: '/dashboard', icon: TrendCharts, available: true,roles: superAdminOnly  }
    ]
  },
  {
    title: '资源',
    items: [
      { label: '流程模板管理', path: '/templates', icon: Files, available: true, roles: superAdminOnly },
      { label: '模板市场', path: '/template-market', icon: Files, available: true, roles: adminRoles },
      { label: '节点/工具库', path: '/placeholder/tools', icon: Tools, roles: adminRoles }
    ]
  },
  {
    title: '系统',
    items: [
      { label: '用户管理', path: '/admin/users', icon: UserFilled, available: true, roles: superAdminOnly },
      { label: '部门管理', path: '/admin/departments', icon: OfficeBuilding, available: true, roles: superAdminOnly },
      { label: '自动化策略', path: '/settings/automation', icon: Operation, available: true, roles: adminRoles },
      { label: '个人设置', path: '/settings', icon: Setting, available: true },
      { label: 'AI 资源池', path: '/placeholder/ai-pool', icon: Cpu, roles: adminRoles },
      { label: '定时任务', path: '/placeholder/schedule', icon: Timer, roles: adminRoles }
      
    ]
  }
]

function canSee(item: MenuItem) {
  if (!item.roles || item.roles.length === 0) return true
  const role = authStore.user?.systemRole
  return Boolean(role && item.roles.includes(role))
}

const menuGroups = computed(() =>
  allMenuGroups
    .map(group => ({ ...group, items: group.items.filter(canSee) })) 
    .filter(group => group.items.length > 0)
)

onMounted(async () => {
  try {
    const tasks = await getMyTasks()
    todoCount.value = tasks.length
    const todoItem = allMenuGroups
      .flatMap(group => group.items)
      .find(item => item.path === '/tasks/todo')
    if (todoItem && tasks.length > 0) {
      todoItem.badge = String(tasks.length)
    }
  } catch {
    // badge 获取失败不阻塞侧边栏渲染
  }
})

function isActive(path: string) {
  return route.path === path
}

function handleClick(item: MenuItem) {
  if (item.available) {
    router.push(item.path)
  } else {
    showComingSoon()
  }
}
</script>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  width: 248px;
  height: 100vh;
  padding: 24px 18px;
  overflow-y: auto;
  border-right: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
}
.brand { display: flex; gap: 12px; align-items: center; margin-bottom: 28px; }
.brand-mark {
  display: grid; width: 42px; height: 42px; color: #fff; font-weight: 800;
  border-radius: 14px; place-items: center;
  background: linear-gradient(135deg, #00a3ff, #19c37d);
  box-shadow: 0 12px 24px rgba(0, 163, 255, 0.24);
}
.brand-title { color: var(--text); font-size: 18px; font-weight: 800; }
.brand-subtitle { color: var(--muted); font-size: 11px; letter-spacing: 0.12em; }
.nav-groups { display: flex; flex-direction: column; gap: 18px; }
.group-title { margin: 0 0 8px 8px; color: var(--muted); font-size: 12px; font-weight: 700; }
.nav-item {
  display: flex; align-items: center; width: 100%; min-height: 42px; padding: 0 12px;
  margin-bottom: 6px; color: #486070; text-align: left; cursor: pointer; border: 0;
  border-radius: 12px; background: transparent; transition: 0.18s ease; gap: 10px;
}
.nav-item:hover { color: var(--text); background: rgba(0, 163, 255, 0.08); }
.nav-item.active {
  color: #0576b9;
  background: linear-gradient(135deg, rgba(0, 163, 255, 0.14), rgba(25, 195, 125, 0.13));
  box-shadow: inset 3px 0 0 #00a3ff;
}
.nav-item span:nth-child(2) { flex: 1; }
.item-badge {
  min-width: 20px; height: 20px; padding: 0 6px; color: #fff; font-size: 11px;
  line-height: 20px; text-align: center; border-radius: 999px; background: #ff6b6b;
}
</style>
