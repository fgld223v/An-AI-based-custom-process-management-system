<template>
  <div class="sys-config-page">
    <!-- Hero -->
    <section class="page-hero">
      <div>
        <el-tag type="success" effect="plain">系统管理</el-tag>
        <h1>系统配置</h1>
        <p>管理系统全局运行参数，所有修改即时生效，请谨慎操作</p>
      </div>
      <el-button type="success" :icon="Plus" round @click="openCreate">新增配置</el-button>
    </section>

    <!-- Config Cards Grid -->
    <section class="config-grid" v-loading="loading">
      <div v-for="item in configs" :key="item.id" class="config-card">
        <div class="card-header">
          <div class="card-title-row">
            <div class="card-icon" :class="typeClass(item.valueType)">
              <el-icon :size="18"><component :is="typeIcon(item.valueType)" /></el-icon>
            </div>
            <div>
              <h3>{{ item.configName }}</h3>
              <code class="card-key">{{ item.configKey }}</code>
            </div>
          </div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, item)">
            <el-button text circle :icon="MoreFilled" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="!item.editable">
                  <el-icon><Edit /></el-icon> 修改
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <el-icon><Delete /></el-icon> <span class="danger-text">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="card-body">
          <div class="value-display">
            <template v-if="item.valueType === 'bool'">
              <el-switch
                :model-value="item.configValue === 'true'"
                :disabled="!item.editable"
                @change="(val: boolean) => quickToggle(item, val)"
              />
              <span class="value-label" :class="item.configValue === 'true' ? 'on' : 'off'">
                {{ item.configValue === 'true' ? '已开启' : '已关闭' }}
              </span>
            </template>
            <template v-else-if="item.valueType === 'int' || item.valueType === 'float'">
              <span class="value-number">{{ item.configValue }}</span>
              <el-tag size="small" effect="plain" type="info">{{ item.valueType === 'float' ? '浮点' : '整数' }}</el-tag>
            </template>
            <template v-else-if="item.valueType === 'json'">
              <el-tag size="small" effect="plain" type="warning">JSON</el-tag>
              <code class="value-json">{{ item.configValue?.substring(0, 80) }}{{ (item.configValue?.length || 0) > 80 ? '…' : '' }}</code>
            </template>
            <template v-else>
              <span class="value-text">{{ item.configValue }}</span>
            </template>
          </div>
          <p class="card-desc" v-if="item.description">{{ item.description }}</p>
        </div>

        <div class="card-footer">
          <el-tag size="small" effect="plain">{{ typeLabel(item.valueType) }}</el-tag>
          <span class="footer-time">{{ item.updatedAt ? formatTime(item.updatedAt) : '' }}</span>
        </div>
      </div>

      <el-empty v-if="!loading && configs.length === 0" description="暂无配置项，点击右上角新增" :image-size="80" />
    </section>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isCreating ? '新增系统配置' : '修改系统配置'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-position="left">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如：AI 置信度阈值" maxlength="128" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如：ai.confidence.threshold" maxlength="128" :disabled="!isCreating" />
        </el-form-item>
        <el-form-item label="值类型" prop="valueType">
          <el-select v-model="form.valueType" :disabled="!isCreating" @change="onTypeChange">
            <el-option label="字符串 (string)" value="string" />
            <el-option label="整数 (int)" value="int" />
            <el-option label="浮点 (float)" value="float" />
            <el-option label="布尔 (bool)" value="bool" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <template v-if="form.valueType === 'bool'">
            <el-switch v-model="form.boolVal" active-text="true" inactive-text="false" />
          </template>
          <template v-else-if="form.valueType === 'int' || form.valueType === 'float'">
            <el-input-number v-model="form.numVal" :precision="form.valueType === 'float' ? 2 : 0" :min="0" style="width:100%" />
          </template>
          <template v-else>
            <el-input v-model="form.configValue" :type="form.valueType === 'json' ? 'textarea' : 'text'" :rows="3" placeholder="输入配置值" />
          </template>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="配置项用途说明" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="dialogVisible = false">取消</el-button>
        <el-button round type="success" :loading="saving" @click="submitForm">
          {{ isCreating ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Edit, MoreFilled, Plus, Setting, Switch, Tickets } from '@element-plus/icons-vue'
import { createSystemConfig, deleteSystemConfig, getSystemConfigList, updateSystemConfig } from '@/api/systemConfig'

interface ConfigItem {
  id: number
  configKey: string
  configName: string
  configValue: string
  valueType: string
  description: string
  editable: number
  updatedAt?: string
}

const loading = ref(false)
const saving = ref(false)
const configs = ref<ConfigItem[]>([])
const dialogVisible = ref(false)
const isCreating = ref(false)
const editingItem = ref<ConfigItem | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  configName: '',
  configKey: '',
  configValue: '',
  valueType: 'string',
  description: '',
  boolVal: false,
  numVal: 0,
})

const rules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configKey: [
    { required: true, message: '请输入配置键', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9._-]*$/, message: '仅支持字母开头，可含数字、点、下划线、连字符', trigger: 'blur' },
  ],
  valueType: [{ required: true, message: '请选择值类型', trigger: 'change' }],
}

onMounted(loadConfigs)

async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await getSystemConfigList() || []
  } finally { loading.value = false }
}

