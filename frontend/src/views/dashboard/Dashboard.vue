<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { getStatisticsOverview } from '@/api/statistics'

declare const echarts: any

// ----- 图表容器 ref -----
const gaugeRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()

// ----- 数据 -----
const loading = ref(false)
const overview = ref<any>({})

// ----- 图表实例 -----
let gaugeChart: any = null
let barChart: any = null
let pieChart: any = null

// ----- 加载数据 -----
async function loadData() {
  loading.value = true
  try {
    const res: any = await getStatisticsOverview()
    overview.value = res
    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

function nextTick(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, 0))
}

// ==================== 仪表盘（办结率） ====================
function renderGauge() {
  if (!gaugeRef.value) return
  if (!gaugeChart) {
    gaugeChart = echarts.init(gaugeRef.value)
  }
  const rate = overview.value.completionRate ?? 0
  gaugeChart.setOption({
    series: [{
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0,
      max: 100,
      splitNumber: 10,
      axisLine: {
        lineStyle: {
          width: 18,
          color: [
            [0.3, '#e74c3c'],
            [0.6, '#f39c12'],
            [1, '#27ae60']
          ]
        }
      },
      pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '75%', width: 8 },
      axisTick: { distance: -18, length: 6 },
      splitLine: { distance: -22, length: 14 },
      axisLabel: { distance: 30, fontSize: 10 },
      detail: {
        valueAnimation: true,
        formatter: '{value}%',
        fontSize: 24,
        offsetCenter: [0, '70%']
      },
      title: { offsetCenter: [0, '92%'], fontSize: 13 },
      data: [{ value: Math.round(rate * 100) / 100, name: '办结率' }]
    }]
  })
}

// ==================== 柱状图（各状态数量） ====================
function renderBar() {
  if (!barRef.value) return
  if (!barChart) {
    barChart = echarts.init(barRef.value)
  }
  const dist = overview.value.statusDistribution ?? {}
  const statusMap: Record<string, string> = {
    draft: '草稿',
    submitted: '已提交',
    running: '运行中',
    completed: '已完成'
  }
  const keys = Object.keys(statusMap)
  const labels = keys.map(k => statusMap[k])
  const values = keys.map(k => dist[k] ?? 0)

  barChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: {
        color: (p: any) => ['#909399', '#409eff', '#e6a23c', '#67c23a'][p.dataIndex] ?? '#409eff',
        borderRadius: [4, 4, 0, 0]
      }
    }],
    grid: { left: 40, right: 20, top: 20, bottom: 30 }
  })
}

// ==================== 饼图（业务类型分布） ====================
function renderPie() {
  if (!pieRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieRef.value)
  }
  const bizList = overview.value.bizTypeDistribution ?? []
  const data = bizList.map((item: any) => ({
    name: item.bizTypeName,
    value: item.count
  }))

  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '55%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data
    }]
  })
}

// ----- 渲染所有 -----
function renderAll() {
  renderGauge()
  renderBar()
  renderPie()
}

// ----- 窗口缩放 -----
function onResize() {
  gaugeChart?.resize()
  barChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  gaugeChart?.dispose()
  barChart?.dispose()
  pieChart?.dispose()
})
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <!-- 概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">实例总数</div>
          <div class="stat-value">{{ overview.totalInstances ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">办结率</div>
          <div class="stat-value">{{ overview.completionRate ?? 0 }}<small>%</small></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">平均耗时</div>
          <div class="stat-value">{{ overview.avgDurationHours ?? 0 }}<small>h</small></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">异常实例</div>
          <div class="stat-value danger">{{ overview.anomalyCount ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="charts-row">
      <!-- 仪表盘：办结率 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">办结率仪表盘</span></template>
          <div ref="gaugeRef" class="chart-box"></div>
        </el-card>
      </el-col>

      <!-- 柱状图：各状态数量 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">各状态实例数量</span></template>
          <div ref="barRef" class="chart-box"></div>
        </el-card>
      </el-col>

      <!-- 饼图：业务类型分布 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">业务类型分布</span></template>
          <div ref="pieRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 4px;
}
.stat-cards {
  margin-bottom: 16px;
}
.stat-cards .stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.stat-cards .stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.stat-cards .stat-value small {
  font-size: 14px;
  font-weight: 500;
  color: #909399;
}
.stat-cards .stat-value.danger {
  color: #e74c3c;
}
.charts-row {
  margin-top: 4px;
}
.chart-box {
  width: 100%;
  height: 320px;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
}
</style>