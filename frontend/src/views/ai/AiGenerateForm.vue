<template>
  <div class="page-shell ai-form-page">
    <div class="page-header">
      <h1>AI 智能表单生成</h1>
      <p>用自然语言描述你需要采集的数据，AI 将自动生成对应的表单字段和校验规则</p>
    </div>

    <div class="input-card">
      <el-input
        v-model="description"
        type="textarea"
        :rows="5"
        placeholder="例如：创建一个员工入职信息登记表，需要姓名、手机号、入职日期、所属部门（下拉选择）、紧急联系人、备注"
        :disabled="generating"
        maxlength="2000"
        show-word-limit
        resize="none"
      />
      <div class="input-actions">
        <el-button type="success" :icon="MagicStick" :loading="generating" size="large" round @click="handleGenerate">
          {{ generating ? 'AI 正在生成表单...' : '生成表单' }}
        </el-button>
        <el-button :disabled="generating" size="large" round @click="handleClear">清空</el-button>
      </div>
    </div>

    <div v-if="generating" class="loading-card">
      <el-skeleton :rows="6" animated />
      <p class="loading-text">AI 正在分析您的需求并生成表单字段配置，请稍候...</p>
    </div>

    <div v-if="!generating && result" class="result-section">
      <div class="result-card summary-card">
        <div class="card-title">表单信息</div>
        <div class="summary-row">
          <span class="label">名称</span><strong>{{ result.formName }}</strong>
        </div>
        <div class="summary-row">
          <span class="label">编码</span><code>{{ result.formCode }}</code>
        </div>
        <div class="summary-row">
          <span class="label">字段数</span><strong>{{ result.fields?.length || 0 }}</strong>
        </div>
        <p class="summary-text">{{ result.summary }}</p>
      </div>

      <div class="result-card">
        <div class="card-title">字段配置（{{ result.fields?.length || 0 }} 个字段）</div>
        <el-table :data="result.fields" stripe size="default">
          <el-table-column prop="field" label="字段标识" width="160" />
          <el-table-column prop="label" label="显示名称" min-width="120" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="typeTag(row.type)" size="small" effect="plain">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="必填" width="70">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'" size="small" effect="plain">
                {{ row.required ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="placeholder" label="占位提示" min-width="150" />
          <el-table-column label="选项" min-width="140">
            <template #default="{ row }">
              <span v-if="row.options?.length">{{ row.options.map((o: any) => o.label).join('、') }}</span>
              <span v-else style="color:var(--muted)">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="result-card">
        <div class="card-title">表单 Schema JSON</div>
        <div class="xml-box"><pre>{{ JSON.stringify(result, null, 2) }}</pre></div>
      </div>

      <div class="result-actions">
        <el-button type="success" :icon="Edit" size="large" round @click="handleOpenDesigner">
          在表单设计器中打开
        </el-button>
        <el-button size="large" round @click="handleRegenerate">重新生成</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon closable class="error-alert" @close="errorMessage = ''" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { generateForm } from '@/api/ai'

interface FormField {
  field: string; label: string; type: string
  required: boolean; placeholder: string
  options?: { label: string; value: string }[]
}

interface GenerateFormResult {
  formName: string; formCode: string
  fields: FormField[]; summary: string
}

const router = useRouter()
const description = ref('')
const generating = ref(false)
const result = ref<GenerateFormResult | null>(null)
const errorMessage = ref('')

function typeTag(type: string) {
  const m: Record<string, string> = { text: '', number: 'warning', date: 'success', datetime: 'success', select: 'primary', radio: 'primary', checkbox: 'primary', textarea: 'info', upload: 'danger' }
  return m[type] || 'info'
}

async function handleGenerate() {
  if (!description.value.trim()) { ElMessage.warning('请先描述您想要的表单'); return }
  generating.value = true; errorMessage.value = ''; result.value = null
  try {
    result.value = await generateForm(description.value) as GenerateFormResult
    ElMessage.success('表单生成成功')
  } catch (e: any) {
    errorMessage.value = e?.message || '生成失败，请检查后端服务是否启动'
  } finally { generating.value = false }
}

function handleOpenDesigner() {
  if (!result.value) return
  const fieldList = JSON.stringify(result.value.fields.map((f: any) => ({
    fieldId: f.field, field: f.field, label: f.label, type: f.type, required: f.required, placeholder: f.placeholder, options: f.options || []
  })))
  const formSchema = JSON.stringify({ formName: result.value.formName, formCode: result.value.formCode })
  sessionStorage.setItem('ai-generated-fieldList', fieldList)
  sessionStorage.setItem('ai-generated-formSchema', formSchema)
  router.push('/form-designer?from=ai')
}

function handleRegenerate() { result.value = null; errorMessage.value = '' }
function handleClear() { description.value = ''; result.value = null; errorMessage.value = '' }
</script>

<style scoped>
.ai-form-page { max-width: 900px; margin: 0 auto; }
.page-header { margin-bottom: 28px; text-align: center; }
.page-header h1 { margin: 0 0 10px; font-size: 28px; font-weight: 800; }
.page-header p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.input-card { padding: 28px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.input-actions { display: flex; gap: 12px; margin-top: 20px; justify-content: center; }
.loading-card { margin-top: 22px; padding: 28px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.loading-text { margin: 20px 0 0; text-align: center; color: var(--muted); font-size: 13px; }
.result-section { margin-top: 22px; display: flex; flex-direction: column; gap: 16px; }
.result-card { padding: 24px; border: 1px solid var(--line); border-radius: 22px; background: var(--panel); box-shadow: var(--shadow); }
.card-title { font-size: 15px; font-weight: 700; margin-bottom: 14px; }
.summary-row { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.summary-row .label { color: var(--muted); font-size: 13px; min-width: 60px; }
.summary-row code { background: var(--el-fill-color); padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.summary-text { margin: 12px 0 0; color: var(--muted); line-height: 1.7; }
.xml-box { max-height: 360px; overflow: auto; border: 1px solid var(--line); border-radius: 12px; background: #101a16; }
.xml-box pre { margin: 0; padding: 16px; color: #c6f6e3; font-size: 12px; line-height: 1.55; white-space: pre-wrap; word-break: break-all; }
.result-actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; padding-top: 8px; }
.error-alert { margin-top: 22px; }
</style>