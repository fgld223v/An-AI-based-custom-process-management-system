<template>
  <div class="form-designer-page">
    <aside class="designer-panel form-list-panel">
      <div class="designer-panel-head">
        <div>
          <span>表单列表</span>
          <small>草稿与已发布表单</small>
        </div>
        <el-button circle :icon="Plus" @click="newForm" />
      </div>

      <el-scrollbar class="form-list-scroll">
        <div
          v-for="item in forms"
          :key="item.id"
          class="form-list-item"
          :class="{ active: item.id === currentFormId }"
          @click="loadForm(item)"
        >
          <strong>{{ item.formName }}</strong>
          <span>{{ item.formCode }}</span>
          <el-tag size="small" :type="item.status === 'published' ? 'success' : 'info'" effect="plain">
            {{ statusLabel(item.status) }}
          </el-tag>
          <el-button
            text
            size="small"
            type="danger"
            :icon="Delete"
            @click.stop="handleDeleteForm(item)"
          />
        </div>
        <el-empty v-if="forms.length === 0 && !listLoading" description="暂无表单，点击上方新增" />
      </el-scrollbar>
    </aside>

    <main class="designer-canvas">
      <section class="canvas-toolbar">
        <div>
          <el-tag type="success" effect="plain">FormDefinition</el-tag>
          <h1>表单设计器</h1>
          <p>保存并发布表单后，流程模板即可在绑定表单下拉框中选择它。</p>
        </div>
        <div class="canvas-actions">
          <el-button round :icon="RefreshLeft" :disabled="undoStack.length === 0" @click="undo">撤销</el-button>
          <el-button round :icon="RefreshRight" :disabled="redoStack.length === 0" @click="redo">重做</el-button>
          <el-button round :icon="Refresh" @click="loadForms">刷新</el-button>
          <el-button round :icon="View" @click="previewVisible = true">预览表单</el-button>
          <el-button round type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
          <el-button round type="success" :loading="publishing" :disabled="currentStatus === 'published'" @click="publishCurrentForm">
            {{ currentStatus === 'published' ? '已发布' : '发布表单' }}
          </el-button>
        </div>
      </section>

      <section class="form-meta-panel">
        <el-form label-position="top">
          <el-row :gutter="14">
            <el-col :span="8">
              <el-form-item label="表单编码">
                <el-input v-model="formMeta.formCode" :disabled="Boolean(currentFormId)" placeholder="留空自动生成" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="表单名称">
                <el-input v-model="formMeta.formName" placeholder="例如 请假申请表" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="业务类型">
                <el-select v-model="formMeta.bizTypeId" clearable placeholder="请选择业务类型" style="width: 100%">
                  <el-option v-for="item in bizTypes" :key="item.id" :label="item.typeName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="表单说明">
            <el-input v-model="formMeta.formDesc" type="textarea" :rows="2" placeholder="可选，说明会保存到 formSchema.meta.description" />
          </el-form-item>
        </el-form>
      </section>

      <section class="designer-workspace">
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
                <p>{{ field.fieldId }} / {{ getFieldMeta(field.type).label }}</p>
              </div>
            </div>
            <div class="field-card-preview">
              <component :is="previewComponent(field.type)" :field="field" />
            </div>
          </div>
        </section>

        <aside class="designer-panel property-panel">
          <div class="designer-panel-head">
            <span>字段属性</span>
            <small>{{ selectedField ? '已选中' : '未选择' }}</small>
          </div>

          <el-empty v-if="!selectedField" description="请选择一个字段进行配置" />
          <el-form v-else label-position="top" class="property-form">
            <el-form-item label="字段标识">
              <el-input v-model="selectedField.fieldId" @input="syncJson" />
            </el-form-item>
            <el-form-item label="字段名称">
              <el-input v-model="selectedField.label" @input="syncJson" />
            </el-form-item>
            <el-form-item label="字段类型">
              <el-select v-model="selectedField.type" style="width: 100%" @change="handleTypeChange">
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
      </section>

      <section class="json-card">
        <div class="json-title">
          <span>当前 formSchema</span>
          <el-button text type="success" @click="copyJson">复制</el-button>
        </div>
        <pre>{{ formattedSchema }}</pre>
      </section>
    </main>

    <el-dialog v-model="previewVisible" title="表单预览" width="720px">
      <DynamicFormRenderer v-model="previewData" :form-schema="formSchemaObject" :field-list="fieldListObject" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar,
  Check,
  Delete,
  Document,
  EditPen,
  Files,
  Grid,
  List,
  Plus,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Tickets,
  UploadFilled,
  View
} from '@element-plus/icons-vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { getBizTypes } from '@/api/bizType'
import { createForm, disableForm, getFormDetail, getForms, publishForm, updateForm } from '@/api/formDefinition'
import type { BizType, FormDefinition, FormDefinitionPayload } from '@/types/workflow'

