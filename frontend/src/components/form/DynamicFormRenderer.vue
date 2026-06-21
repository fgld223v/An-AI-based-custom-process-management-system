<template>
  <div class="dynamic-form-renderer">
    <el-alert
      v-if="parseError"
      type="error"
      :closable="false"
      show-icon
      title="表单配置解析失败，请检查表单设计器保存内容。"
    />
    <el-empty
      v-else-if="fields.length === 0"
      description="暂无表单字段，请先在表单设计器中配置表单。"
    />
    <el-form v-else label-position="top" class="dynamic-form">
      <el-form-item
        v-for="field in fields"
        :key="field.field"
        :class="{ 'is-error': Boolean(errors[field.field]) }"
        :label="field.required ? `${field.label} *` : field.label"
      >
        <template v-if="readonly">
          <div class="readonly-value">{{ displayValue(field) }}</div>
        </template>

        <el-input
          v-else-if="field.type === 'input'"
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />
        <el-input
          v-else-if="field.type === 'textarea'"
          type="textarea"
          :rows="3"
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />
        <el-input-number
          v-else-if="field.type === 'number'"
          :model-value="innerData[field.field]"
          controls-position="right"
          :placeholder="field.placeholder"
          :disabled="disabled"
          style="width: 100%"
          @update:model-value="updateValue(field.field, $event)"
        />
        <el-select
          v-else-if="field.type === 'select'"
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          clearable
          style="width: 100%"
          @update:model-value="updateValue(field.field, $event)"
        >
          <el-option v-for="option in field.options" :key="String(option.value)" :label="option.label" :value="option.value" />
        </el-select>
        <el-radio-group
          v-else-if="field.type === 'radio'"
          :model-value="innerData[field.field]"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        >
          <el-radio v-for="option in field.options" :key="String(option.value)" :value="option.value">{{ option.label }}</el-radio>
        </el-radio-group>
        <el-checkbox-group
          v-else-if="field.type === 'checkbox'"
          :model-value="Array.isArray(innerData[field.field]) ? innerData[field.field] : []"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        >
          <el-checkbox v-for="option in field.options" :key="String(option.value)" :value="option.value">{{ option.label }}</el-checkbox>
        </el-checkbox-group>
        <el-date-picker
          v-else-if="field.type === 'date' || field.type === 'datetime'"
          :model-value="innerData[field.field]"
          :type="field.type === 'datetime' ? 'datetime' : 'date'"
          :placeholder="field.placeholder"
          :disabled="disabled"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
          @update:model-value="updateValue(field.field, $event)"
        />
        <div v-else-if="field.type === 'upload'" class="upload-field">
          <el-upload
            v-model:file-list="uploadFileLists[field.field]"
            :action="uploadAction"
            :headers="uploadHeaders"
            :multiple="field.multiple ?? true"
            :limit="field.maxCount ?? 5"
            :accept="field.accept ?? undefined"
            :disabled="disabled || readonly"
            :auto-upload="true"
            :on-success="(res: any) => onUploadSuccess(field.field, res)"
            :on-error="onUploadError"
            :on-exceed="onUploadExceed"
            :before-upload="beforeUpload"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                {{ field.placeholder || '支持 jpg/png/pdf/doc 等格式，单文件不超过 20MB' }}
              </div>
            </template>
          </el-upload>
        </div>
        <el-input
          v-else
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />

        <div v-if="errors[field.field]" class="field-error">{{ errors[field.field] }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch, resolveComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { FileUploadResult } from '@/api/file'

type FieldType = 'input' | 'textarea' | 'number' | 'select' | 'radio' | 'checkbox' | 'date' | 'datetime' | 'upload'

interface FieldOption {
  label: string
  value: string | number | boolean
}

interface DynamicField {
  field: string
  label: string
  type: FieldType
  required: boolean
  placeholder: string
  options: FieldOption[]
  // upload-specific
  multiple?: boolean
  accept?: string
  maxCount?: number
  // cross-field validation
  rules?: FieldRule[]
}

interface FieldRule {
  /** gte: >= targetField, lte: <= targetField, gt: >, lt: <, eq: == */
  op: 'gte' | 'lte' | 'gt' | 'lt' | 'eq'
  /** 目标字段名 */
  targetField: string
  /** 目标字段的显示名称 */
  targetLabel?: string
  /** 自定义错误信息 */
  message?: string
}

const props = withDefaults(defineProps<{
  formSchema?: unknown
  fieldList?: unknown
  modelValue?: Record<string, unknown>
  formData?: Record<string, unknown>
  readonly?: boolean
  disabled?: boolean
}>(), {
  formSchema: undefined,
  fieldList: undefined,
  modelValue: undefined,
  formData: undefined,
  readonly: false,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  change: [value: Record<string, unknown>]
}>()

const innerData = reactive<Record<string, unknown>>({})
const errors = reactive<Record<string, string>>({})
const parseError = computed(() => parsedConfig.value.error)
const fields = computed(() => parsedConfig.value.fields)

// ---- 文件上传相关 ----
const uploadFileLists = reactive<Record<string, UploadFile[]>>({})
const uploadAction = '/api/files/upload'
const uploadHeaders = computed(() => {
  const authStore = useAuthStore()
  const headers: Record<string, string> = {}
  if (authStore.token) {
    headers.Authorization = `Bearer ${authStore.token}`
  }
  return headers
})

function resolveUploadIcon() {
  return UploadFilled
}

function onUploadSuccess(field: string, response: any) {
  // 后端返回 { code: 200, data: { fileName, originalName, url, size } }
  const result = response?.data || response
  if (!result) return

  // 将已上传文件信息存入 innerData
  const existing = innerData[field]
  const files: FileUploadResult[] = Array.isArray(existing) ? [...existing] : []
  files.push({
    fileName: result.fileName,
    originalName: result.originalName,
    url: result.url,
    size: result.size
  })
  innerData[field] = files
  const data = { ...innerData }
  emit('update:modelValue', data)
  emit('change', data)
}

function onUploadError(error: Error) {
  ElMessage.error('文件上传失败：' + (error.message || '未知错误'))
}

function onUploadExceed() {
  ElMessage.warning('已达到最大上传数量限制')
}

function beforeUpload(file: File) {
  const maxSize = 20 * 1024 * 1024 // 20MB
  if (file.size > maxSize) {
    ElMessage.error(`文件"${file.name}"超过 20MB 限制`)
    return false
  }
  return true
}

// 移除已上传文件
function removeUploadedFile(field: string, fileName: string) {
  const existing = innerData[field]
  if (Array.isArray(existing)) {
    const files = existing.filter((f: FileUploadResult) => f.fileName !== fileName)
    innerData[field] = files
    const data = { ...innerData }
    emit('update:modelValue', data)
    emit('change', data)
  }
}

const parsedConfig = computed(() => {
  const schemaFields = readFields(props.formSchema)
  if (schemaFields.error) return schemaFields
  if (schemaFields.fields.length > 0) return schemaFields

  const listFields = readFields(props.fieldList)
  if (listFields.error) return listFields
  return listFields
})

watch(
  () => props.modelValue || props.formData || {},
  (value) => {
    Object.keys(innerData).forEach((key) => delete innerData[key])
    Object.assign(innerData, value)
  },
  { immediate: true, deep: true }
)

watch(fields, (value) => {
  value.forEach((field) => {
    if (!(field.field in innerData)) {
      innerData[field.field] = field.type === 'checkbox' ? [] : ''
    }
    if (field.type === 'upload' && !(field.field in uploadFileLists)) {
      uploadFileLists[field.field] = []
    }
  })
}, { immediate: true })

function readFields(source: unknown): { fields: DynamicField[], error: boolean } {
  if (source === undefined || source === null || source === '') {
    return { fields: [], error: false }
  }
  let value = source
  if (typeof source === 'string') {
    try {
      value = JSON.parse(source)
    } catch {
      return { fields: [], error: true }
    }
  }
  const list: unknown[] = Array.isArray(value)
    ? value
    : Array.isArray((value as Record<string, unknown>)?.fields)
      ? (value as Record<string, unknown>).fields as unknown[]
      : Array.isArray((value as Record<string, unknown>)?.fieldList)
        ? (value as Record<string, unknown>).fieldList as unknown[]
        : []

  return {
    fields: list.map(normalizeField).filter((field): field is DynamicField => Boolean(field)),
    error: false
  }
}

function normalizeField(raw: unknown): DynamicField | null {
  if (!raw || typeof raw !== 'object') return null
  const value = raw as Record<string, unknown>
  const field = String(value.field || value.fieldId || value.name || value.prop || '').trim()
  if (!field) return null

  return {
    field,
    label: String(value.label || value.title || field),
    type: normalizeType(String(value.type || 'input')),
    required: Boolean(value.required),
    placeholder: String(value.placeholder || ''),
    options: normalizeOptions(value.options),
    multiple: Boolean(value.multiple ?? false),
    accept: value.accept ? String(value.accept) : undefined,
    maxCount: value.maxCount ? Number(value.maxCount) : undefined
  }
}

function normalizeType(type: string): FieldType {
  const normalized = type.toLowerCase()
  if (normalized === 'text') return 'input'
  if (normalized === 'file' || normalized === 'image') return 'upload'
  if (['textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'datetime', 'upload', 'input'].includes(normalized)) {
    return normalized as FieldType
  }
  return 'input'
}

function normalizeOptions(options: unknown): FieldOption[] {
  if (!Array.isArray(options)) return []
  return options.map((option) => {
    if (option && typeof option === 'object') {
      const item = option as Record<string, unknown>
      const value = item.value ?? item.label ?? ''
      return { label: String(item.label ?? value), value: value as string | number | boolean }
    }
    return { label: String(option), value: String(option) }
  })
}

// ================================================================
// Upload helpers — files stored as JSON array in form data
// ================================================================

interface FileMeta {
  name: string
  size: number
  type: string
  lastModified: number
}

function uploadFileList(field: string) {
  const raw = innerData[field]
  if (!raw || typeof raw !== 'string') return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function handleUploadChange(field: string, files: any[]) {
  const fileInfos: FileMeta[] = files.map((f: any) => ({
    name: f.name,
    size: f.size,
    type: f.raw?.type || f.type || '',
    lastModified: f.raw?.lastModified || Date.now()
  }))
  updateValue(field, JSON.stringify(fileInfos))
}

function updateValue(field: string, value: unknown) {
  innerData[field] = value
  delete errors[field]
  const data = { ...innerData }
  emit('update:modelValue', data)
  emit('change', data)
}

function validate() {
  Object.keys(errors).forEach((key) => delete errors[key])
  fields.value.forEach((field) => {
    const value = innerData[field.field]
    if (field.required && isEmpty(value)) {
      errors[field.field] = `${field.label}不能为空`
      return
    }
    if (field.type === 'number' && !isEmpty(value) && Number.isNaN(Number(value))) {
      errors[field.field] = `${field.label}必须为数字`
    }
    // 跨字段校验规则
    if (field.rules && !isEmpty(value)) {
      for (const rule of field.rules) {
        const targetValue = innerData[rule.targetField]
        if (isEmpty(targetValue)) continue // 目标字段为空时跳过
        const myVal = field.type === 'number' || field.type === 'date' || field.type === 'datetime'
          ? new Date(value as string).getTime()
          : Number(value)
        const tgtVal = field.type === 'number' || field.type === 'date' || field.type === 'datetime'
          ? new Date(targetValue as string).getTime()
          : Number(targetValue)
        if (Number.isNaN(myVal) || Number.isNaN(tgtVal)) continue

        const targetLabel = rule.targetLabel || rule.targetField
        let failed = false
        switch (rule.op) {
          case 'gte': failed = myVal < tgtVal; break
          case 'lte': failed = myVal > tgtVal; break
          case 'gt':  failed = myVal <= tgtVal; break
          case 'lt':  failed = myVal >= tgtVal; break
          case 'eq':  failed = myVal !== tgtVal; break
        }
        if (failed) {
          errors[field.field] = rule.message || `${field.label}必须${rule.op === 'gte' ? '≥' : rule.op === 'lte' ? '≤' : rule.op === 'gt' ? '>' : rule.op === 'lt' ? '<' : '='}${targetLabel}`
        }
      }
    }
  })
  return Object.keys(errors).length === 0
}

function isEmpty(value: unknown) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

function displayValue(field: DynamicField) {
  const value = innerData[field.field]
  if (field.type === 'upload' && Array.isArray(value)) {
    const fileNames = value.map((f: any) => f?.originalName || f?.fileName || '').filter(Boolean)
    return fileNames.length > 0 ? fileNames.join('、') : '暂无附件'
  }
  if (Array.isArray(value)) return value.join('、') || '-'
  return isEmpty(value) ? '-' : String(value)
}

defineExpose({ validate })
</script>

<style scoped>
.dynamic-form-renderer {
  width: 100%;
}

.dynamic-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.dynamic-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.dynamic-form :deep(.el-form-item.is-error .el-input__wrapper),
.dynamic-form :deep(.el-form-item.is-error .el-textarea__inner),
.dynamic-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.field-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
}

.readonly-value {
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.upload-field {
  width: 100%;
}

.upload-field :deep(.el-upload) {
  width: 100%;
}

.upload-field :deep(.el-upload-dragger) {
  width: 100%;
  padding: 20px;
}

@media (max-width: 760px) {
  .dynamic-form {
    grid-template-columns: 1fr;
  }
}
</style>