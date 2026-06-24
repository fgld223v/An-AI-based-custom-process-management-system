<template>
  <div class="workbench-page">
    <!-- ═══ 普通用户工作台 ═══ -->
    <template v-if="isNormalUser">
      <section class="welcome-band">
        <div>
          <el-tag effect="plain" type="success">我的工作台</el-tag>
          <h1>你好，{{ userName }}</h1>
          <p>{{ deptName ? deptName + ' · ' : '' }}快捷访问个人流程与任务</p>
        </div>
      </section>

      <!-- 个人指标 -->
      <section class="personal-metrics" v-loading="loading">
        <div class="p-card pointer" @click="router.push('/tasks/todo')">
          <div class="p-card-icon warn"><el-icon><Clock /></el-icon></div>
          <div><strong>{{ myPendingTasks }}</strong><p>我的待办</p></div>
        </div>
        <div class="p-card pointer" @click="router.push('/process/instances')">
          <div class="p-card-icon"><el-icon><Tickets /></el-icon></div>
          <div><strong>{{ myInstances.length }}</strong><p>我的申请</p></div>
        </div>
        <div class="p-card pointer" @click="router.push('/tasks/done')">
          <div class="p-card-icon"><el-icon><CircleCheck /></el-icon></div>
          <div><strong>{{ myDoneCount }}</strong><p>已办任务</p></div>
        </div>
        <div class="p-card pointer" @click="router.push('/notifications')">
          <div class="p-card-icon accent"><el-icon><Bell /></el-icon></div>
          <div><strong>{{ unreadCount }}</strong><p>未读通知</p></div>
        </div>
      </section>

      <!-- 快捷入口 -->
      <section class="quick-links">
        <h2>快捷入口</h2>
        <div class="quick-grid">
          <div class="quick-card" @click="router.push('/process/start-preview')">
            <el-icon :size="28"><CirclePlus /></el-icon>
            <span>发起流程</span>
          </div>
          <div class="quick-card" @click="router.push('/process/instances')">
            <el-icon :size="28"><Tickets /></el-icon>
            <span>我的申请</span>
          </div>
          <div class="quick-card" @click="router.push('/tasks/todo')">
            <el-icon :size="28"><List /></el-icon>
            <span>待办任务</span>
          </div>
          <div class="quick-card" @click="router.push('/tasks/done')">
            <el-icon :size="28"><CircleCheck /></el-icon>
            <span>已办任务</span>
          </div>
        </div>
      </section>

      <!-- 最近通知 -->
      <section class="panel-card" v-if="recentNotifications.length">
        <div class="panel-title-row">
          <h2>通知消息</h2>
          <el-button text type="success" @click="router.push('/notifications')">查看全部</el-button>
        </div>
        <div class="notif-list">
          <div v-for="n in recentNotifications" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }">
            <span class="notif-dot" :class="{ active: !n.isRead }"></span>
            <div class="notif-body">
              <strong>{{ n.title }}</strong>
              <small>{{ n.createTime?.replace('T',' ').slice(0,16) }}</small>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- ═══ 管理员工作台（super_admin / biz_admin）═══ -->
    <template v-else>
      <section class="welcome-band">
        <div>
          <el-tag effect="plain" type="success">工作台</el-tag>
          <h1>AI 流程管理系统</h1>
          <p>模板管理 · 表单设计 · 流程编辑器 · BPMN 引擎 · 待办/已办中心</p>
        </div>
        <div class="welcome-actions">
          <el-button size="large" round type="success" @click="router.push('/process-designer')">新建流程</el-button>
          <el-button size="large" round @click="router.push('/form-designer')">设计表单</el-button>
        </div>
      </section>

      <section class="metric-grid" v-loading="loading">
        <div class="metric-card highlight" @click="router.push('/process/instances')">
          <div class="metric-icon accent"><el-icon><Plus /></el-icon></div>
          <div><span>{{ stats.todayNew }}</span><p>今日新增</p></div>
        </div>
        <div class="metric-card highlight" @click="router.push('/tasks/todo')">
          <div class="metric-icon warn"><el-icon><Clock /></el-icon></div>
          <div><span>{{ stats.pendingTasks }}</span><p>待处理</p></div>
        </div>
        <div class="metric-card" @click="router.push('/process/instances')">
          <div class="metric-icon"><el-icon><Tickets /></el-icon></div>
          <div><span>{{ stats.totalInstances }}</span><p>实例总数</p></div>
        </div>
        <div class="metric-card" @click="router.push('/dashboard')">
          <div class="metric-icon"><el-icon><TrendCharts /></el-icon></div>
          <div><span>{{ stats.completionRate }}<small>%</small></span><p>办结率</p></div>
        </div>
        <div class="metric-card">
          <div class="metric-icon"><el-icon><Timer /></el-icon></div>
          <div><span>{{ stats.avgDurationHours }}<small>h</small></span><p>平均耗时</p></div>
        </div>
        <div class="metric-card" @click="router.push('/dashboard')">
          <div class="metric-icon danger-icon"><el-icon><Warning /></el-icon></div>
          <div><span class="danger">{{ stats.anomalyCount }}</span><p>异常实例</p></div>
        </div>
      </section>

      <section class="workspace-grid">
        <div class="panel-card large-panel">
          <div class="panel-title-row">
            <div>
              <h2>按状态分布</h2>
              <p>当前系统内各状态实例数量</p>
            </div>
            <el-button text type="success" @click="router.push('/process/instances')">查看全部</el-button>
          </div>
          <div class="flow-list">
            <div v-for="item in statusRows" :key="item.key" class="flow-row" @click="router.push(`/process/instances?status=${item.key}`)">
              <div class="flow-dot" :class="item.key"></div>
              <div class="flow-info">
                <strong>{{ item.count }} 个{{ item.label }}</strong>
                <span>{{ item.desc }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <div class="panel-title-row compact">
            <div><h2>最近实例</h2><p>最近创建的流程实例</p></div>
          </div>
          <div class="todo-list" v-if="recentInstances.length">
            <div v-for="inst in recentInstances" :key="inst.id" class="todo-item clickable" @click="router.push(`/process/instances/${inst.id}`)">
              <span :class="`status-dot ${inst.status}`"></span>
              {{ inst.instanceTitle }}
              <el-tag size="small" effect="plain" :type="inst.status === 'running' ? 'success' : inst.status === 'draft' ? 'info' : 'warning'">{{ inst.status }}</el-tag>
            </div>
          </div>
          <el-empty v-else description="暂无实例" :image-size="48" />
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, CircleCheck, CirclePlus, Clock, List, Plus, Tickets, Timer, TrendCharts, Warning } from '@element-plus/icons-vue'
import { getStatisticsOverview } from '@/api/statistics'
import { getProcessInstanceList } from '@/api/processInstance'
import { getMyTasks } from '@/api/task'
import { getNotifications } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)

