<template>
  <!-- 自动化策略配置页面：维护审批自动通过规则 -->
  <div class="automation-page">
    <!-- 页面标题区 -->
    <section class="page-head">
      <div>
        <h1>自动化策略配置</h1>
        <p>维护审批自动通过规则，支持按金额、天数等字段配置阈值，并可随时启用或停用。</p>
      </div>
      <el-button round type="success" :icon="Plus" @click="openCreate">新增规则</el-button>
    </section>

    <!-- 统计指标卡片 -->
    <section class="metric-row">
      <div class="metric-card">
        <span>全部规则</span>
        <strong>{{ rules.length }}</strong>
      </div>
      <div class="metric-card">
        <span>启用中</span>
        <strong>{{ enabledCount }}</strong>
      </div>
      <div class="metric-card">
        <span>自动通过条件</span>
        <strong>{{ autoApproveCount }}</strong>
      </div>
    </section>

    <!-- 筛选查询面板 -->
    <section class="query-panel">
      <el-form :inline="true" :model="query" class="query-form">
        <!-- 关键字搜索 -->
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="规则名称 / 适用流程" />
        </el-form-item>
        <!-- 启用/停用状态筛选 -->
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <!-- 字段类型筛选 -->
        <el-form-item label="字段">
          <el-select v-model="query.field" clearable placeholder="全部字段" style="width: 160px">
            <el-option label="请假天数" value="leaveDays" />
            <el-option label="申请天数" value="days" />
            <el-option label="金额" value="amount" />
          </el-select>
        </el-form-item>
        <!-- 重置按钮 -->
        <el-form-item>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 规则列表表格 -->
    <section class="table-panel">
      <el-table :data="filteredRules" border row-key="id" empty-text="暂无自动化策略">
        <!-- 启用开关（即时生效） -->
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="persistRules" />
          </template>
        </el-table-column>
        <!-- 规则名称 + 适用范围 -->
        <el-table-column label="规则名称" min-width="170">
          <template #default="{ row }">
            <div class="rule-name">
              <strong>{{ row.name }}</strong>
              <span>{{ row.scope || '全部流程模板' }}</span>
            </div>
          </template>
        </el-table-column>
        <!-- 触发条件 -->
        <el-table-column label="触发条件" min-width="220">
          <template #default="{ row }">
            <div class="condition-cell">
              <el-tag type="info" effect="plain">{{ fieldLabel(row.field) }}</el-tag>
              <span>{{ operatorLabel(row.operator) }} {{ row.value }}</span>
            </div>
          </template>
        </el-table-column>
        <!-- 命中动作 -->
        <el-table-column label="动作" width="150">
          <template #default="{ row }">
            <el-tag :type="row.action === 'approve' ? 'success' : 'warning'" effect="plain">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 更新时间 -->
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <!-- 操作：编辑 / 删除 -->
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新增/编辑策略抽屉 -->
    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑策略' : '新增策略'" size="420px">
      <el-form label-position="top" :model="form" class="drawer-form">
        <!-- 规则名称 -->
        <el-form-item label="规则名称">
          <el-input v-model="form.name" placeholder="例如 请假小于 3 天自动通过" />
        </el-form-item>
        <!-- 适用流程 -->
        <el-form-item label="适用流程">
          <el-input v-model="form.scope" placeholder="可填模板名称，留空表示全部流程" />
        </el-form-item>
        <!-- 规则字段（支持自定义输入） -->
        <el-form-item label="规则字段">
          <el-select v-model="form.field" allow-create filterable default-first-option>
            <el-option label="请假天数 leaveDays" value="leaveDays" />
            <el-option label="申请天数 days" value="days" />
            <el-option label="金额 amount" value="amount" />
          </el-select>
        </el-form-item>
        <!-- 判断条件：操作符 + 阈值 -->
        <el-form-item label="判断条件">
          <el-input v-model="form.value" placeholder="例如 3">
            <template #prepend>
              <el-select v-model="form.operator" style="width: 88px">
                <el-option label="<" value="<" />
                <el-option label="<=" value="<=" />
                <el-option label=">" value=">" />
                <el-option label=">=" value=">=" />
                <el-option label="==" value="==" />
                <el-option label="!=" value="!=" />
              </el-select>
            </template>
          </el-input>
        </el-form-item>
        <!-- 命中动作 -->
        <el-form-item label="命中动作">
          <el-select v-model="form.action">
            <el-option label="系统自动通过" value="approve" />
            <el-option label="仅提醒" value="notify" />
          </el-select>
        </el-form-item>
        <!-- 启用状态 -->
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <!-- 说明 -->
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="记录策略适用场景或审批说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="saveRule">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAutomationRules, saveAutomationRules, type AutomationRule } from '@/api/systemConfig'

