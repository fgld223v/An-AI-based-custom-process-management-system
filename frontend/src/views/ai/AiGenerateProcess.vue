<template>
  <div class="page-shell ai-generate-page">
    <div class="page-header">
      <h1>AI 智能流程生成</h1>
      <p>用自然语言描述你的业务流程，AI 将自动生成对应的 BPMN 流程图和节点配置</p>
    </div>

    <div class="input-card">
      <el-input
        v-model="description"
        type="textarea"
        :rows="6"
        placeholder="例如：创建一个请假审批流程，3 天内自动通过，超过 3 天需要部门经理审批，超过 7 天需要总经理审批"
        :disabled="generating"
        maxlength="2000"
        show-word-limit
        resize="none"
      />
      <div class="input-actions">
        <el-button
          type="success"
          :icon="MagicStick"
          :loading="generating"
          size="large"
          round
          @click="handleGenerate"
        >
          {{ generating ? 'AI 正在解析您的需求...' : '生成流程' }}
        </el-button>
        <el-button
          :disabled="generating"
          size="large"
          round
          @click="handleClear"
        >
          清空
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="generating" class="loading-card">
      <div class="loading-header">
        <el-icon class="loading-spin" :size="32"><MagicStick /></el-icon>
        <h3>AI 正在生成流程</h3>
      </div>
      <el-steps :active="currentStep" align-center finish-status="success" class="loading-steps">
        <el-step title="解析需求" description="分析业务场景" />
        <el-step title="生成 BPMN" description="构建流程模型" />
        <el-step title="配置节点" description="生成审批规则" />
        <el-step title="渲染预览" description="准备可视化展示" />
      </el-steps>
      <p class="loading-text">
        已等待 <strong>{{ elapsedSeconds }}</strong> 秒 · {{ stageMessages[currentStep] || '正在处理...' }}
      </p>
    </div>

    <!-- 生成结果 -->
    <div v-if="!generating && result" class="result-section">
      <!-- 左右分栏 -->
      <div class="result-grid">
        <!-- 左：BPMN 流程图 -->
        <div class="result-card viewer-card">
          <div class="card-title">流程图预览</div>
          <BpmnViewerPanel :bpmn-xml="result.bpmnXml" />
        </div>

        <!-- 右：摘要 + 节点配置 -->
        <div class="result-right">
          <!-- 摘要 -->
          <div class="result-card summary-card">
            <div class="card-title">流程摘要</div>
            <p>{{ result.summary }}</p>
          </div>

          <!-- 节点配置 -->
          <div class="result-card">
            <div class="card-title">节点配置（{{ result.nodeConfig?.length || 0 }} 个节点）</div>
            <el-table :data="result.nodeConfig" stripe size="default">
              <el-table-column prop="nodeKey" label="节点 Key" width="180" />
              <el-table-column prop="nodeName" label="节点名称" width="140" />
              <el-table-column prop="businessType" label="业务类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="businessTypeTag(row.businessType)" effect="plain" size="small">
                    {{ row.businessType }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- BPMN XML 折叠 -->
          <el-collapse>
            <el-collapse-item title="BPMN XML 源码" name="xml">
              <div class="xml-box">
                <pre>{{ result.bpmnXml }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="result-actions">
        <el-button type="success" :icon="Edit" size="large" round @click="handleOpenInDesigner">
          在流程编辑器中打开
        </el-button>
        <el-button type="primary" size="large" round @click="handleCreateTemplate">
          确认创建模板
        </el-button>
        <el-button size="large" round @click="handleRegenerate">
          重新生成
        </el-button>
      </div>
    </div>

    <!-- 创建模板弹窗 -->
    <el-dialog v-model="createDialogVisible" title="确认创建模板" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="模板名称">
          <el-input v-model="createForm.templateName" placeholder="输入模板名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="模板编号">
          <el-input v-model="createForm.templateCode" placeholder="自动生成或手动输入" maxlength="64" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="createForm.bizTypeId" placeholder="选择业务类型" style="width: 100%">
            <el-option v-for="bt in bizTypeList" :key="bt.id" :label="bt.typeName" :value="bt.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreateTemplate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 错误提示 -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      class="error-alert"
      @close="errorMessage = ''"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Edit } from '@element-plus/icons-vue'
import BpmnViewerPanel from '@/components/ai/BpmnViewerPanel.vue'
import type { AiGenerateProcessResult } from '@/api/ai'
import { createProcessTemplate } from '@/api/processTemplate'
import { createMyProcess } from '@/api/myProcess'
import { getBizTypes } from '@/api/bizType'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'ai-generate-process-state'
const router = useRouter()
const authStore = useAuthStore()

interface BizType {
  id: number
  typeName: string
  typeCode: string
}

const description = ref('')
const generating = ref(false)
const result = ref<AiGenerateProcessResult | null>(null)
const errorMessage = ref('')
const currentStep = ref(0)
const elapsedSeconds = ref(0)
let progressTimer: ReturnType<typeof setInterval> | null = null
const stageMessages = ['正在分析您的业务需求...', '正在构建 BPMN 流程图...', '正在配置节点审批规则...', '即将完成，准备渲染预览...']
const createDialogVisible = ref(false)
const creating = ref(false)
const bizTypeList = ref<BizType[]>([])
const createForm = ref({
  templateName: '',
  templateCode: '',
  bizTypeId: null as number | null
})

// 页面离开时保存状态
function saveState() {
  const state = {
    description: description.value,
    result: result.value
  }
  if (state.description || state.result) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  }
}

