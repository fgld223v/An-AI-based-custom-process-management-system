<template>
  <div class="page-shell ai-generate-page">
    <div class="page-header">
      <h1>AI 智能表单生成</h1>
      <p>用自然语言描述表单需求，AI 将自动生成对应的字段配置</p>
    </div>

    <div class="input-card">
      <el-input
        v-model="description"
        type="textarea"
        :rows="5"
        placeholder="例如：生成一个请假表单，包含请假类型（事假/病假/年假）、开始时间、结束时间、请假天数、请假原因"
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
          {{ generating ? 'AI 正在生成表单...' : '生成表单' }}
        </el-button>
        <el-button :disabled="generating" size="large" round @click="handleClear">
          清空
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="generating" class="loading-card">
      <el-skeleton :rows="6" animated />
      <p class="loading-text">AI 正在分析需求并生成表单字段...</p>
    </div>

    <!-- 结果 -->
    <div v-if="!generating && result" class="result-section">
      <div class="result-grid">
        <!-- 左：字段列表 -->
        <div class="result-card">
          <div class="card-title">字段列表（{{ fieldList.length }} 个字段）</div>
          <el-table :data="fieldList" stripe size="default">
            <el-table-column prop="fieldName" label="字段名" width="140" />
            <el-table-column prop="fieldLabel" label="显示名" width="120" />
            <el-table-column prop="fieldType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.fieldType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="required" label="必填" width="70">
              <template #default="{ row }">
                <span :style="{ color: row.required ? '#c4503a' : 'var(--muted)' }">
                  {{ row.required ? '是' : '否' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右：表单预览 -->
        <div class="result-card">
          <div class="card-title">表单预览</div>
          <DynamicFormRenderer :field-list="result.fieldList" :readonly="true" />
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="result-actions">
        <el-button type="primary" size="large" round @click="handleCreateForm">
          确认创建表单
        </el-button>
        <el-button size="large" round @click="handleRegenerate">
          重新生成
        </el-button>
      </div>
    </div>

    <!-- 创建表单弹窗 -->
    <el-dialog v-model="createDialogVisible" title="确认创建表单" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="表单名称">
          <el-input v-model="createForm.formName" placeholder="输入表单名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="表单编号">
          <el-input v-model="createForm.formCode" placeholder="自动生成或手动输入" maxlength="64" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="createForm.bizTypeId" placeholder="选择业务类型" style="width: 100%">
            <el-option v-for="bt in bizTypeList" :key="bt.id" :label="bt.typeName" :value="bt.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreateForm">确认创建</el-button>
      </template>
    </el-dialog>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon closable
      class="error-alert" @close="errorMessage = ''" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { createForm } from '@/api/formDefinition'
import { getBizTypes } from '@/api/bizType'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'ai-generate-form-state'
const router = useRouter()

interface FieldItem {
  fieldName: string
  fieldLabel: string
  fieldType: string
  required: boolean
  options?: string[]
}

interface GenerateFormResult {
  fieldList: string
  formSchema: string
}

interface BizType {
  id: number
  typeName: string
  typeCode: string
}

const description = ref('')
const generating = ref(false)
const result = ref<GenerateFormResult | null>(null)
const errorMessage = ref('')
const createDialogVisible = ref(false)
const creating = ref(false)
const bizTypeList = ref<BizType[]>([])
const createForm = ref({
  formName: '',
  formCode: '',
  bizTypeId: null as number | null
})

// 解析 fieldList JSON 字符串为数组
const fieldList = ref<FieldItem[]>([])

watch(() => result.value, () => {
  if (result.value?.fieldList) {
    try {
      fieldList.value = typeof result.value.fieldList === 'string'
        ? JSON.parse(result.value.fieldList)
        : result.value.fieldList
    } catch {
      fieldList.value = []
    }
  }
})

// sessionStorage 持久化
function saveState() {
  const state = { description: description.value, result: result.value }
  if (state.description || state.result) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  }
}
watch([description, result], () => saveState(), { deep: true })
onMounted(() => {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (raw) {
    try {
      const state = JSON.parse(raw)
      description.value = state.description || ''
      result.value = state.result || null
    } catch { /* ignore */ }
  }
})

async function handleGenerate() {
  const text = description.value.trim()
  if (!text) { ElMessage.warning('请先描述您想要的表单'); return }
  generating.value = true
  errorMessage.value = ''
  result.value = null
  try {
    const { generateForm } = await import('@/api/ai')
    const data = await generateForm(text)
    result.value = data
    ElMessage.success('表单生成成功')
  } catch (e: any) {
    errorMessage.value = e?.response?.data?.message || e?.message || '生成失败，请检查后端服务'
  } finally {
    generating.value = false
  }
}

async function handleCreateForm() {
  if (!result.value) return
  createForm.value = { formName: 'AI生成表单', formCode: 'ai-' + Date.now(), bizTypeId: null }
  try { bizTypeList.value = await getBizTypes() || [] } catch { /* ignore */ }
  createDialogVisible.value = true
}

async function submitCreateForm() {
  if (!result.value) return
  creating.value = true
  try {
    await createForm({
      formName: createForm.value.formName,
      formCode: createForm.value.formCode,
      bizTypeId: createForm.value.bizTypeId ?? undefined,
      fieldList: result.value.fieldList,
      formSchema: result.value.formSchema
    } as any)
    createDialogVisible.value = false
    ElMessage.success('表单创建成功')
    router.push('/form-designer')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function handleRegenerate() { result.value = null; errorMessage.value = '' }
function handleClear() {
  description.value = ''
  result.value = null
  errorMessage.value = ''
  sessionStorage.removeItem(STORAGE_KEY)
}
</script>

<style scoped>
.ai-generate-page { max-width: 1000px; margin: 0 auto; }
.page-header { margin-bottom: 28px; text-align: center; }
.page-header h1 { margin: 0 0 10px; font-size: 28px; font-weight: 800; color: var(--text); letter-spacing: 0; }
.page-header p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.input-card { padding: 28px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.input-actions { display: flex; gap: 12px; margin-top: 20px; justify-content: center; }
.loading-card { margin-top: 22px; padding: 28px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.loading-text { margin: 20px 0 0; text-align: center; color: var(--muted); font-size: 13px; }
.result-section { margin-top: 22px; display: flex; flex-direction: column; gap: 16px; }
.result-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(300px, 0.85fr); gap: 16px; align-items: start; }
.result-card { padding: 24px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.card-title { font-size: 15px; font-weight: 700; color: var(--text); margin-bottom: 14px; }
.result-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; padding-top: 8px; }
.error-alert { margin-top: 22px; }
@media (max-width: 900px) { .result-grid { grid-template-columns: 1fr; } }
</style>