type RuleAction = 'approve' | 'notify'
type RuleStatus = '' | 'enabled' | 'disabled'

/** 规则列表 */
const rules = ref<AutomationRule[]>([])
/** 抽屉可见性 */
const drawerVisible = ref(false)
/** 编辑模式下的规则 ID */
const editingId = ref('')
/** 数据加载状态 */
const loading = ref(false)
/** 查询条件 */
const query = reactive<{ keyword: string; status: RuleStatus; field: string }>({
  keyword: '',
  status: '',
  field: ''
})
/** 表单数据（不含 id 和 updatedAt） */
const form = reactive<Omit<AutomationRule, 'id' | 'updatedAt'>>({
  name: '',
  scope: '',
  field: 'leaveDays',
  operator: '<',
  value: '3',
  action: 'approve',
  enabled: true,
  remark: ''
})

/** 启用规则数量 */
const enabledCount = computed(() => rules.value.filter((rule) => rule.enabled).length)
/** 自动通过动作的规则数量 */
const autoApproveCount = computed(() => rules.value.filter((rule) => rule.action === 'approve').length)
/** 根据查询条件过滤后的规则列表 */
const filteredRules = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return rules.value.filter((rule) => {
    const matchKeyword = !keyword
      || rule.name.toLowerCase().includes(keyword)
      || rule.scope.toLowerCase().includes(keyword)
    const matchStatus = !query.status
      || (query.status === 'enabled' ? rule.enabled : !rule.enabled)
    const matchField = !query.field || rule.field === query.field
    return matchKeyword && matchStatus && matchField
  })
})

/** 页面挂载时加载规则列表 */
onMounted(() => {
  loadRules()
})

/** 加载自动化规则列表 */
async function loadRules() {
  loading.value = true
  try {
    rules.value = await getAutomationRules()
    // 首次加载无数据时初始化默认规则
    if (rules.value.length === 0) {
      rules.value = defaultRules()
      await persistRules()
    }
  } catch {
    // 后端不可用时回退到 localStorage 缓存
    ElMessage.warning('自动化策略服务暂不可用，使用本地缓存')
    const raw = localStorage.getItem('aiflow.automation.rules')
    if (raw) {
      try {
        rules.value = JSON.parse(raw)
        loading.value = false
        return
      } catch {
        localStorage.removeItem('aiflow.automation.rules')
      }
    }
    rules.value = defaultRules()
  } finally {
    loading.value = false
  }
}

/** 持久化规则列表到后端或 localStorage */
async function persistRules() {
  try {
    await saveAutomationRules(rules.value)
  } catch {
    // 后端不可用时回退到 localStorage
    localStorage.setItem('aiflow.automation.rules', JSON.stringify(rules.value))
  }
}

/** 打开新增规则抽屉 */
function openCreate() {
  editingId.value = ''
  Object.assign(form, {
    name: '',
    scope: '',
    field: 'leaveDays',
    operator: '<',
    value: '3',
    action: 'approve',
    enabled: true,
    remark: ''
  })
  drawerVisible.value = true
}