type FieldType = 'text' | 'textarea' | 'number' | 'select' | 'radio' | 'checkbox' | 'date' | 'datetime' | 'upload'

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

interface NormalizedField {
  field: string
  label: string
  type: string
  required: boolean
  placeholder: string
  options: FieldOption[]
}

interface FormJson {
  fields: FormField[]
}

const fieldTypes = [
  { type: 'text', label: '文本', icon: EditPen },
  { type: 'textarea', label: '多行文本', icon: Document },
  { type: 'number', label: '数字', icon: Tickets },
  { type: 'select', label: '下拉选择', icon: List },
  { type: 'radio', label: '单选', icon: Check },
  { type: 'checkbox', label: '多选', icon: Grid },
  { type: 'date', label: '日期', icon: Calendar },
  { type: 'datetime', label: '日期时间', icon: Calendar },
  { type: 'upload', label: '附件/图片', icon: Files }
] as const

const emptyFormJson: FormJson = { fields: [] }

const listLoading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const previewVisible = ref(false)
const forms = ref<FormDefinition[]>([])
const bizTypes = ref<BizType[]>([])
const currentFormId = ref<number | null>(null)
const currentStatus = ref('draft')
const selectedFieldId = ref('')
const previewData = ref<Record<string, unknown>>({})

// 撤销/重做栈
const undoStack = ref<FormField[][]>([])
const redoStack = ref<FormField[][]>([])
let undoRecording = true  // 批量操作时暂停记录

const formMeta = reactive({
  formCode: '',
  formName: '',
  formDesc: '',
  bizTypeId: null as number | null
})

const formJson = reactive<FormJson>(structuredClone(emptyFormJson))
const selectedField = computed(() => formJson.fields.find((field) => field.fieldId === selectedFieldId.value) || null)
const fieldListObject = computed(() => normalizeFields(formJson.fields))
const formSchemaObject = computed(() => ({
  fields: fieldListObject.value,
  layout: 'vertical',
  meta: {
    formCode: formMeta.formCode,
    formName: formMeta.formName,
    description: formMeta.formDesc
  }
}))
const formattedSchema = computed(() => JSON.stringify(formSchemaObject.value, null, 2))
const route = useRoute()

watch(
  () => route.query.id,
  async (formId) => {
    await loadRouteForm(formId)
  }
)

onMounted(async () => {
  await Promise.all([loadForms(), loadBizTypes()])
  // 从 AI 表单生成页跳转过来时，自动加载指定表单
  const formId = route.query.id
  if (formId && typeof formId === 'string') {
    try {
      const detail = await getFormDetail(Number(formId))
      if (detail) loadForm(detail)
    } catch { /* 表单不存在时不报错 */ }
  }
})