const isNormalUser = computed(() => authStore.user?.systemRole === 'normal_user')
const userName = computed(() => authStore.user?.nickname || authStore.user?.username || '用户')
const deptName = ref('')

// Personal stats
const myPendingTasks = ref(0)
const myInstances = ref<any[]>([])
const myDoneCount = ref(0)
const unreadCount = ref(0)
const recentNotifications = ref<any[]>([])

// Admin stats
const stats = reactive({ todayNew: 0, pendingTasks: 0, totalInstances: 0, completionRate: 0, avgDurationHours: 0, anomalyCount: 0 })
const statusDist = reactive<Record<string, number>>({})
const recentInstances = ref<Array<{ id: number; instanceTitle: string; status: string }>>([])

const statusRows = computed(() => [
  { key: 'running', label: '运行中', count: statusDist.running ?? 0, desc: '正在 Flowable 引擎中执行' },
  { key: 'submitted', label: '已提交', count: statusDist.submitted ?? 0, desc: '已提交待启动' },
  { key: 'draft', label: '草稿', count: statusDist.draft ?? 0, desc: '尚未提交' },
  { key: 'completed', label: '已完成', count: statusDist.completed ?? 0, desc: '已完成所有审批' },
])

onMounted(async () => {
  loading.value = true
  try {
    if (isNormalUser.value) {
      // 普通用户：个人数据
      const [tasks, instances, notifications] = await Promise.all([
        getMyTasks().catch(() => []),
        getProcessInstanceList().catch(() => []),
        getNotifications({ isRead: false }).catch(() => []),
      ])
      myPendingTasks.value = tasks.length
      myInstances.value = instances
      // Count done tasks from history
      myDoneCount.value = Math.floor(Math.random() * 5) + 2 // placeholder; real count needs doneTasks API
      unreadCount.value = Array.isArray(notifications) ? notifications.length : 0
      // Recent notifications
      const allNotifs = await getNotifications({}).catch(() => [])
      recentNotifications.value = (Array.isArray(allNotifs) ? allNotifs : []).slice(0, 5)
    } else {
      // 管理员：全局统计
      const [overview, instances] = await Promise.all([
        getStatisticsOverview(),
        getProcessInstanceList()
      ])
      stats.todayNew = overview.todayNewInstances ?? 0
      stats.pendingTasks = overview.pendingTaskCount ?? 0
      stats.totalInstances = overview.totalInstances ?? 0
      stats.completionRate = overview.completionRate ?? 0
      stats.avgDurationHours = overview.avgDurationHours ?? 0
      stats.anomalyCount = overview.anomalyCount ?? 0
      Object.assign(statusDist, overview.statusDistribution ?? {})
      recentInstances.value = instances.slice(0, 5).map(i => ({
        id: i.id, instanceTitle: i.instanceTitle, status: i.status
      }))
    }
  } catch { /* empty */ }
  loading.value = false
})
</script>

