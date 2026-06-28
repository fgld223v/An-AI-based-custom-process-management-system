<template>
  <div class="dynamic-form-renderer">
    <!-- 表单配置解析失败时的错误提示 -->
    <el-alert
      v-if="parseError"
      type="error"
      :closable="false"
      show-icon
      title="表单配置解析失败，请检查表单设计器保存内容。"
    />
    <!-- 无字段时的空状态提示 -->
    <el-empty
      v-else-if="fields.length === 0"
      description="暂无表单字段，请先在表单设计器中配置表单。"
    />
    <!-- 动态表单主体：根据 fields 配置循环渲染不同类型控件 -->
    <el-form v-else label-position="top" class="dynamic-form">
      <el-form-item
        v-for="field in fields"
        :key="field.field"
        :class="{ 'is-error': Boolean(errors[field.field]) }"
        :label="field.required ? `${field.label} *` : field.label"
      >
        <!-- 只读模式：仅展示字段值，不可编辑 -->
        <template v-if="readonly">
          <div class="readonly-value">{{ displayValue(field) }}</div>
        </template>

        <!-- 单行文本输入框 -->
        <el-input
          v-else-if="field.type === 'input'"
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />
        <!-- 多行文本输入框 -->
        <el-input
          v-else-if="field.type === 'textarea'"
          type="textarea"
          :rows="3"
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />
        <!-- 数字输入框 -->
        <el-input-number
          v-else-if="field.type === 'number'"
          :model-value="innerData[field.field]"
          controls-position="right"
          :placeholder="field.placeholder"
          :disabled="disabled"
          style="width: 100%"
          @update:model-value="updateValue(field.field, $event)"
        />
        <!-- 下拉选择框 -->
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
        <!-- 单选框组 -->
        <el-radio-group
          v-else-if="field.type === 'radio'"
          :model-value="innerData[field.field]"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        >
          <el-radio v-for="option in field.options" :key="String(option.value)" :value="option.value">{{ option.label }}</el-radio>
        </el-radio-group>
        <!-- 多选框组 -->
        <el-checkbox-group
          v-else-if="field.type === 'checkbox'"
          :model-value="Array.isArray(innerData[field.field]) ? innerData[field.field] : []"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        >
          <el-checkbox v-for="option in field.options" :key="String(option.value)" :value="option.value">{{ option.label }}</el-checkbox>
        </el-checkbox-group>
        <!-- 日期/日期时间选择器 -->
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
        <!-- 文件上传控件（拖拽上传） -->
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
        <!-- 未知类型兜底：使用默认的文本输入框 -->
        <el-input
          v-else
          :model-value="innerData[field.field]"
          :placeholder="field.placeholder"
          :disabled="disabled"
          @update:model-value="updateValue(field.field, $event)"
        />

        <!-- 字段级别的校验错误信息 -->
        <div v-if="errors[field.field]" class="field-error">{{ errors[field.field] }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
/**
 * DynamicFormRenderer - 动态表单渲染器
 *
 * 根据后台配置的 fieldList/formSchema JSON 数据，动态渲染表单控件。
 * 支持 input / textarea / number / select / radio / checkbox / date / datetime / upload 等字段类型。
 * 支持只读模式（readonly）、禁用模式（disabled）、跨字段校验规则（rules）。
 * 通过 defineExpose 暴露 validate() 方法供父组件调用来校验表单。
 */
import { computed, reactive, watch, resolveComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { FileUploadResult } from '@/api/file'

/** 支持的字段类型枚举 */
type FieldType = 'input' | 'textarea' | 'number' | 'select' | 'radio' | 'checkbox' | 'date' | 'datetime' | 'upload'

/** 下拉/单选/多选框选项 */
interface FieldOption {
  label: string
  value: string | number | boolean
}

/** 动态字段配置 */
interface DynamicField {
  field: string          // 字段 key，对应表单数据的属性名
  label: string          // 字段显示名称
  type: FieldType        // 控件类型
  required: boolean      // 是否必填
  placeholder: string    // 占位提示文本
  options: FieldOption[] // 选项列表（select/radio/checkbox）
  // ---- 文件上传专属配置 ----
  multiple?: boolean     // 是否允许多文件上传
  accept?: string        // 允许的文件类型
  maxCount?: number      // 最大文件数量
  // ---- 跨字段校验规则 ----
  rules?: FieldRule[]
}

/** 跨字段校验规则 */
interface FieldRule {
  /** 比较运算符：gte: >=, lte: <=, gt: >, lt: <, eq: == */
  op: 'gte' | 'lte' | 'gt' | 'lt' | 'eq'
  /** 目标字段名 */
  targetField: string
  /** 目标字段的显示名称 */
  targetLabel?: string
  /** 自定义错误信息 */
  message?: string
}

// ---- 组件 Props：支持多种输入数据格式兼容 ----
const props = withDefaults(defineProps<{
  formSchema?: unknown    // JSON schema 格式的表单配置
  fieldList?: unknown     // 字段数组格式的表单配置
  modelValue?: Record<string, unknown>  // v-model 双向绑定的表单数据
  formData?: Record<string, unknown>    // 备选数据源（向后兼容）
  readonly?: boolean      // 只读模式（用于详情展示）
  disabled?: boolean      // 禁用交互模式
}>(), {
  formSchema: undefined,
  fieldList: undefined,
  modelValue: undefined,
  formData: undefined,
  readonly: false,
  disabled: false
})

/** 向父组件发射事件 */
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]  // v-model 更新
  change: [value: Record<string, unknown>]                // 数据变更通知
}>()