function openCreate() {
  isCreating.value = true
  editingItem.value = null
  form.configName = ''
  form.configKey = ''
  form.configValue = ''
  form.valueType = 'string'
  form.description = ''
  form.boolVal = false
  form.numVal = 0
  dialogVisible.value = true
}

function openEdit(item: ConfigItem) {
  isCreating.value = false
  editingItem.value = item
  form.configName = item.configName
  form.configKey = item.configKey
  form.valueType = item.valueType
  form.description = item.description || ''
  if (item.valueType === 'bool') {
    form.boolVal = item.configValue === 'true'
  } else if (item.valueType === 'int' || item.valueType === 'float') {
    form.numVal = Number(item.configValue) || 0
  } else {
    form.configValue = item.configValue || ''
  }
  dialogVisible.value = true
}

function handleCommand(cmd: string, item: ConfigItem) {
  if (cmd === 'edit') openEdit(item)
  else if (cmd === 'delete') confirmDelete(item)
}

function onTypeChange() {
  form.configValue = ''
  form.boolVal = false
  form.numVal = 0
}

async function quickToggle(item: ConfigItem, val: boolean) {
  try {
    await updateSystemConfig(item.configKey, String(val))
    item.configValue = String(val)
    ElMessage.success(`${item.configName} 已${val ? '开启' : '关闭'}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '切换失败')
  }
}

async function submitForm() {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    let configValue: string
    if (form.valueType === 'bool') {
      configValue = String(form.boolVal)
    } else if (form.valueType === 'int' || form.valueType === 'float') {
      configValue = String(form.numVal)
    } else {
      configValue = form.configValue
    }

    if (isCreating.value) {
      await createSystemConfig({
        configKey: form.configKey.trim(),
        configName: form.configName.trim(),
        configValue,
        valueType: form.valueType,
        description: form.description.trim(),
      })
      ElMessage.success('配置项已创建')
    } else {
      await updateSystemConfig(form.configKey, configValue)
      ElMessage.success('配置已更新')
    }
    dialogVisible.value = false
    await loadConfigs()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally { saving.value = false }
}

async function confirmDelete(item: ConfigItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除配置「${item.configName}」吗？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  try {
    await deleteSystemConfig(item.configKey)
    ElMessage.success('配置已删除')
    await loadConfigs()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// --- helpers ---
function typeClass(vt: string) {
  const m: Record<string, string> = { bool: 't-bool', int: 't-num', float: 't-num', json: 't-json' }
  return m[vt] || 't-str'
}
function typeIcon(vt: string) {
  const m: Record<string, any> = { bool: Switch, int: Tickets, float: Tickets, json: Setting }
  return m[vt] || Setting
}
function typeLabel(vt: string) {
  const m: Record<string, string> = { string: '字符串', int: '整数', float: '浮点', bool: '布尔', json: 'JSON' }
  return m[vt] || vt
}
function formatTime(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : ''
}
</script>

<style scoped>
.sys-config-page { display: flex; flex-direction: column; gap: 22px; }

/* ── Hero ── */
.page-hero {
  display: flex; align-items: center; justify-content: space-between; gap: 18px;
  padding: 26px 30px; border: 1px solid var(--line); border-radius: 20px;
  background: rgba(255,255,255,0.94); box-shadow: var(--shadow);
}
.page-hero h1 { margin: 10px 0 6px; font-size: 28px; font-weight: 800; }
.page-hero p { margin: 0; color: var(--muted); font-size: 14px; }

/* ── Card Grid ── */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 18px;
  min-height: 200px;
}

.config-card {
  display: flex; flex-direction: column;
  border: 1px solid var(--line); border-radius: 16px;
  background: rgba(255,255,255,0.94); box-shadow: var(--shadow);
  transition: box-shadow 0.18s, transform 0.18s;
  overflow: hidden;
}
.config-card:hover { box-shadow: 0 8px 30px rgba(28,55,47,0.1); transform: translateY(-1px); }

.card-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 18px 20px 0;
}
.card-title-row { display: flex; align-items: center; gap: 14px; flex: 1; min-width: 0; }
.card-icon {
  width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.card-icon.t-str  { background: #f0f4ff; color: #5470c6; }
.card-icon.t-bool { background: #e6fffb; color: #13c2c2; }
.card-icon.t-num  { background: #fff7e6; color: #fa8c16; }
.card-icon.t-json { background: #fff0f6; color: #eb2f96; }

.card-header h3 { margin: 0; font-size: 16px; font-weight: 700; }
.card-key { margin-top: 3px; font-size: 12px; color: #94a3b8; display: block; word-break: break-all; }

.card-body { padding: 14px 20px; flex: 1; }
.value-display { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.value-number { font-size: 28px; font-weight: 800; color: var(--text); }
.value-text  { font-size: 20px; font-weight: 700; color: var(--text); word-break: break-all; }
.value-json  { font-size: 12px; color: var(--muted); word-break: break-all; }
.value-label { font-size: 14px; font-weight: 600; }
.value-label.on  { color: #13c2c2; }
.value-label.off { color: #94a3b8; }

.card-desc { margin: 12px 0 0; font-size: 13px; color: var(--muted); line-height: 1.6; }

.card-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px 16px;
}
.footer-time { font-size: 12px; color: #c0c4cc; }

.danger-text { color: #e74c3c; }

@media (max-width: 860px) {
  .config-grid { grid-template-columns: 1fr; }
  .page-hero { flex-direction: column; align-items: flex-start; }
}
</style>
