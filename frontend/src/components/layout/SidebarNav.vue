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
import { useRoute, useRouter } from 'vue-router'
import {
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

interface MenuItem {
  label: string
  path: string
  icon: object
  available?: boolean
  badge?: string
}

const route = useRoute()
const router = useRouter()

const menuGroups: Array<{ title: string; items: MenuItem[] }> = [
  {
    title: '流程',
    items: [
      { label: '工作台', path: '/workbench', icon: DataAnalysis, available: true },
      { label: '流程编辑器', path: '/process-designer', icon: Share, available: true },
      { label: 'AI生成流程', path: '/placeholder/ai-process', icon: MagicStick }
    ]
  },
  {
    title: '运行',
    items: [
      { label: '运行监控', path: '/placeholder/monitor', icon: Monitor },
      { label: '执行追踪', path: '/placeholder/tracing', icon: TrendCharts },
      { label: '我的待办', path: '/placeholder/todo', icon: Tickets, badge: '3' }
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
]

function handleClick(item: MenuItem) {
  if (item.available) {
    router.push(item.path)
  } else {
    showComingSoon()
  }
}
</script>
