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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CirclePlus,
  Cpu,
  DataAnalysis,
  Files,
  MagicStick,
  Monitor,
  Operation,
  Setting,
  Share,
  Tickets,
  Timer,
  Tools,
  TrendCharts
} from '@element-plus/icons-vue'
import { showComingSoon } from '@/utils/feedback'
import { getMyTasks } from '@/api/task'

interface MenuItem {
  label: string
  path: string
  icon: object
  available?: boolean
  badge?: string
}

const route = useRoute()
const router = useRouter()
const todoCount = ref<number | null>(null)

const menuGroups = ref<Array<{ title: string; items: MenuItem[] }>>([
  {
    title: '流程',
    items: [
      { label: '工作台', path: '/workbench', icon: DataAnalysis, available: true },
      { label: '流程编辑器', path: '/process-designer', icon: Share, available: true },
      { label: 'AI生成流程', path: '/ai/generate-process', icon: MagicStick, available: true }
    ]
  },
  {
    title: '运行',
    items: [
      { label: '流程发起预览', path: '/process/start-preview', icon: CirclePlus, available: true },
      { label: '流程实例', path: '/process/instances', icon: Tickets, available: true },
      { label: '运行监控', path: '/placeholder/monitor', icon: Monitor },
      { label: '执行追踪', path: '/placeholder/tracing', icon: TrendCharts },
      { label: '待办任务', path: '/tasks/todo', icon: Tickets, available: true, badge: '' },
      { label: '已办任务', path: '/tasks/done', icon: Tickets, available: true }
    ]
  },
  {
    title: '资源',
    items: [
      { label: '流程模板管理', path: '/templates', icon: Files, available: true },
      { label: '模板市场', path: '/template-market', icon: Files, available: true },
      { label: '节点/工具库', path: '/placeholder/tools', icon: Tools }
    ]
  },
  {
    title: '系统',
    items: [
      { label: '设置/权限', path: '/settings', icon: Setting, available: true },
      { label: '自动化策略', path: '/placeholder/automation', icon: Operation },
      { label: 'AI资源池', path: '/placeholder/ai-pool', icon: Cpu },
      { label: '定时任务', path: '/placeholder/schedule', icon: Timer }
    ]
  }
])

onMounted(async () => {
  try {
    const tasks = await getMyTasks()
    todoCount.value = tasks.length
    // 更新待办 badge
    const runGroup = menuGroups.value.find(g => g.title === '运行')
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
