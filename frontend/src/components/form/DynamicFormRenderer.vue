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
        <div v-else-if="field.type === 'upload'" class="upload-placeholder">
          <el-button plain disabled>附件字段占位</el-button>
          <span>{{ field.placeholder || '当前阶段暂不上传附件' }}</span>
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
import { computed, reactive, watch } from 'vue'

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
    options: normalizeOptions(value.options)
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
  })
  return Object.keys(errors).length === 0
}

function isEmpty(value: unknown) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

function displayValue(field: DynamicField) {
  const value = innerData[field.field]
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

.readonly-value,
.upload-placeholder {
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.readonly-value {
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.upload-placeholder span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 760px) {
  .dynamic-form {
    grid-template-columns: 1fr;
  }
}
</style>
