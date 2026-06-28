<!--
  侧边栏导航组件。

  功能：
  - 展示系统品牌标识（AI Flow / PROCESS OS）
  - 按角色动态过滤菜单分组与菜单项
  - 挂载时获取待办任务数量并在菜单徽标中展示
  - active 状态根据当前路由路径高亮
-->
<template>
  <aside class="sidebar">
    <!-- 品牌区域 -->
    <div class="brand">
      <div class="brand-mark">AF</div>
      <div>
        <div class="brand-title">AI Flow</div>
        <div class="brand-subtitle">PROCESS OS</div>
      </div>
    </div>

    <!-- 导航菜单分组 -->
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
          <!-- 角标（待办数等） -->
          <span v-if="item.badge" class="item-badge">{{ item.badge }}</span>
        </button>
      </section>
    </nav>
  </aside>
</template>

<script setup lang="ts">
/**
 * 侧边栏导航逻辑。
 *
 * 核心逻辑：
 *  1. 全量菜单分组 allMenuGroups 按角色标注 roles
 *  2. canSee() 根据当前用户角色过滤不可见项
 *  3. menuGroups 计算属性产出最终渲染的菜单结构
 *  4. onMounted 异步获取待办任务数并更新徽标
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  CircleCheck,
  CirclePlus,
  DataAnalysis,
  Clock,
  Collection,
  Connection,
  DataBoard,
  DataLine,
  Document,
  DocumentAdd,
  EditPen,
  Files,
  Goods,
  Guide,
  Histogram,
  MagicStick,
  Monitor,
  OfficeBuilding,
  Setting,
  SetUp,
  TrendCharts,
  UserFilled
} from '@element-plus/icons-vue'
import { getMyTasks } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import type { SystemRole } from '@/types/auth'

/** 菜单项 */
interface MenuItem {
  label: string
  path: string
  icon: object
  available?: boolean      // 是否可选（false 则灰显不可点击）
  badge?: string           // 徽标文本（如待办数）
  roles?: SystemRole[]     // 允许访问的角色列表
}

/** 菜单分组 */
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

// 预定义角色组合
const adminRoles: SystemRole[] = ['super_admin', 'biz_admin']
const superAdminOnly: SystemRole[] = ['super_admin']
const bizAdminOnly: SystemRole[] = ['biz_admin']

/** 全量菜单分组定义（含角色标注） */
const allMenuGroups: MenuGroup[] = [
  {
    title: '概览',
    items: [
      { label: '工作台', path: '/workbench', icon: DataBoard, available: true, roles: superAdminOnly }
    ]
  },
  {
    title: '流程',
    items: [
      { label: '我的流程', path: '/my-processes', icon: Guide, available: true, roles: bizAdminOnly },
      { label: '表单设计器', path: '/form-designer', icon: Files, available: true, roles: adminRoles },
      { label: '流程编辑器', path: '/process-designer', icon: EditPen, available: true, roles: adminRoles }
    ]
  },
  {
    title: 'AI',
    items: [
      { label: 'AI 生成流程', path: '/ai/generate-process', icon: MagicStick, available: true, roles: adminRoles },
      { label: 'AI 生成表单', path: '/ai/generate-form', icon: DocumentAdd, available: true, roles: adminRoles },
      { label: 'AI 流程优化', path: '/ai/optimize', icon: TrendCharts, available: true, roles: adminRoles }
    ]
  },
  {
    title: '运行',
    items: [
      { label: '业务监控', path: '/business-monitor', icon: Monitor, available: true, roles: bizAdminOnly },
      { label: '运行监控', path: '/runtime-monitor', icon: DataLine, available: true, roles: superAdminOnly },
      { label: '流程发起', path: '/process/start-preview', icon: CirclePlus, available: true },  // 所有角色
      { label: '我的申请', path: '/process/instances', icon: Document, available: true },
      { label: '待办任务', path: '/tasks/todo', icon: Clock, available: true, badge: '' },
      { label: '已办任务', path: '/tasks/done', icon: CircleCheck, available: true },
      { label: '通知中心', path: '/notifications', icon: Bell, available: true },
      { label: '执行追踪', path: '/dashboard', icon: Histogram, available: true, roles: superAdminOnly }
    ]
  },
  {
    title: '资源',
    items: [
      { label: '流程模板管理', path: '/templates', icon: Collection, available: true, roles: superAdminOnly },
      { label: '模板市场', path: '/template-market', icon: Goods, available: true, roles: adminRoles }
    ]
  },
  {
    title: '系统',
    items: [
      { label: '个人设置', path: '/settings', icon: Setting, available: true },
      { label: '用户管理', path: '/admin/users', icon: UserFilled, available: true, roles: superAdminOnly },
      { label: '部门管理', path: '/admin/departments', icon: OfficeBuilding, available: true, roles: superAdminOnly },
      { label: '流程角色管理', path: '/admin/workflow-roles', icon: Connection, available: true, roles: superAdminOnly },
      { label: '自动化策略', path: '/settings/automation', icon: SetUp, available: true, roles: adminRoles },
      { label: '系统设置', path: '/settings/system', icon: DataAnalysis, available: true, roles: superAdminOnly }
    ]
  }
]

/**
 * 判断当前用户是否可以看到某个菜单项。
 * 无 roles 限制的项所有角色可见。
 */
function canSee(item: MenuItem) {
  if (!item.roles || item.roles.length === 0) return true
  const role = authStore.user?.systemRole
  return Boolean(role && item.roles.includes(role))
}

/** 按角色过滤后的菜单分组（移除空分组） */
const menuGroups = computed(() =>
  allMenuGroups
    .map(group => ({ ...group, items: group.items.filter(canSee) }))
    .filter(group => group.items.length > 0)
)

// 挂载后异步获取待办任务数
onMounted(async () => {
  try {
    const tasks = await getMyTasks()
    todoCount.value = tasks.length
    // 更新待办任务菜单项的徽标
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

/** 判断当前路由是否激活 */
function isActive(path: string) {
  return route.path === path
}

/** 菜单项点击 → 路由跳转 */
function handleClick(item: MenuItem) {
  if (item.available !== false) {
    router.push(item.path)
  }
}
</script>

<!-- 侧边栏样式：粘性定位 + 毛玻璃背景 + 渐变品牌色 -->
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
