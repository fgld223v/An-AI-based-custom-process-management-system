<template>
  <div class="page-shell optimize-page">
    <!-- 顶部 -->
    <div class="page-head">
      <div>
        <h1>AI 优化建议</h1>
        <p class="desc">AI 自动扫描全部业务流程，基于历史运行数据生成优化建议。请逐条审核后采纳或忽略。</p>
      </div>
      <div class="head-btns">
        <el-button :icon="Refresh" :loading="scanning" round @click="handleScan">重新扫描</el-button>
        <el-button type="success" :icon="Check" round @click="handleAdoptAll">全部采纳</el-button>
      </div>
    </div>

    <!-- 扫描进度 -->
    <div v-if="scanning" class="loading-card">
      <el-skeleton :rows="4" animated />
      <p class="loading-text">AI 正在采集历史数据并分析优化方案...</p>
    </div>

    <!-- 无数据 -->
    <div v-if="!scanning && allResults.length === 0 && scanned" class="empty-card">
      <el-empty description="暂无可分析的流程数据，请先运行一些流程实例" />
    </div>

    <!-- 三栏布局 -->
    <div v-if="!scanning && allResults.length > 0" class="opt-layout">
      <!-- 左：流程列表 -->
      <div class="left-col">
        <div class="col-title">流程列表</div>
        <div
          v-for="r in allResults"
          :key="r.templateId"
          class="tpl-card"
          :class="{ active: selectedTemplateId === r.templateId }"
          @click="selectTemplate(r.templateId)"
        >
          <div class="tpl-name">{{ r.templateName }}</div>
          <div class="tpl-meta">{{ r.suggestions?.length || 0 }} 条建议</div>
          <span v-if="hasHighSeverity(r)" class="tpl-badge-hot">{{ highCount(r) }} 条高优</span>
          <span v-else class="tpl-badge">{{ r.suggestions?.length || 0 }} 条建议</span>
        </div>
      </div>

      <!-- 中：建议卡片 -->
      <div class="center-col">
        <div class="col-title">优化建议（{{ selectedResult?.suggestions?.length || 0 }} 条）</div>

        <div v-if="!selectedResult" style="color:var(--muted);padding:40px;text-align:center">
          请从左侧选择一个流程查看优化建议
        </div>

        <div
          v-for="(s, i) in (selectedResult?.suggestions || [])"
          :key="i"
          class="scard"
          :class="{ applied: s._applied, ignored: s._ignored }"
          :style="{ borderLeftColor: sevColor(s.severity) }"
        >
          <div class="scard-h">
            <span class="stag" :style="{ background: typeBg(s.type), color: typeFg(s.type) }">
              {{ typeLabel(s.type) }}
            </span>
            <span class="sev-dot" :style="{ background: sevColor(s.severity) }"></span>
            <span class="sev-text">严重度：{{ sevLabel(s.severity) }}</span>
          </div>

          <div class="sdesc">{{ s.description }}</div>

          <div v-if="s._metrics" class="sdata">
            <span v-for="(v, k) in s._metrics" :key="k">
              {{ k }}：<span class="mono">{{ v }}</span>
            </span>
          </div>

          <div class="ssol">☞ {{ s.suggestion }}</div>

          <div v-if="s.expectedImprovement" class="simp">
            预计收益：{{ s.expectedImprovement }}
          </div>

          <div class="scard-actions" v-if="!s._applied && !s._ignored">
            <el-button type="success" size="small" :icon="Check" :loading="s._adopting" round @click="handleAdopt(i)">
              采纳
            </el-button>
            <el-button size="small" round @click="handleIgnore(i)">忽略</el-button>
            <el-button size="small" round @click="selectSuggestion(i)">查看变更</el-button>
          </div>
          <div v-else class="scard-result">
            <el-tag v-if="s._applied" type="success">已采纳</el-tag>
            <el-tag v-else type="info">已忽略</el-tag>
          </div>
        </div>
      </div>

      <!-- 右：版本变更对比 -->
      <div class="right-col">
        <div class="col-title">版本变更对比</div>
        <div v-if="!selectedSug" style="color:var(--muted);padding:20px;font-size:12px;text-align:center">
          点击建议卡片上的「查看变更」查看采纳前后的 BPMN/节点差异
        </div>

        <div v-else class="diff-panel">
          <div class="diff-r header-row">
            <span style="width:50px"></span>
            <div style="flex:1">采纳后 (建议)</div>
            <div style="flex:1">当前版本</div>
          </div>

          <div class="diff-r changed">
            <el-radio v-model="diffChoice" label="adopt" size="small">采纳</el-radio>
            <div style="flex:1" class="add">{{ selectedSug.suggestion }}</div>
            <div style="flex:1" class="rm">{{ selectedSug.description?.substring(0, 60) }}...</div>
          </div>

          <div v-for="(d, i) in diffLines" :key="i" class="diff-r" :class="{ changed: d.changed }">
            <el-radio v-model="diffChoice" :label="'keep' + i" size="small" :disabled="!d.changed">保留</el-radio>
            <div style="flex:1">{{ d.after }}</div>
            <div style="flex:1" :class="{ rm: d.changed }">{{ d.before }}</div>
          </div>

          <div style="margin-top:12px;display:flex;gap:8px">
            <el-button type="success" size="small" round style="flex:1" @click="handleAdopt(selectedSugIdx)">应用选定变更</el-button>
            <el-button size="small" round @click="selectedSug = null">取消</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * AiOptimize - AI 流程优化建议页面
 *
 * 三栏布局：左侧流程列表 -> 中间优化建议卡片 -> 右侧版本变更对比。
 * 支持逐条采纳/忽略、全部采纳、查看变更对比。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { optimizeAll, adoptSuggestion } from '@/api/ai'

