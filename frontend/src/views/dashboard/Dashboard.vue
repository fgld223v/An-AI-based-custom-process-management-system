<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStatisticsOverview, getStatisticsTrend, getNodeEfficiency } from '@/api/statistics'

declare const echarts: any

const gaugeRef = ref<HTMLDivElement>()
const barRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const trendRef = ref<HTMLDivElement>()

const router = useRouter()
const loading = ref(false)
const overview = ref<any>({})
const nodeRankings = ref<any[]>([])
const trendRange = ref<'7d' | '30d' | '90d' | 'custom'>('30d')
const trendDateLabel = ref('')
const customDates = ref<[Date, Date] | null>(null)

let gaugeChart: any = null
let barChart: any = null
let pieChart: any = null
let trendChart: any = null

// ----- 时间范围 -----
function fmtLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function rangeDates(): { start: string; end: string } {
  if (trendRange.value === 'custom' && customDates.value) {
    return { start: fmtLocal(customDates.value[0]), end: fmtLocal(customDates.value[1]) }
  }
  const end = new Date()
  const start = new Date()
  const days = trendRange.value === '7d' ? 7 : trendRange.value === '90d' ? 90 : 30
  start.setDate(start.getDate() - days + 1)
  return { start: fmtLocal(start), end: fmtLocal(end) }
}

// 监听自定义日期选择
watch(customDates, (val) => {
  if (val) {
    trendRange.value = 'custom'
    loadTrend()
  }
})

// 禁止选择未来日期
function disableFuture(date: Date): boolean {
  return date.getTime() > Date.now()
}

// ----- 加载概览 -----
async function loadOverview() {
  const res: any = await getStatisticsOverview()
  overview.value = res
  await nextTick()
  renderGauge()
  renderBar()
  renderPie()
}

// ----- 加载趋势 -----
async function loadTrend() {
  const { start, end } = rangeDates()
  trendDateLabel.value = `${start} ~ ${end}`
  try {
    const res: any = await getStatisticsTrend({ start, end, granularity: 'day', mode: 'summary' })
    renderTrend(res)
  } catch { /* empty */ }
}

// ----- 加载节点效率 -----
async function loadNodeEfficiency() {
  try {
    const res: any = await getNodeEfficiency()
    nodeRankings.value = res?.rankings ?? []
  } catch { /* empty */ }
}

// ----- 点击节点行 → 跳转实例列表 -----
function goToInstances(nodeKey: string) {
  router.push({ path: '/process/instances', query: { nodeKey } })
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadTrend(), loadNodeEfficiency()])
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
      data: bizList.map((item: any) => ({ name: item.bizTypeName, value: item.count }))
    }]
  })
}

// ==================== 折线图（发起量 / 办结量趋势） ====================
function renderTrend(data: any) {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const labels = data?.labels ?? []
  const series = data?.series ?? []
  const initLine = series.find((s: any) => s.bizTypeName === '发起量')
  const doneLine = series.find((s: any) => s.bizTypeName === '办结量')

  // 短标签用于 x 轴：去掉年份简洁显示
  const shortLabels = labels.map((l: string) => l.length > 5 ? l.slice(5) : l)
  // 7天内显示全部，30天每3个显示1个，90天每7个显示1个
  const interval = labels.length > 60 ? 6 : labels.length > 20 ? 2 : 0

  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        const idx = params[0]?.dataIndex ?? 0
        const fullDate = labels[idx] ?? ''
        let html = `<b>${fullDate}</b><br/>`
        for (const p of params) {
          html += `${p.marker} ${p.seriesName}: ${p.value}<br/>`
        }
        return html
      }
    },
    legend: { data: ['发起量', '办结量'], bottom: 0 },
    xAxis: {
      type: 'category',
      data: shortLabels,
      axisLabel: { interval }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '发起量', type: 'line', data: initLine?.values ?? [],
        smooth: true, symbol: 'circle', symbolSize: 5,
        lineStyle: { width: 2, color: '#409eff' },
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64,158,255,0.1)' }
      },
      {
        name: '办结量', type: 'line', data: doneLine?.values ?? [],
        smooth: true, symbol: 'circle', symbolSize: 5,
        lineStyle: { width: 2, color: '#67c23a' },
        itemStyle: { color: '#67c23a' },
        areaStyle: { color: 'rgba(103,194,58,0.1)' }
      }
    ],
    grid: { left: 50, right: 30, top: 20, bottom: 40 }
  })
}

// ----- resize -----
function onResize() {
  gaugeChart?.resize()
  barChart?.resize()
  pieChart?.resize()
  trendChart?.resize()
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
  trendChart?.dispose()
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

    <!-- 图表区：仪表盘 + 柱状图 + 饼图 -->
    <el-row :gutter="16" class="charts-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">办结率仪表盘</span></template>
          <div ref="gaugeRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">各状态实例数量</span></template>
          <div ref="barRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">业务类型分布</span></template>
          <div ref="pieRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势折线图（全宽） -->
    <el-card shadow="hover" class="trend-card">
      <template #header>
        <div class="trend-header">
          <div>
            <span class="card-title">发起量 / 办结量 趋势</span>
            <span class="date-range">{{ trendDateLabel }}</span>
          </div>
          <div class="trend-controls">
            <el-radio-group v-model="trendRange" size="small" @change="loadTrend">
              <el-radio-button value="7d">7天</el-radio-button>
              <el-radio-button value="30d">30天</el-radio-button>
              <el-radio-button value="90d">90天</el-radio-button>
            </el-radio-group>
            <el-date-picker
              v-model="customDates"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              size="small"
              :disabled-date="disableFuture"
              style="width: 240px; margin-left: 8px"
            />
          </div>
        </div>
      </template>
      <div ref="trendRef" class="trend-box"></div>
    </el-card>

    <!-- 节点耗时排名表 -->
    <el-card shadow="hover" class="node-table-card">
      <template #header>
        <span class="card-title">节点耗时分析</span>
      </template>
      <el-table
        :data="nodeRankings"
        stripe
        highlight-current-row
        @row-click="(row: any) => goToInstances(row.nodeKey)"
        style="cursor: pointer; width: 100%"
      >
        <el-table-column type="index" label="排名" width="60" />
        <el-table-column prop="nodeName" label="节点名称" min-width="150" />
        <el-table-column prop="totalCount" label="总任务数" width="100" align="center" />
        <el-table-column prop="timeoutCount" label="超时数" width="80" align="center" />
        <el-table-column label="超时率" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.timeoutRate > 20 }">
              {{ row.timeoutRate }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="平均耗时" width="120" align="center">
          <template #default="{ row }">
            {{ row.avgDwellHours }}h
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard { padding: 4px; }
.stat-cards { margin-bottom: 16px; }
.stat-cards .stat-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.stat-cards .stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-cards .stat-value small { font-size: 14px; font-weight: 500; color: #909399; }
.stat-cards .stat-value.danger { color: #e74c3c; }
.charts-row { margin-top: 4px; }
.chart-box { width: 100%; height: 320px; }
.trend-card { margin-top: 16px; }
.trend-box { width: 100%; height: 360px; }
.trend-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 600; font-size: 15px; }
.date-range { margin-left: 12px; font-size: 12px; color: #909399; font-weight: 400; }
.trend-controls { display: flex; align-items: center; }
.node-table-card { margin-top: 16px; }
.text-danger { color: #e74c3c; font-weight: 700; }
</style>