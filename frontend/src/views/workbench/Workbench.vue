<template>
  <div class="workbench-page">
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

    <el-alert
      v-if="error"
      type="error"
      show-icon
      :closable="false"
      title="数据加载失败"
      description="请检查网络连接后重试"
    >
      <template #default>
        <el-button type="danger" size="small" round @click="loadData">重新加载</el-button>
      </template>
    </el-alert>

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
          <div class="flow-row" @click="router.push('/process/instances?status=running')">
            <div class="flow-dot running"></div>
            <div class="flow-info">
              <strong>{{ statusDist.running ?? 0 }} 个运行中</strong>
              <span>流程正在 Flowable 引擎中执行</span>
            </div>
            <el-tag type="success" size="small" effect="plain">running</el-tag>
          </div>
          <div class="flow-row" @click="router.push('/process/instances?status=submitted')">
            <div class="flow-dot submitted"></div>
            <div class="flow-info">
              <strong>{{ statusDist.submitted ?? 0 }} 个已提交</strong>
              <span>已提交，待启动流程引擎</span>
            </div>
            <el-tag size="small" effect="plain">submitted</el-tag>
          </div>
          <div class="flow-row" @click="router.push('/process/instances?status=draft')">
            <div class="flow-dot draft"></div>
            <div class="flow-info">
              <strong>{{ statusDist.draft ?? 0 }} 个草稿</strong>
              <span>尚未提交到 Flowable 引擎</span>
            </div>
            <el-tag type="info" size="small" effect="plain">draft</el-tag>
          </div>
          <div class="flow-row" @click="router.push('/process/instances?status=completed')">
            <div class="flow-dot completed"></div>
            <div class="flow-info">
              <strong>{{ statusDist.completed ?? 0 }} 个已完成</strong>
              <span>流程已走完所有审批节点</span>
            </div>
            <el-tag type="warning" size="small" effect="plain">completed</el-tag>
          </div>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-title-row compact">
          <div>
            <h2>最近实例</h2>
            <p>最近创建的流程实例</p>
          </div>
        </div>
        <div class="todo-list" v-if="recentInstances.length">
          <div v-for="inst in recentInstances" :key="inst.id" class="todo-item clickable" @click="router.push(`/process/instances/${inst.id}`)">
            <span :class="`status-dot ${inst.status}`"></span>
            {{ inst.instanceTitle }}
            <el-tag size="small" effect="plain" :type="inst.status === 'running' ? 'success' : inst.status === 'draft' ? 'info' : 'warning'">
              {{ inst.status }}
            </el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无实例" :image-size="48" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, Plus, Tickets, Timer, TrendCharts, Warning } from '@element-plus/icons-vue'
import { getStatisticsOverview } from '@/api/statistics'
import { getProcessInstanceList } from '@/api/processInstance'

const router = useRouter()
const loading = ref(false)
const error = ref(false)

const stats = reactive({
  todayNew: 0,
  pendingTasks: 0,
  totalInstances: 0,
  completionRate: 0,
  avgDurationHours: 0,
  anomalyCount: 0
})

const statusDist = reactive<Record<string, number>>({})
const recentInstances = ref<Array<{ id: number; instanceTitle: string; status: string }>>([])

onMounted(() => { loadData() })

async function loadData() {
  loading.value = true
  error.value = false
  try {
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
      id: i.id,
      instanceTitle: i.instanceTitle,
      status: i.status
    }))
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.workbench-page { display: flex; flex-direction: column; gap: 18px; }
.welcome-band, .metric-grid, .panel-card {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255,255,255,0.94);
  box-shadow: var(--shadow);
}
.welcome-band { display: flex; justify-content: space-between; align-items: center; padding: 24px; gap: 16px; }
.welcome-band h1 { margin: 10px 0 6px; font-size: 28px; }
.welcome-band p { margin: 0; color: var(--muted); }
.welcome-actions { display: flex; gap: 10px; }
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
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .workspace-grid { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .metric-grid { grid-template-columns: 1fr; }
}
</style>