/** 单条优化建议 */
interface Suggestion {
  type: string; nodeKey?: string; nodeName?: string; severity: string
  description: string; suggestion: string; expectedImprovement?: string
  _metrics?: Record<string, string>  // 关联指标数据
  _applied?: boolean; _ignored?: boolean; _adopting?: boolean  // 前端状态标记
}
/** 单个流程的分析结果 */
interface AnalysisResult {
  templateId: number; templateName: string; analysis: string
  suggestions: Suggestion[]; metrics: any
}

// ---- 页面状态 ----
const scanning = ref(false)                    // 是否正在扫描
const scanned = ref(false)                     // 是否已扫描过
const allResults = ref<AnalysisResult[]>([])   // 全部流程分析结果
const selectedTemplateId = ref<number | null>(null)  // 当前选中的流程模板
const selectedSug = ref<Suggestion | null>(null)     // 当前查看的建议
const selectedSugIdx = ref(-1)                       // 当前建议索引
const diffChoice = ref('keep')                       // 变更对比选择

/** 根据 selectedTemplateId 获取当前选中流程的分析结果 */
const selectedResult = computed(() =>
  allResults.value.find(r => r.templateId === selectedTemplateId.value) || null
)

/** 模拟版本变更差异行（后端应返回 before/after 结构） */
const diffLines = computed(() => {
  if (!selectedSug.value) return []
  const s = selectedSug.value
  return [
    { changed: false, after: s.nodeName || '—', before: s.nodeName || '—' },
    { changed: true, after: '已优化（采纳 AI 建议）', before: s.description?.substring(0, 50) || '—' }
  ]
})

/** 页面加载时自动触发扫描 */
onMounted(() => handleScan())

/** 扫描全部流程，获取优化建议 */
async function handleScan() {
  scanning.value = true; scanned.value = true
  try {
    allResults.value = await optimizeAll() as AnalysisResult[]
    if (allResults.value.length > 0) {
      selectedTemplateId.value = allResults.value[0].templateId
    }
    ElMessage.success(`扫描完成，${allResults.value.length} 个流程有待优化建议`)
  } catch (e: any) {
    ElMessage.error(e?.message || '扫描失败')
  } finally { scanning.value = false }
}

/** 选中流程模板 */
function selectTemplate(id: number) { selectedTemplateId.value = id; selectedSug.value = null }
/** 选中某条建议查看变更 */
function selectSuggestion(i: number) {
  if (!selectedResult.value) return
  selectedSug.value = selectedResult.value.suggestions[i]
  selectedSugIdx.value = i
}

/** 判断流程是否有高严重度建议 */
function hasHighSeverity(r: AnalysisResult) { return r.suggestions?.some(s => s.severity === 'high') }
/** 统计高严重度建议数量 */
function highCount(r: AnalysisResult) { return r.suggestions?.filter(s => s.severity === 'high').length }

/** 采纳单条优化建议 */
async function handleAdopt(i: number) {
  if (!selectedResult.value) return
  const s = selectedResult.value.suggestions[i]
  s._adopting = true
  try {
    await adoptSuggestion(selectedResult.value.templateId, s.type, s.nodeKey, s.suggestion)
    s._applied = true
    ElMessage.success('已采纳优化建议')
  } catch (e: any) {
    ElMessage.error(e?.message || '采纳失败')
  } finally { s._adopting = false }
}

/** 忽略单条建议 */
function handleIgnore(i: number) {
  if (!selectedResult.value) return
  selectedResult.value.suggestions[i]._ignored = true
}

/** 全部采纳当前流程的所有未处理建议 */
async function handleAdoptAll() {
  if (!selectedResult.value) return
  let count = 0
  for (let i = 0; i < selectedResult.value.suggestions.length; i++) {
    const s = selectedResult.value.suggestions[i]
    if (!s._applied && !s._ignored) {
      try { await adoptSuggestion(selectedResult.value.templateId, s.type, s.nodeKey, s.suggestion); s._applied = true; count++ }
      catch { /* continue */ }
    }
  }
  ElMessage.success(`已采纳 ${count} 条建议`)
}