function restoreState() {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (raw) {
    try {
      const state = JSON.parse(raw)
      description.value = state.description || ''
      result.value = state.result || null
    } catch { /* ignore */ }
  }
}

// 监听变化自动保存
watch([description, result], () => saveState(), { deep: true })

// 浏览器关闭/刷新前保存
window.addEventListener('beforeunload', saveState)

onMounted(() => restoreState())

function businessTypeTag(type: string) {
  const map: Record<string, string> = {
    start: 'info',
    approval: '',
    condition: 'warning',
    end: 'success'
  }
  return map[type] || 'info'
}

async function handleGenerate() {
  const text = description.value.trim()
  if (!text) {
    ElMessage.warning('请先描述您想要的业务流程')
    return
  }

  generating.value = true
  errorMessage.value = ''
  result.value = null
  currentStep.value = 0
  elapsedSeconds.value = 0

  // 进度模拟：每 3~6 秒推进一个步骤
  progressTimer = setInterval(() => {
    elapsedSeconds.value++
    if (elapsedSeconds.value >= 4 && currentStep.value === 0) currentStep.value = 1
    if (elapsedSeconds.value >= 8 && currentStep.value === 1) currentStep.value = 2
    if (elapsedSeconds.value >= 12 && currentStep.value === 2) currentStep.value = 3
  }, 1000)

  try {
    const { generateProcess } = await import('@/api/ai')
    const data = await generateProcess(text)
    result.value = data
    currentStep.value = 4 // 全部完成
    ElMessage.success('流程生成成功')
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '生成失败，请检查后端服务是否启动'
    errorMessage.value = msg
  } finally {
    if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
    generating.value = false
  }
}

function handleOpenInDesigner() {
  if (!result.value?.bpmnXml) return
  window.sessionStorage.setItem('ai-generated-bpmn', result.value.bpmnXml)
  // 保存 nodeConfig 以便设计器恢复
  window.sessionStorage.setItem('ai-generated-nodeconfig', JSON.stringify(result.value.nodeConfig || []))
  router.push('/process-designer?from=ai')
}

async function handleCreateTemplate() {
  if (!result.value) return
  // 预填模板名（从摘要取前 20 字）
  createForm.value = {
    templateName: result.value.summary?.slice(0, 20) || 'AI生成流程',
    templateCode: 'ai-' + Date.now(),
    bizTypeId: null
  }
  // 加载业务类型列表
  try {
    bizTypeList.value = await getBizTypes() || []
  } catch { /* 列表加载失败不阻塞弹窗 */ }
  createDialogVisible.value = true
}

async function submitCreateTemplate() {
  if (!result.value) return
  // 前端校验模板名称
  if (!createForm.value.templateName?.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  creating.value = true
  try {
    const createApi = authStore.user?.systemRole === 'biz_admin' ? createMyProcess : createProcessTemplate
    await createApi({
      templateName: createForm.value.templateName,
      templateCode: createForm.value.templateCode,
      bizTypeId: createForm.value.bizTypeId ?? undefined,
      sourceType: 'ai_generated',
      bpmnXml: result.value.bpmnXml,
      nodeConfig: JSON.stringify(result.value.nodeConfig)
    } as any)
    createDialogVisible.value = false
    ElMessage.success(authStore.user?.systemRole === 'biz_admin' ? '流程已创建到我的流程' : '模板创建成功')
    router.push(authStore.user?.systemRole === 'biz_admin' ? '/my-processes' : '/templates')
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '创建失败'
    ElMessage.error(msg)
  } finally {
    creating.value = false
  }
}

function handleRegenerate() {
  result.value = null
  errorMessage.value = ''
}

function handleClear() {
  description.value = ''
  result.value = null
  errorMessage.value = ''
  sessionStorage.removeItem(STORAGE_KEY)
}
</script>

<style scoped>
.ai-generate-page {
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 28px;
  text-align: center;
}

.page-header h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 800;
  color: var(--text);
  letter-spacing: 0;
}

.page-header p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.input-card {
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.input-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  justify-content: center;
}

.loading-card {
  margin-top: 22px;
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.loading-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  justify-content: center;
}
.loading-header h3 { margin: 0; font-size: 18px; }
.loading-spin { color: var(--el-color-success); animation: spin 1.5s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.loading-steps { margin-bottom: 20px; }
.loading-text {
  text-align: center;
  color: var(--muted);
  font-size: 14px;
}
.loading-text strong { color: var(--el-color-success); font-family: var(--fm, monospace); }

.result-section {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 16px;
  align-items: start;
}

.result-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-card {
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.viewer-card {
  padding: 16px;
}

.result-card :deep(.el-collapse) {
  border: none;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 14px;
}

.summary-card p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.xml-box {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #101a16;
}

.xml-box pre {
  margin: 0;
  padding: 16px;
  color: #c6f6e3;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  padding-top: 8px;
}

@media (max-width: 900px) {
  .result-grid {
    grid-template-columns: 1fr;
  }
}

.error-alert {
  margin-top: 22px;
}
</style>