async function handleDeleteForm(item: FormDefinition) {
  try {
    await ElMessageBox.confirm('确定删除表单"' + item.formName + '"吗？此操作不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await disableForm(item.id)
    ElMessage.success('已删除')
    if (currentFormId.value === item.id) newForm()
    await loadForms()
  } catch { /* 取消删除 */ }
}

async function loadForms() {
  listLoading.value = true
  try {
    forms.value = await getForms()
  } finally {
    listLoading.value = false
  }
}

async function loadBizTypes() {
  bizTypes.value = await getBizTypes()
}

function newForm() {
  currentFormId.value = null
  currentStatus.value = 'draft'
  selectedFieldId.value = ''
  Object.assign(formMeta, {
    formCode: '',
    formName: '',
    formDesc: '',
    bizTypeId: null
  })
  formJson.fields.splice(0, formJson.fields.length)
  previewData.value = {}
}

function loadForm(item: FormDefinition) {
  currentFormId.value = item.id
  currentStatus.value = item.status || 'draft'
  formMeta.formCode = item.formCode
  formMeta.formName = item.formName
  formMeta.bizTypeId = item.bizTypeId ?? null

  const schema = parseJsonObject(item.formSchema)
  formMeta.formDesc = String(schema?.meta?.description || '')

  const fields = readSavedFields(item.formSchema, item.fieldList)
  formJson.fields.splice(0, formJson.fields.length, ...fields)
  selectedFieldId.value = formJson.fields[0]?.fieldId || ''
  previewData.value = {}
}

async function loadRouteForm(routeFormId: unknown) {
  const formId = normalizeRouteFormId(routeFormId)
  if (!formId || currentFormId.value === formId) return
  try {
    const detail = await getFormDetail(formId)
    if (detail) loadForm(detail)
  } catch {
    // Ignore invalid route ids so the designer stays usable.
  }
}

function normalizeRouteFormId(routeFormId: unknown) {
  if (typeof routeFormId !== 'string') return null
  const formId = Number(routeFormId)
  return Number.isFinite(formId) && formId > 0 ? formId : null
}

async function saveDraft() {
  const saved = await persistForm()
  if (saved) {
    ElMessage.success('保存成功')
  }
}

async function publishCurrentForm() {
  if (formJson.fields.length === 0) {
    ElMessage.warning('请至少配置一个表单字段后再发布。')
    return
  }

  const saved = await persistForm()
  if (!saved?.id) return

  publishing.value = true
  try {
    const published = await publishForm(saved.id)
    currentFormId.value = published.id
    currentStatus.value = published.status || 'published'
    ElMessage.success('发布成功，流程模板现在可以绑定该表单。')
    await loadForms()
  } finally {
    publishing.value = false
  }
}

async function persistForm() {
  if (!formMeta.formName.trim()) {
    ElMessage.warning('请输入表单名称。')
    return null
  }

  saving.value = true
  try {
    const payload = buildPayload()
    const saved = currentFormId.value
      ? await updateForm(currentFormId.value, payload)
      : await createForm(payload)

    currentFormId.value = saved.id
    currentStatus.value = saved.status || currentStatus.value
    formMeta.formCode = saved.formCode
    formMeta.formName = saved.formName
    await loadForms()
    return saved
  } catch (error) {
    if (!(error instanceof Error)) {
      ElMessage.error('表单配置生成失败，请检查字段配置。')
    }
    return null
  } finally {
    saving.value = false
  }
}

function buildPayload(): FormDefinitionPayload {
  const fieldList = fieldListObject.value
  const formSchema = formSchemaObject.value
  return {
    formCode: formMeta.formCode.trim() || `form_${Date.now()}`,
    formName: formMeta.formName.trim(),
    bizTypeId: formMeta.bizTypeId,
    version: 1,
    fieldList: JSON.stringify(fieldList),
    formSchema: JSON.stringify(formSchema)
  }
}

// ====== 撤销/重做 ======
function pushUndo() {
  if (!undoRecording) return
  undoStack.value.push(structuredClone(formJson.fields))
  redoStack.value = []
  if (undoStack.value.length > 30) undoStack.value.shift()
}
function undo() {
  const prev = undoStack.value.pop()
  if (!prev) return
  undoRecording = false
  redoStack.value.push(structuredClone(formJson.fields))
  formJson.fields.splice(0, formJson.fields.length, ...structuredClone(prev))
  selectedFieldId.value = formJson.fields[0]?.fieldId || ''
  undoRecording = true
}
function redo() {
  const next = redoStack.value.pop()
  if (!next) return
  undoRecording = false
  undoStack.value.push(structuredClone(formJson.fields))
  formJson.fields.splice(0, formJson.fields.length, ...structuredClone(next))
  selectedFieldId.value = formJson.fields[0]?.fieldId || ''
  undoRecording = true
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
  pushUndo()
}

function selectField(fieldId: string) {
  selectedFieldId.value = fieldId
}

function getFieldMeta(type: FieldType) {
  return fieldTypes.find((field) => field.type === type) || fieldTypes[0]
}

function needsOptions(type: FieldType) {
  return type === 'select' || type === 'radio' || type === 'checkbox'
}

function defaultOptions() {
  return [
    { label: '选项一', value: '选项一' },
    { label: '选项二', value: '选项二' }
  ]
}

function getPlaceholder(type: FieldType) {
  if (type === 'date' || type === 'datetime') return '请选择时间'
  if (type === 'upload') return '请上传附件'
  if (type === 'select') return '请选择'
  if (type === 'textarea') return '请输入详细内容'
  return '请输入内容'
}

function handleTypeChange(type: FieldType) {
  if (!selectedField.value) return
  selectedField.value.options = needsOptions(type)
    ? selectedField.value.options.length ? selectedField.value.options : defaultOptions()
    : []
  selectedField.value.placeholder = getPlaceholder(type)
}

function addOption() {
  if (!selectedField.value) return
  const label = `选项${selectedField.value.options.length + 1}`
  selectedField.value.options.push({ label, value: label })
}

function removeOption(index: number) {
  selectedField.value?.options.splice(index, 1)
}

function duplicateField() {
  if (!selectedField.value) return
  const cloned = structuredClone(selectedField.value)
  cloned.fieldId = `${cloned.type}_${Date.now()}`
  cloned.label = `${cloned.label} 副本`
  formJson.fields.push(cloned)
  selectedFieldId.value = cloned.fieldId
  pushUndo()
}

function deleteSelectedField() {
  if (!selectedField.value) return
  const index = formJson.fields.findIndex((field) => field.fieldId === selectedField.value?.fieldId)
  formJson.fields.splice(index, 1)
  selectedFieldId.value = formJson.fields[Math.max(0, index - 1)]?.fieldId || ''
  pushUndo()
}

function syncJson() {
  // Reactive computed values update automatically; this hook keeps template events explicit.
}

async function copyJson() {
  await navigator.clipboard.writeText(formattedSchema.value)
  ElMessage.success('formSchema 已复制')
}

function normalizeFields(fields: FormField[]): NormalizedField[] {
  return fields.map((field) => ({
    field: field.fieldId.trim(),
    label: field.label.trim() || field.fieldId.trim(),
    type: normalizeType(field.type),
    required: Boolean(field.required),
    placeholder: field.placeholder || '',
    options: needsOptions(field.type) ? field.options : []
  })).filter((field) => field.field)
}

function normalizeType(type: FieldType) {
  if (type === 'text') return 'input'
  return type
}

function readSavedFields(formSchema?: string, fieldList?: string): FormField[] {
  const schema = parseJsonObject(formSchema)
  const schemaFields = Array.isArray(schema?.fields) ? schema.fields : null
  const listFields = parseJsonArray(fieldList)
  const source = schemaFields || listFields || []
  return source.map((raw, index) => toDesignerField(raw, index)).filter((field): field is FormField => Boolean(field))
}

function toDesignerField(raw: unknown, index: number): FormField | null {
  if (!raw || typeof raw !== 'object') return null
  const value = raw as Record<string, unknown>
  const fieldId = String(value.field || value.fieldId || value.name || '').trim()
  if (!fieldId) return null
  const type = toDesignerType(String(value.type || 'input'))
  return {
    fieldId,
    label: String(value.label || fieldId),
    type,
    required: Boolean(value.required),
    placeholder: String(value.placeholder || getPlaceholder(type)),
    options: Array.isArray(value.options) ? value.options.map(normalizeOption) : needsOptions(type) ? defaultOptions() : []
  }
}

function toDesignerType(type: string): FieldType {
  const normalized = type.toLowerCase()
  if (normalized === 'input') return 'text'
  if (normalized === 'file' || normalized === 'image') return 'upload'
  if (['text', 'textarea', 'number', 'select', 'radio', 'checkbox', 'date', 'datetime', 'upload'].includes(normalized)) {
    return normalized as FieldType
  }
  return 'text'
}

function normalizeOption(option: unknown): FieldOption {
  if (option && typeof option === 'object') {
    const value = option as Record<string, unknown>
    const optionValue = String(value.value ?? value.label ?? '')
    return { label: String(value.label ?? optionValue), value: optionValue }
  }
  return { label: String(option), value: String(option) }
}

function parseJsonObject(source?: string) {
  if (!source) return null
  try {
    const value = JSON.parse(source)
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : null
  } catch {
    return null
  }
}

function parseJsonArray(source?: string) {
  if (!source) return null
  try {
    const value = JSON.parse(source)
    return Array.isArray(value) ? value : null
  } catch {
    return null
  }
}

function statusLabel(status?: string) {
  if (status === 'published') return '已发布'
  if (status === 'disabled') return '已停用'
  return '草稿'
}

const PreviewInput = defineComponent({
  props: {
    field: { type: Object, required: true }
  },
  setup(props) {
    return () => {
      const field = props.field as FormField
      const ElInput = resolveComponent('ElInput')
      const ElInputNumber = resolveComponent('ElInputNumber')
      const ElDatePicker = resolveComponent('ElDatePicker')
      const ElSelect = resolveComponent('ElSelect')
      const ElOption = resolveComponent('ElOption')
      const ElRadioGroup = resolveComponent('ElRadioGroup')
      const ElRadio = resolveComponent('ElRadio')
      const ElCheckboxGroup = resolveComponent('ElCheckboxGroup')
      const ElCheckbox = resolveComponent('ElCheckbox')
      const ElButton = resolveComponent('ElButton')

      if (field.type === 'number') {
        return h(ElInputNumber, { placeholder: field.placeholder, controlsPosition: 'right', style: 'width: 100%' })
      }
      if (field.type === 'date' || field.type === 'datetime') {
        return h(ElDatePicker, { placeholder: field.placeholder, type: field.type, style: 'width: 100%' })
      }
      if (field.type === 'select') {
        return h(ElSelect, { placeholder: field.placeholder, style: 'width: 100%' }, () =>
          field.options.map((option) => h(ElOption, { label: option.label, value: option.value }))
        )
      }
      if (field.type === 'radio') {
        return h(ElRadioGroup, {}, () => field.options.map((option) => h(ElRadio, { value: option.value }, () => option.label)))
      }
      if (field.type === 'checkbox') {
        return h(ElCheckboxGroup, {}, () => field.options.map((option) => h(ElCheckbox, { value: option.value }, () => option.label)))
      }
      if (field.type === 'upload') {
        const ElIcon = resolveComponent('ElIcon')
        const UploadFilledIcon = resolveComponent('UploadFilled')
        return h('div', { style: 'display:flex;align-items:center;gap:8px;padding:8px 12px;border:1px dashed var(--el-border-color);border-radius:6px;background:var(--el-fill-color-lighter);color:var(--el-text-color-secondary);font-size:13px;cursor:default;' }, [
          h(ElIcon, {}, () => h(UploadFilledIcon)),
          h('span', field.placeholder || '点击或拖拽上传')
        ])
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

<style scoped>
.form-designer-page {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 104px);
}

.designer-panel,
.form-meta-panel,
.form-canvas-card,
.json-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
}

.designer-panel {
  padding: 16px;
}

.designer-panel-head,
.canvas-toolbar,
.json-title,
.field-card-main,
.canvas-actions,
.property-actions {
  display: flex;
  align-items: center;
}

.designer-panel-head,
.canvas-toolbar,
.json-title {
  justify-content: space-between;
  gap: 14px;
}

.designer-panel-head span {
  font-weight: 700;
}

.designer-panel-head small,
.canvas-toolbar p,
.canvas-field-card p,
.form-list-item span {
  color: var(--muted);
}

.form-list-panel {
  min-height: 0;
}

.form-list-scroll {
  margin-top: 14px;
  height: calc(100vh - 190px);
}

.form-list-item,
.field-type-card {
  width: 100%;
  border: 1px solid var(--line);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.form-list-item {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
  padding: 12px;
  border-radius: 8px;
}

.form-list-item.active,
.canvas-field-card.active {
  border-color: var(--el-color-success);
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.12);
}

.designer-canvas {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.canvas-toolbar {
  padding: 2px 0;
}

.canvas-toolbar h1 {
  margin: 8px 0 4px;
  font-size: 28px;
}

.canvas-toolbar p {
  margin: 0;
}

.canvas-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.form-meta-panel {
  padding: 16px 18px 0;
}

.designer-workspace {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.field-palette,
.property-panel {
  position: sticky;
  top: 86px;
}

.field-type-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 11px 12px;
  border-radius: 8px;
}

.field-type-card:hover,
.form-list-item:hover {
  border-color: var(--el-color-success);
}

.form-canvas-card {
  min-height: 420px;
  padding: 16px;
}

.canvas-field-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 0.9fr);
  gap: 16px;
  align-items: center;
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.field-card-main {
  gap: 12px;
  min-width: 0;
}

.field-card-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.field-card-main strong span {
  color: var(--el-color-danger);
}

.field-card-main p {
  margin: 4px 0 0;
  word-break: break-all;
}

.field-card-preview {
  min-width: 0;
}

.option-editor,
.property-actions {
  width: 100%;
}

.option-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.add-option-button {
  width: 100%;
}

.json-card {
  padding: 16px;
}

pre {
  max-height: 260px;
  margin: 12px 0 0;
  padding: 14px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}

@media (max-width: 1180px) {
  .form-designer-page,
  .designer-workspace {
    grid-template-columns: 1fr;
  }

  .field-palette,
  .property-panel {
    position: static;
  }

  .form-list-scroll {
    height: auto;
    max-height: 280px;
  }
}
</style>
