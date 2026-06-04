<template>
  <div class="workbench-page">
    <section class="welcome-band">
      <div>
        <el-tag effect="plain" type="success">第一阶段 MVP</el-tag>
        <h1>构建你的流程自动化工作区</h1>
        <p>当前版本聚焦账号权限、表单设计器、流程设计器和模板保存，为后续 AI 生成与执行引擎预留入口。</p>
      </div>
      <div class="welcome-actions">
        <el-button size="large" round type="success" @click="router.push('/process-designer')">新建流程</el-button>
        <el-button size="large" round @click="router.push('/form-designer')">设计表单</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-icon">
          <el-icon><component :is="metric.icon" /></el-icon>
        </div>
        <div>
          <span>{{ metric.value }}</span>
          <p>{{ metric.label }}</p>
        </div>
      </div>
    </section>

    <section class="workspace-grid">
      <div class="panel-card large-panel">
        <div class="panel-title-row">
          <div>
            <h2>最近流程</h2>
            <p>快速回到正在搭建的业务流程</p>
          </div>
          <el-button text type="success" @click="router.push('/templates')">查看全部</el-button>
        </div>
        <div class="flow-list">
          <div v-for="flow in flows" :key="flow.name" class="flow-row">
            <div class="flow-dot"></div>
            <div class="flow-info">
              <strong>{{ flow.name }}</strong>
              <span>{{ flow.type }} · {{ flow.time }}</span>
            </div>
            <el-tag size="small" :type="flow.status === '已发布' ? 'success' : 'info'" effect="plain">{{ flow.status }}</el-tag>
          </div>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-title-row compact">
          <div>
            <h2>下一步</h2>
            <p>建议完成的 MVP 能力</p>
          </div>
        </div>
        <div class="todo-list">
          <div v-for="item in todos" :key="item" class="todo-item">
            <span></span>
            {{ item }}
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Collection, Finished, MagicStick, Share } from '@element-plus/icons-vue'

const router = useRouter()

const metrics = [
  { label: '流程模板', value: '12', icon: Share },
  { label: '表单配置', value: '8', icon: Collection },
  { label: '已发布', value: '5', icon: Finished },
  { label: 'AI入口', value: '2', icon: MagicStick }
]

const flows = [
  { name: '员工请假审批流程', type: '人事流程', time: '今天 10:24', status: '草稿' },
  { name: '费用报销审批流程', type: '财务流程', time: '昨天 18:10', status: '已发布' },
  { name: '设备报修上报流程', type: '行政流程', time: '周二 09:30', status: '草稿' }
]

const todos = ['连接模板管理接口', '完善表单字段属性面板', '接入 bpmn.js 画布保存与回显', '补充角色菜单权限']
</script>
