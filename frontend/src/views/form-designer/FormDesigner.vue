<template>
  <div class="form-designer-page">
    <aside class="designer-panel field-palette">
      <div class="designer-panel-head">
        <span>字段组件</span>
        <small>点击添加</small>
      </div>
      <button
        v-for="field in fieldTypes"
        :key="field.type"
        class="field-type-card"
        type="button"
        @click="addField(field.type)"
      >
        <el-icon><component :is="field.icon" /></el-icon>
        <span>{{ field.label }}</span>
      </button>
    </aside>

    <main class="designer-canvas">
      <div class="canvas-toolbar">
        <div>
          <el-tag type="success" effect="plain">Form JSON</el-tag>
          <h1>表单设计器</h1>
          <p>点击左侧字段添加到画布，选中字段后在右侧调整属性。</p>
        </div>
        <div class="canvas-actions">
          <el-button round :icon="View" @click="previewVisible = true">预览表单</el-button>
          <el-button round type="success" :icon="DocumentCopy" @click="emitFormJson">回传 JSON</el-button>
        </div>
      </div>

      <section class="form-canvas-card">
        <el-empty v-if="formJson.fields.length === 0" description="从左侧添加一个字段开始搭建表单" />
        <div
          v-for="field in formJson.fields"
          v-else
          :key="field.fieldId"
          class="canvas-field-card"
          :class="{ active: selectedField?.fieldId === field.fieldId }"
          @click="selectField(field.fieldId)"
        >
          <div class="field-card-main">
            <div class="field-card-icon">
              <el-icon><component :is="getFieldMeta(field.type).icon" /></el-icon>
            </div>
            <div>
              <strong>
                {{ field.label }}
                <span v-if="field.required">*</span>
              </strong>
              <p>{{ field.fieldId }} · {{ getFieldMeta(field.type).label }}</p>
            </div>
          </div>
          <div class="field-card-preview">
            <component :is="previewComponent(field.type)" :field="field" />
          </div>
        </div>
      </section>

      <section class="json-card">
        <div class="json-title">
          <span>当前 formJson</span>
          <el-button text type="success" @click="copyJson">复制</el-button>
        </div>
        <pre>{{ formattedJson }}</pre>
      </section>
    </main>

    <aside class="designer-panel property-panel">
      <div class="designer-panel-head">
        <span>字段属性</span>
        <small>{{ selectedField ? '已选中' : '未选择' }}</small>
      </div>

      <el-empty v-if="!selectedField" description="请选择一个字段进行配置" />
      <el-form v-else label-position="top" class="property-form">
        <el-form-item label="字段ID">
          <el-input v-model="selectedField.fieldId" @input="syncJson" />
        </el-form-item>
        <el-form-item label="字段名称">
          <el-input v-model="selectedField.label" @input="syncJson" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="selectedField.type" @change="handleTypeChange">
            <el-option v-for="item in fieldTypes" :key="item.type" :label="item.label" :value="item.type" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="selectedField.required" active-text="必填" inactive-text="选填" @change="syncJson" />
        </el-form-item>
        <el-form-item label="占位提示">
          <el-input v-model="selectedField.placeholder" @input="syncJson" />
        </el-form-item>
        <el-form-item v-if="needsOptions(selectedField.type)" label="选项内容">
          <div class="option-editor">
            <div v-for="(option, index) in selectedField.options" :key="index" class="option-row">
              <el-input v-model="option.label" placeholder="选项名称" @input="option.value = option.label; syncJson()" />
              <el-button circle :icon="Delete" @click="removeOption(index)" />
            </div>
            <el-button class="add-option-button" plain :icon="Plus" @click="addOption">添加选项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="操作">
          <div class="property-actions">
            <el-button round @click="duplicateField">复制</el-button>
            <el-button round type="danger" plain @click="deleteSelectedField">删除</el-button>
          </div>
        </el-form-item>
      </el-form>
    </aside>

    <el-dialog v-model="previewVisible" title="表单预览" width="620px">
      <el-form label-position="top">
        <el-form-item
          v-for="field in formJson.fields"
          :key="field.fieldId"
          :label="field.required ? `${field.label} *` : field.label"
        >
          <component :is="previewComponent(field.type)" :field="field" interactive />
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, reactive, ref, resolveComponent, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  Check,
  Delete,
  Document,
  DocumentCopy,
  EditPen,
  Files,
  Grid,
  Picture,
  Plus,
  Tickets,
  View
} from '@element-plus/icons-vue'

type FieldType = 'text' | 'number' | 'date' | 'radio' | 'checkbox' | 'file' | 'image' | 'textarea'

interface FieldOption {
  label: string
  value: string
}

interface FormField {
  fieldId: string
  label: string
  type: FieldType
  required: boolean
  placeholder: string
  options: FieldOption[]
}

interface FormJson {
  fields: FormField[]
}

const props = withDefaults(defineProps<{ modelValue?: FormJson | string }>(), {
  modelValue: undefined
})

const emit = defineEmits<{
  'update:modelValue': [value: FormJson]
  change: [value: FormJson]
}>()

const fieldTypes = [
  { type: 'text', label: '文本', icon: EditPen },
  { type: 'number', label: '数字', icon: Tickets },
  { type: 'date', label: '日期', icon: Calendar },
  { type: 'radio', label: '单选', icon: Check },
  { type: 'checkbox', label: '多选', icon: Grid },
  { type: 'file', label: '附件', icon: Files },
  { type: 'image', label: '图片', icon: Picture },
  { type: 'textarea', label: '备注', icon: Document }
] as const