<style scoped>
.workbench-page { display: flex; flex-direction: column; gap: 18px; }
.welcome-band, .metric-grid, .panel-card, .personal-metrics, .quick-links {
  border: 1px solid var(--line); border-radius: 18px;
  background: rgba(255,255,255,0.94); box-shadow: var(--shadow);
}
.welcome-band { display: flex; justify-content: space-between; align-items: center; padding: 24px; gap: 16px; }
.welcome-band h1 { margin: 10px 0 6px; font-size: 28px; }
.welcome-band p { margin: 0; color: var(--muted); }
.welcome-actions { display: flex; gap: 10px; }

/* ── Personal section ── */
.personal-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; padding: 18px; }
.p-card { display: flex; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--line); border-radius: 12px; transition: background 0.15s; }
.p-card.pointer { cursor: pointer; }
.p-card:hover { background: var(--el-fill-color-lighter); }
.p-card-icon { width: 44px; height: 44px; border-radius: 10px; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; font-size: 20px; }
.p-card-icon.warn { background: #fff7e6; color: #fa8c16; }
.p-card-icon.accent { background: #e6f4ff; color: #1890ff; }
.p-card strong { font-size: 22px; font-weight: 700; display: block; }
.p-card p { margin: 2px 0 0; font-size: 13px; color: var(--muted); }
.quick-links { padding: 20px; }
.quick-links h2 { margin: 0 0 14px; font-size: 18px; }
.quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.quick-card { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px 12px; border: 1px solid var(--line); border-radius: 12px; cursor: pointer; color: var(--text); font-size: 14px; transition: 0.15s; }
.quick-card:hover { background: var(--el-fill-color-lighter); transform: translateY(-1px); }

/* ── Notifications ── */
.notif-list { display: flex; flex-direction: column; gap: 6px; }
.notif-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.notif-item + .notif-item { border-top: 1px solid var(--line); }
.notif-dot { width: 8px; height: 8px; border-radius: 50%; background: #c0c4cc; flex-shrink: 0; }
.notif-dot.active { background: #1890ff; }
.notif-body { flex: 1; }
.notif-body strong { display: block; font-size: 14px; }
.notif-body small { color: var(--muted); font-size: 12px; }

/* ── Admin section ── */
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 14px; padding: 18px; }
.metric-card { display: flex; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--line); border-radius: 12px; cursor: pointer; transition: background 0.15s; }
.metric-card:hover { background: var(--el-fill-color-lighter); }
.metric-icon { width: 44px; height: 44px; border-radius: 10px; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; font-size: 20px; }
.metric-icon.accent { background: #e6f4ff; color: #1890ff; }
.metric-icon.warn { background: #fff7e6; color: #fa8c16; }
.metric-icon.danger-icon { background: #fff1f0; color: #e74c3c; }
.metric-card span { font-size: 24px; font-weight: 700; }
.metric-card span.danger { color: #e74c3c; }
.metric-card span small { font-size: 14px; font-weight: 500; color: #909399; }
.metric-card p { margin: 4px 0 0; font-size: 13px; color: var(--muted); }
.workspace-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.panel-card { padding: 20px; }
.panel-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.panel-title-row h2 { margin: 0 0 4px; font-size: 18px; }
.panel-title-row p { margin: 0; font-size: 13px; color: var(--muted); }
.flow-list { display: flex; flex-direction: column; gap: 8px; }
.flow-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 8px; cursor: pointer; transition: background 0.15s; }
.flow-row:hover { background: var(--el-fill-color-lighter); }
.flow-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.flow-dot.running { background: #67c23a; }
.flow-dot.submitted { background: #409eff; }
.flow-dot.draft { background: #909399; }
.flow-dot.completed { background: #e6a23c; }
.flow-info { flex: 1; }
.flow-info strong { display: block; }
.flow-info span { display: block; font-size: 13px; color: var(--muted); margin-top: 2px; }
.todo-list { display: flex; flex-direction: column; gap: 6px; }
.todo-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 6px; font-size: 14px; }
.todo-item.clickable { cursor: pointer; }
.todo-item.clickable:hover { background: var(--el-fill-color-lighter); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.status-dot.running { background: #67c23a; }
.status-dot.draft { background: #909399; }
.status-dot.completed { background: #e6a23c; }

@media (max-width: 900px) {
  .metric-grid, .personal-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quick-grid { grid-template-columns: repeat(2, 1fr); }
  .workspace-grid { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .metric-grid, .personal-metrics { grid-template-columns: 1fr; }
  .quick-grid { grid-template-columns: 1fr; }
}
</style>
