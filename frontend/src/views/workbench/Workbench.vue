<template>
  <div class="workbench-page">
    <section class="welcome-band">
      <div>
        <el-tag effect="plain" type="success">V0.7 第四阶段</el-tag>
        <h1>AI 流程管理系统</h1>
        <p>模板管理 · 表单设计 · 流程编辑器 · BPMN 引擎 · 待办/已办中心</p>
      </div>
      <div class="welcome-actions">
        <el-button size="large" round type="success" @click="router.push('/process-designer')">新建流程</el-button>
        <el-button size="large" round @click="router.push('/form-designer')">设计表单</el-button>
      </div>
    </section>

    <section class="metric-grid" v-loading="loading">
      <div class="metric-card" @click="router.push('/templates')">
        <div class="metric-icon"><el-icon><Share /></el-icon></div>
        <div><span>{{ stats.templateCount }}</span><p>流程模板</p></div>
      </div>
      <div class="metric-card" @click="router.push('/form-designer')">
        <div class="metric-icon"><el-icon><Collection /></el-icon></div>
        <div><span>{{ stats.formCount }}</span><p>表单定义</p></div>
      </div>
      <div class="metric-card" @click="router.push('/process/instances')">
        <div class="metric-icon"><el-icon><Tickets /></el-icon></div>
        <div><span>{{ stats.instanceCount }}</span><p>流程实例</p></div>
      </div>
      <div class="metric-card" @click="router.push('/tasks/todo')">
        <div class="metric-icon"><el-icon><Clock /></el-icon></div>
        <div><span>{{ stats.todoCount }}</span><p>待办任务</p></div>
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
              <strong>{{ stats.runningCount }} 个运行中</strong>
              <span>流程正在 Flowable 引擎中执行</span>
            </div>
            <el-tag type="success" size="small" effect="plain">running</el-tag>
          </div>
          <div class="flow-row" @click="router.push('/process/instances?status=draft')">
            <div class="flow-dot draft"></div>
            <div class="flow-info">
              <strong>{{ stats.draftCount }} 个草稿</strong>
              <span>尚未提交到 Flowable 引擎</span>
            </div>
            <el-tag type="info" size="small" effect="plain">draft</el-tag>
          </div>
          <div class="flow-row" @click="router.push('/process/instances?status=completed')">
            <div class="flow-dot completed"></div>
            <div class="flow-info">
              <strong>{{ stats.completedCount }} 个已完成</strong>
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
import { Clock, Collection, Share, Tickets } from '@element-plus/icons-vue'
import { getProcessTemplates } from '@/api/processTemplate'
import { getForms } from '@/api/formDefinition'
import { getProcessInstanceList } from '@/api/processInstance'
import { getMyTasks } from '@/api/task'

const router = useRouter()
const loading = ref(false)

const stats = reactive({
  templateCount: 0,
  formCount: 0,
  instanceCount: 0,
  runningCount: 0,
  draftCount: 0,
  completedCount: 0,
  todoCount: 0
})

const recentInstances = ref<Array<{ id: number; instanceTitle: string; status: string }>>([])

onMounted(async () => {
  loading.value = true
  try {
    const [templates, forms, instances, tasks] = await Promise.all([
      getProcessTemplates(),
      getForms(),
      getProcessInstanceList(),
      getMyTasks()
    ])
    stats.templateCount = templates.length
    stats.formCount = forms.length
    stats.instanceCount = instances.length
    stats.todoCount = tasks.length
    stats.runningCount = instances.filter(i => i.status === 'running').length
    stats.draftCount = instances.filter(i => i.status === 'draft').length
    stats.completedCount = instances.filter(i => i.status === 'completed').length
    recentInstances.value = instances.slice(0, 5).map(i => ({
      id: i.id,
      instanceTitle: i.instanceTitle,
      status: i.status
    }))
  } catch {
    // 工作台统计加载失败时保持默认值。
  }
  loading.value = false
})
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
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; padding: 18px; }
.metric-card { display: flex; align-items: center; gap: 14px; padding: 16px; border: 1px solid var(--line); border-radius: 12px; cursor: pointer; transition: background 0.15s; }
.metric-card:hover { background: var(--el-fill-color-lighter); }
.metric-icon { width: 44px; height: 44px; border-radius: 10px; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; font-size: 20px; }
.metric-card span { font-size: 24px; font-weight: 700; }
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