const defaultJson: FormJson = {
  fields: [
    {
      fieldId: 'applicant',
      label: '申请人',
      type: 'text',
      required: true,
      placeholder: '请输入申请人',
      options: []
    }
  ]
}

const formJson = reactive<FormJson>(normalizeModelValue(props.modelValue) || structuredClone(defaultJson))
const selectedFieldId = ref(formJson.fields[0]?.fieldId || '')
const previewVisible = ref(false)

const selectedField = computed(() => formJson.fields.find((field) => field.fieldId === selectedFieldId.value) || null)
const formattedJson = computed(() => JSON.stringify(formJson, null, 2))

watch(
  () => props.modelValue,
  (value) => {
    const next = normalizeModelValue(value)
    if (!next) return
    formJson.fields.splice(0, formJson.fields.length, ...next.fields)
    selectedFieldId.value = formJson.fields[0]?.fieldId || ''
  }
)

function normalizeModelValue(value?: FormJson | string) {
  if (!value) return null
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as FormJson
    } catch {
      return null
    }
  }
  return value
}

function addField(type: FieldType) {
  const index = formJson.fields.length + 1
  const meta = getFieldMeta(type)
  const field: FormField = {
    fieldId: `${type}_${Date.now()}`,
    label: `${meta.label}${index}`,
    type,
    required: false,
    placeholder: getPlaceholder(type),
    options: needsOptions(type) ? defaultOptions() : []
  }
  formJson.fields.push(field)
  selectedFieldId.value = field.fieldId
  syncJson()
}

function selectField(fieldId: string) {
  selectedFieldId.value = fieldId
}

function getFieldMeta(type: FieldType) {
  return fieldTypes.find((field) => field.type === type) || fieldTypes[0]
}

function needsOptions(type: FieldType) {
  return type === 'radio' || type === 'checkbox'
}

function defaultOptions() {
  return [
    { label: '选项一', value: '选项一' },
    { label: '选项二', value: '选项二' }
  ]
}

function getPlaceholder(type: FieldType) {
  if (type === 'date') return '请选择日期'
  if (type === 'file') return '请上传附件'
  if (type === 'image') return '请上传图片'
  if (type === 'textarea') return '请输入备注'
  return '请输入内容'
}

function handleTypeChange(type: FieldType) {
  if (!selectedField.value) return
  selectedField.value.options = needsOptions(type) ? selectedField.value.options.length ? selectedField.value.options : defaultOptions() : []
  selectedField.value.placeholder = getPlaceholder(type)
  syncJson()
}

function addOption() {
  if (!selectedField.value) return
  const label = `选项${selectedField.value.options.length + 1}`
  selectedField.value.options.push({ label, value: label })
  syncJson()
}

function removeOption(index: number) {
  selectedField.value?.options.splice(index, 1)
  syncJson()
}

function duplicateField() {
  if (!selectedField.value) return
  const cloned = structuredClone(selectedField.value)
  cloned.fieldId = `${cloned.type}_${Date.now()}`
  cloned.label = `${cloned.label} 副本`
  formJson.fields.push(cloned)
  selectedFieldId.value = cloned.fieldId
  syncJson()
}

function deleteSelectedField() {
  if (!selectedField.value) return
  const index = formJson.fields.findIndex((field) => field.fieldId === selectedField.value?.fieldId)
  formJson.fields.splice(index, 1)
  selectedFieldId.value = formJson.fields[Math.max(0, index - 1)]?.fieldId || ''
  syncJson()
}

function emitFormJson() {
  syncJson()
  ElMessage.success('formJson 已回传给父组件')
}

async function copyJson() {
  await navigator.clipboard.writeText(formattedJson.value)
  ElMessage.success('formJson 已复制')
}

function syncJson() {
  const value = structuredClone(formJson)
  emit('update:modelValue', value)
  emit('change', value)
}

const PreviewInput = defineComponent({
  props: {
    field: { type: Object, required: true },
    interactive: { type: Boolean, default: false }
  },
  setup(props) {
    return () => {
      const field = props.field as FormField
      const ElInput = resolveComponent('ElInput')
      const ElInputNumber = resolveComponent('ElInputNumber')
      const ElDatePicker = resolveComponent('ElDatePicker')
      const ElRadioGroup = resolveComponent('ElRadioGroup')
      const ElRadio = resolveComponent('ElRadio')
      const ElCheckboxGroup = resolveComponent('ElCheckboxGroup')
      const ElCheckbox = resolveComponent('ElCheckbox')
      const ElButton = resolveComponent('ElButton')
      if (field.type === 'number') {
        return h(ElInputNumber, { placeholder: field.placeholder, controlsPosition: 'right', style: 'width: 100%' })
      }
      if (field.type === 'date') {
        return h(ElDatePicker, { placeholder: field.placeholder, type: 'date', style: 'width: 100%' })
      }
      if (field.type === 'radio') {
        return h(ElRadioGroup, {}, () => field.options.map((option) => h(ElRadio, { value: option.value }, () => option.label)))
      }
      if (field.type === 'checkbox') {
        return h(ElCheckboxGroup, {}, () => field.options.map((option) => h(ElCheckbox, { value: option.value }, () => option.label)))
      }
      if (field.type === 'file' || field.type === 'image') {
        return h(ElButton, { plain: true }, () => field.placeholder)
      }
      if (field.type === 'textarea') {
        return h(ElInput, { type: 'textarea', rows: 3, placeholder: field.placeholder })
      }
      return h(ElInput, { placeholder: field.placeholder })
    }
  }
})

function previewComponent(_type?: FieldType) {
  return PreviewInput
}
</script>