// ---- 内部响应式状态 ----
const innerData = reactive<Record<string, unknown>>({})  // 表单数据
const errors = reactive<Record<string, string>>({})       // 字段校验错误
const parseError = computed(() => parsedConfig.value.error)
const fields = computed(() => parsedConfig.value.fields)

// ---- 文件上传相关 ----
const uploadFileLists = reactive<Record<string, UploadFile[]>>({})  // 各字段的上传文件列表
const uploadAction = '/api/files/upload'                              // 上传接口地址
const uploadHeaders = computed(() => {
  const authStore = useAuthStore()
  const headers: Record<string, string> = {}
  if (authStore.token) {
    headers.Authorization = `Bearer ${authStore.token}`  // 携带鉴权 token
  }
  return headers
})

/** 解析上传组件图标（运行时获取以避免 SSR 问题） */
function resolveUploadIcon() {
  return UploadFilled
}

/** 文件上传成功回调：将服务器返回的文件信息存入表单数据 */
function onUploadSuccess(field: string, response: any) {
  // 后端返回 { code: 200, data: { fileName, originalName, url, size } }
  const result = response?.data || response
  if (!result) return

  // 将已上传文件信息追加到 innerData 中对应字段的数组
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

/** 文件上传失败回调 */
function onUploadError(error: Error) {
  ElMessage.error('文件上传失败：' + (error.message || '未知错误'))
}

/** 文件数量超出限制回调 */
function onUploadExceed() {
  ElMessage.warning('已达到最大上传数量限制')
}

/** 文件上传前校验：限制单文件不超过 20MB */
function beforeUpload(file: File) {
  const maxSize = 20 * 1024 * 1024 // 20MB
  if (file.size > maxSize) {
    ElMessage.error(`文件"${file.name}"超过 20MB 限制`)
    return false
  }
  return true
}

/** 移除已上传的文件（按 fileName 精确匹配） */
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

/** 解析表单配置：优先使用 formSchema，若无则回退到 fieldList */
const parsedConfig = computed(() => {
  const schemaFields = readFields(props.formSchema)
  if (schemaFields.error) return schemaFields
  if (schemaFields.fields.length > 0) return schemaFields

  const listFields = readFields(props.fieldList)
  if (listFields.error) return listFields
  return listFields
})

/** 监听外部数据源变化，同步到内部 innerData */
watch(
  () => props.modelValue || props.formData || {},
  (value) => {
    Object.keys(innerData).forEach((key) => delete innerData[key])
    Object.assign(innerData, value)
  },
  { immediate: true, deep: true }
)

/** 监听字段配置变化：为新增字段初始化默认值 */
watch(fields, (value) => {
  value.forEach((field) => {
    if (!(field.field in innerData)) {
      // checkbox 默认空数组，其他类型默认空字符串
      innerData[field.field] = field.type === 'checkbox' ? [] : ''
    }
    if (field.type === 'upload' && !(field.field in uploadFileLists)) {
      uploadFileLists[field.field] = []
    }
  })
}, { immediate: true })

/**
 * 从多种可能的格式中读取字段列表
 * 支持：JSON 字符串、{ fields: [...] }、{ fieldList: [...] }、纯数组
 */
function readFields(source: unknown): { fields: DynamicField[], error: boolean } {
  if (source === undefined || source === null || source === '') {
    return { fields: [], error: false }
  }
  let value = source
  if (typeof source === 'string') {
    try {
      value = JSON.parse(source)  // 尝试 JSON 解析
    } catch {
      return { fields: [], error: true }  // 解析失败则标记 error
    }
  }
  // 兼容多种包装格式
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

/**
 * 将原始字段配置规范化为 DynamicField 标准格式
 * 支持多种命名变体：fieldId / name / prop 等
 */
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

/** 将字符串类型名映射为标准 FieldType，支持别名兼容 */
function normalizeType(type: string): FieldType {
  const normalized = type.toLowerCase()
  if (normalized === 'text') return 'input'       // "text" 别名为 input
  if (normalized === 'file' || normalized === 'image') return 'upload'  // "file"/"image" 映射为 upload
  if (['textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'datetime', 'upload', 'input'].includes(normalized)) {
    return normalized as FieldType
  }
  return 'input'  // 未知类型兜底为 input
}

/** 规范化选项列表：兼容 { label, value } 对象数组和纯值数组 */
function normalizeOptions(options: unknown): FieldOption[] {
  if (!Array.isArray(options)) return []
  return options.map((option) => {
    if (option && typeof option === 'object') {
      const item = option as Record<string, unknown>
      const value = item.value ?? item.label ?? ''
      return { label: String(item.label ?? value), value: value as string | number | boolean }
    }
    return { label: String(option), value: String(option) }  // 纯值则 label 和 value 同值
  })
}

// ================================================================
// Upload helpers — 文件元信息辅助函数（兼容 JSON 字符串存储格式）
// ================================================================

interface FileMeta {
  name: string
  size: number
  type: string
  lastModified: number
}

/** 从表单数据中解析文件列表（适配 JSON 字符串存储格式） */
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

/** 处理上传文件变更：将文件信息序列化后存入表单数据 */
function handleUploadChange(field: string, files: any[]) {
  const fileInfos: FileMeta[] = files.map((f: any) => ({
    name: f.name,
    size: f.size,
    type: f.raw?.type || f.type || '',
    lastModified: f.raw?.lastModified || Date.now()
  }))
  updateValue(field, JSON.stringify(fileInfos))
}

/** 更新单个字段值，清除该字段的校验错误，并通知父组件 */
function updateValue(field: string, value: unknown) {
  innerData[field] = value
  delete errors[field]  // 值变更后清除该字段的校验错误
  const data = { ...innerData }
  emit('update:modelValue', data)
  emit('change', data)
}

/**
 * 表单校验方法（暴露给父组件调用）
 * 支持：必填校验、数字格式校验、跨字段比较规则
 * @returns 校验是否通过（true=全部通过）
 */
function validate() {
  // 清除上一轮校验错误信息
  Object.keys(errors).forEach((key) => delete errors[key])
  fields.value.forEach((field) => {
    const value = innerData[field.field]
    // 必填校验
    if (field.required && isEmpty(value)) {
      errors[field.field] = `${field.label}不能为空`
      return
    }
    // 数字格式校验
    if (field.type === 'number' && !isEmpty(value) && Number.isNaN(Number(value))) {
      errors[field.field] = `${field.label}必须为数字`
    }
    // 跨字段校验规则（如：结束日期 >= 开始日期）
    if (field.rules && !isEmpty(value)) {
      for (const rule of field.rules) {
        const targetValue = innerData[rule.targetField]
        if (isEmpty(targetValue)) continue // 目标字段为空时跳过此规则
        // 将值转为可比较的数值（日期取时间戳，其他取数字值）
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
          case 'gte': failed = myVal < tgtVal; break   // 当前值 >= 目标值
          case 'lte': failed = myVal > tgtVal; break   // 当前值 <= 目标值
          case 'gt':  failed = myVal <= tgtVal; break  // 当前值 > 目标值
          case 'lt':  failed = myVal >= tgtVal; break  // 当前值 < 目标值
          case 'eq':  failed = myVal !== tgtVal; break // 当前值 == 目标值
        }
        if (failed) {
          // 构建友好的错误消息
          errors[field.field] = rule.message || `${field.label}必须${rule.op === 'gte' ? '≥' : rule.op === 'lte' ? '≤' : rule.op === 'gt' ? '>' : rule.op === 'lt' ? '<' : '='}${targetLabel}`
        }
      }
    }
  })
  return Object.keys(errors).length === 0
}

/** 判断值是否为空（兼容 null / undefined / 空字符串 / 空数组） */
function isEmpty(value: unknown) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

/** 只读模式下的字段展示值格式化：数组用顿号连接，上传字段显示文件名列表 */
function displayValue(field: DynamicField) {
  const value = innerData[field.field]
  if (field.type === 'upload' && Array.isArray(value)) {
    const fileNames = value.map((f: any) => f?.originalName || f?.fileName || '').filter(Boolean)
    return fileNames.length > 0 ? fileNames.join('、') : '暂无附件'
  }
  if (Array.isArray(value)) return value.join('、') || '-'
  return isEmpty(value) ? '-' : String(value)
}

/** 暴露 validate 方法，供父组件通过 ref 调用 */
defineExpose({ validate })
</script>

<style scoped>
/* ---- 根容器 ---- */
.dynamic-form-renderer {
  width: 100%;
}

/* ---- 表单网格布局：默认双列，小屏幕单列 ---- */
.dynamic-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.dynamic-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

/* ---- 校验失败时的红色边框提示 ---- */
.dynamic-form :deep(.el-form-item.is-error .el-input__wrapper),
.dynamic-form :deep(.el-form-item.is-error .el-textarea__inner),
.dynamic-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* ---- 字段错误文本 ---- */
.field-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
}

/* ---- 只读模式下的展示值样式 ---- */
.readonly-value {
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

/* ---- 文件上传控件样式 ---- */
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

/* ---- 响应式：窄屏时切换为单列布局 ---- */
@media (max-width: 760px) {
  .dynamic-form {
    grid-template-columns: 1fr;
  }
}
</style>