<template>
  <div class="start-preview-page">
    <section class="preview-head">
      <div>
        <el-tag type="success" effect="plain">流程发起预览</el-tag>
        <h1>动态表单绑定验证</h1>
        <p>当前阶段仅验证动态表单绑定与数据采集，流程实例将在下一阶段生成。</p>
      </div>
      <el-button round :icon="Refresh" @click="loadTemplates">刷新模板</el-button>
    </section>

    <section class="preview-panel">
      <el-form label-position="top">
        <el-form-item label="选择流程模板">
          <el-select
            v-model="selectedTemplateId"
            filterable
            clearable
            placeholder="请选择流程模板"
            :loading="loading"
            style="width: 100%"
            @change="loadBoundForm"
          >
            <el-option
              v-for="item in templates"
              :key="item.id"
              :label="item.templateName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert v-if="message" type="warning" :closable="false" show-icon :title="message" />

      <div v-if="binding" class="binding-summary">
        <div>
          <span>模板名称</span>
          <strong>{{ binding.template.templateName }}</strong>
        </div>
        <div>
          <span>模板编码</span>
          <strong>{{ binding.template.templateCode }}</strong>
        </div>
        <div>
          <span>绑定表单</span>
          <strong>{{ binding.form.formName }}</strong>
        </div>
        <div>
          <span>版本</span>
          <strong>{{ binding.form.version || '-' }}</strong>
        </div>
      </div>

      <DynamicFormRenderer
        v-if="binding"
        ref="formRendererRef"
        v-model="formData"
        :form-schema="binding.form.formSchema"
        :field-list="binding.form.fieldList"
      />

      <div v-if="binding" class="preview-actions">
        <el-button round type="success" :icon="View" @click="previewData">预览表单数据</el-button>
        <el-button round :icon="CircleCheck" @click="validateForm">校验表单</el-button>
      </div>
    </section>

    <section v-if="jsonVisible" class="json-panel">
      <div class="json-title">
        <h2>formData JSON</h2>
        <el-button text type="success" @click="jsonVisible = false">收起</el-button>
      </div>
      <pre>{{ formattedData }}</pre>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Refresh, View } from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { getProcessTemplateBoundForm, getProcessTemplates } from '@/api/processTemplate'
import type { ProcessTemplate, TemplateFormBinding } from '@/types/workflow'

const loading = ref(false)
const templates = ref<ProcessTemplate[]>([])
const selectedTemplateId = ref<number | null>(null)
const binding = ref<TemplateFormBinding | null>(null)
const message = ref('')
const formData = ref<Record<string, unknown>>({})
const jsonVisible = ref(false)
const formRendererRef = ref<InstanceType<typeof DynamicFormRenderer> | null>(null)

const formattedData = computed(() => JSON.stringify(formData.value, null, 2))

onMounted(loadTemplates)

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await getProcessTemplates()
  } finally {
    loading.value = false
  }
}

async function loadBoundForm(id?: number) {
  binding.value = null
  message.value = ''
  formData.value = {}
  jsonVisible.value = false
  if (!id) return

  try {
    const result = await getProcessTemplateBoundForm(id)
    if (result) {
      binding.value = result
    }
  } catch (error) {
    message.value = error instanceof Error ? error.message : '当前流程模板未绑定表单。'
  }
}

function previewData() {
  jsonVisible.value = true
}

function validateForm() {
  const passed = formRendererRef.value?.validate() ?? false
  if (passed) {
    ElMessage.success('表单校验通过')
  } else {
    ElMessage.warning('表单校验未通过，请检查必填项或数字字段')
  }
}
</script>

<style scoped>
.start-preview-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.preview-head,
.preview-panel,
.json-panel {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 26px;
  border-radius: 24px;
}

.preview-head h1 {
  margin: 10px 0 6px;
  font-size: 30px;
}

.preview-head p {
  margin: 0;
  color: var(--muted);
}

.preview-panel,
.json-panel {
  padding: 22px;
  border-radius: 22px;
}

.binding-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 22px;
}

.binding-summary div {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.binding-summary span {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: 13px;
}

.binding-summary strong {
  display: block;
  overflow-wrap: anywhere;
}

.preview-actions,
.json-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-actions {
  justify-content: flex-start;
  margin-top: 6px;
}

.json-title h2 {
  margin: 0;
  font-size: 18px;
}

pre {
  margin: 16px 0 0;
  padding: 16px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}

@media (max-width: 960px) {
  .preview-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .binding-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .binding-summary {
    grid-template-columns: 1fr;
  }
}
</style>