function sevLabel(s: string) { const m: Record<string, string> = { high: '高', medium: '中', low: '低' }; return m[s] || s }
function sevColor(s: string) { const m: Record<string, string> = { high: '#c4503a', medium: '#c4953a', low: '#3a8f7d' }; return m[s] || '#888' }
function typeLabel(t: string) {
  const m: Record<string, string> = {
    redundant_node: '冗余节点检测', bottleneck: '低效节点分析',
    approval_optimization: '审批优化', branch_optimization: '分支优化',
    permission_optimization: '权限调整', duplicate_approval: '重复审批检测'
  }; return m[t] || t
}
function typeBg(t: string) {
  const m: Record<string, string> = { redundant_node: '#3a7dc418', bottleneck: '#c4953a20', duplicate_approval: '#8e4ec918', approval_optimization: '#4a9e6b18', branch_optimization: '#3a8f7d18', permission_optimization: '#c4503a18' }
  return m[t] || '#eee'
}
function typeFg(t: string) {
  const m: Record<string, string> = { redundant_node: '#3a7dc4', bottleneck: '#9e7530', duplicate_approval: '#8e4ec9', approval_optimization: '#4a9e6b', branch_optimization: '#3a8f7d', permission_optimization: '#c4503a' }
  return m[t] || '#666'
}
</script>

<style scoped>
.optimize-page { max-width: 1200px; margin: 0 auto; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.page-head h1 { font-size: 25px; font-weight: 600; margin-bottom: 5px; }
.desc { color: var(--muted); font-size: 13px; max-width: 600px; }
.head-btns { display: flex; gap: 8px; white-space: nowrap; }
.loading-card { padding: 28px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); box-shadow: var(--shadow); }
.loading-text { margin: 20px 0 0; text-align: center; color: var(--muted); font-size: 13px; }
.empty-card { padding: 48px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); box-shadow: var(--shadow); }

.opt-layout { display: grid; grid-template-columns: 220px 1fr 260px; gap: 16px; }
.col-title { font-size: 12px; font-weight: 700; color: var(--muted); margin-bottom: 10px; text-transform: uppercase; letter-spacing: .1em; }

/* 左栏 - 流程卡片 */
.left-col { display: flex; flex-direction: column; gap: 8px; }
.tpl-card { padding: 12px 14px; border: 1px solid var(--line); border-radius: 9px; background: var(--panel); cursor: pointer; transition: .15s; }
.tpl-card:hover { border-color: var(--el-color-primary); }
.tpl-card.active { border: 2px solid var(--el-color-success); }
.tpl-name { font-weight: 600; font-size: 13px; }
.tpl-meta { font-size: 11px; color: var(--muted); margin-top: 2px; }
.tpl-badge { display: inline-block; margin-top: 4px; font-size: 10px; padding: 1px 7px; border-radius: 10px; font-weight: 600; background: var(--el-color-success-light-9); color: var(--el-color-success); }
.tpl-badge-hot { display: inline-block; margin-top: 4px; font-size: 10px; padding: 1px 7px; border-radius: 10px; font-weight: 600; background: #fde2e2; color: #c4503a; }

/* 中栏 - 建议卡片 */
.center-col { display: flex; flex-direction: column; gap: 12px; }
.scard { padding: 16px; border-radius: 9px; background: var(--panel); border: 1px solid var(--line); border-left: 3px solid #888; position: relative; }
.scard.applied { opacity: .6; }
.scard.ignored { opacity: .5; }
.scard-h { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.stag { font-size: 10px; font-weight: 600; padding: 2px 10px; border-radius: 4px; }
.sev-dot { width: 6px; height: 6px; border-radius: 50%; }
.sev-text { font-size: 11px; color: var(--muted); }
.sdesc { font-size: 13px; line-height: 1.6; margin-bottom: 8px; }
.sdata { font-size: 11px; color: var(--muted); margin-bottom: 8px; display: flex; gap: 18px; flex-wrap: wrap; }
.ssol { font-size: 12px; color: var(--el-color-success); margin-bottom: 6px; }
.simp { font-size: 12px; color: var(--el-color-warning); margin-bottom: 10px; font-weight: 500; }
.scard-actions { display: flex; gap: 8px; }
.scard-result { margin-top: 4px; }
.mono { font-family: monospace; }

/* 右栏 - 版本变更 */
.right-col { }
.diff-panel { padding: 14px; border: 1px solid var(--line); border-radius: 9px; background: var(--panel); }
.diff-r { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-bottom: 1px solid var(--line); font-size: 12px; }
.diff-r.header-row { font-size: 10px; color: var(--muted); border-bottom: 1px solid var(--line); }
.diff-r.changed { background: #fef0f0; border-radius: 4px; margin-bottom: 2px; }
.add { color: var(--el-color-success); }
.rm { color: #c4503a; text-decoration: line-through; }
</style>