/** 打开编辑规则抽屉，预填表单数据 */
function openEdit(rule: AutomationRule) {
  editingId.value = rule.id
  Object.assign(form, {
    name: rule.name,
    scope: rule.scope,
    field: rule.field,
    operator: rule.operator,
    value: rule.value,
    action: rule.action,
    enabled: rule.enabled,
    remark: rule.remark
  })
  drawerVisible.value = true
}

/** 保存新增或编辑规则 */
function saveRule() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!form.field.trim() || !form.value.trim()) {
    ElMessage.warning('请完整填写规则条件')
    return
  }
  const payload: AutomationRule = {
    ...form,
    name: form.name.trim(),
    scope: form.scope.trim(),
    field: form.field.trim(),
    value: form.value.trim(),
    id: editingId.value || `rule_${Date.now()}`,
    updatedAt: formatNow()
  }
  if (editingId.value) {
    // 编辑模式：替换已有规则
    const index = rules.value.findIndex((rule) => rule.id === editingId.value)
    if (index >= 0) {
      rules.value[index] = payload
    }
  } else {
    // 新建模式：添加到列表头部
    rules.value.unshift(payload)
  }
  persistRules()
  drawerVisible.value = false
  ElMessage.success('策略已保存')
}

/** 删除规则（需二次确认） */
async function removeRule(rule: AutomationRule) {
  await ElMessageBox.confirm(`确认删除「${rule.name}」？`, '删除策略', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  rules.value = rules.value.filter((item) => item.id !== rule.id)
  persistRules()
  ElMessage.success('策略已删除')
}

/** 重置查询条件 */
function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.field = ''
}

/**
 * 获取默认规则列表（系统预设规则）
 * 对应后端 RuleEvaluatorService 的 approvalRule 配置
 */
function defaultRules(): AutomationRule[] {
  return [
    {
      id: 'rule_leave_lt_3',
      name: '请假小于 3 天自动通过',
      scope: '请假审批流程',
      field: 'leaveDays',
      operator: '<',
      value: '3',
      action: 'approve',
      enabled: true,
      remark: '对应 D6 RuleEvaluatorService 的 approvalRule 配置。',
      updatedAt: formatNow()
    },
    {
      id: 'rule_amount_lte_10000',
      name: '报销金额不超过 10000 自动通过',
      scope: '费用报销流程',
      field: 'amount',
      operator: '<=',
      value: '10000',
      action: 'approve',
      enabled: false,
      remark: '启用后可作为金额阈值策略参考。',
      updatedAt: formatNow()
    }
  ]
}

/** 字段名转中文标签 */
function fieldLabel(field: string) {
  const map: Record<string, string> = {
    leaveDays: '请假天数',
    days: '申请天数',
    amount: '金额'
  }
  return map[field] || field
}

/** 操作符转中文标签 */
function operatorLabel(operator: string) {
  const map: Record<string, string> = {
    '<': '小于',
    '<=': '小于等于',
    '>': '大于',
    '>=': '大于等于',
    '==': '等于',
    '!=': '不等于'
  }
  return map[operator] || operator
}

/** 动作类型转中文标签 */
function actionLabel(action: RuleAction) {
  return action === 'approve' ? '系统自动通过' : '仅提醒'
}

/** 获取当前时间格式化字符串 */
function formatNow() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped>
.automation-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head,
.query-panel,
.table-panel,
.metric-card {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
}

.page-head h1 {
  margin: 10px 0 6px;
  font-size: 28px;
}

.page-head p,
.rule-name span,
.metric-card span {
  margin: 0;
  color: var(--muted);
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96px;
  gap: 12px;
  padding: 16px 18px;
}

.metric-card span {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.25;
}

.metric-card strong {
  color: var(--primary-dark);
  flex-shrink: 0;
  font-size: 22px;
  line-height: 1;
}

.query-panel,
.table-panel {
  padding: 18px;
}

.query-form {
  row-gap: 8px;
}

.rule-name {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.condition-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.drawer-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 840px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-row {
    grid-template-columns: 1fr;
  }
}
</style>
