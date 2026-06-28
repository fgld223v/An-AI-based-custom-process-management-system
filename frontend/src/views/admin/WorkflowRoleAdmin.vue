<template>
  <!-- 流程角色管理页面：维护业务审批职责及角色成员授权 -->
  <div class="admin-page role-admin-page">
    <!-- 页面标题区 -->
    <header class="page-head">
      <div>
        <h1>流程角色管理</h1>
        <p>维护业务审批职责，以及角色在全局或部门范围内的成员。</p>
      </div>
      <el-button type="primary" round :icon="Plus" @click="openCreate">新增角色</el-button>
    </header>

    <!-- 角色列表表格 -->
    <section class="table-panel" v-loading="loading">
      <el-table :data="roles" border stripe table-layout="fixed">
        <!-- 角色名称 -->
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <!-- 角色编码 -->
        <el-table-column prop="roleCode" label="角色编码" min-width="185" />
        <!-- 作用范围：全局 / 部门 -->
        <el-table-column label="作用范围" width="120">
          <template #default="{ row }">
            <el-tag :type="row.roleScope === 'global' ? 'warning' : 'info'" effect="plain">
              {{ scopeLabel(row.roleScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 说明 -->
        <el-table-column prop="description" label="说明" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <!-- 授权成员数 -->
        <el-table-column prop="memberCount" label="授权数" width="100" align="center" />
        <!-- 启用/停用状态 -->
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 操作：成员管理 / 编辑 / 删除 -->
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" @click="openAssignments(row)">成员</el-button>
              <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新增/编辑角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" :title="isEdit ? '编辑流程角色' : '新增流程角色'" width="520px" destroy-on-close>
      <el-form :model="roleForm" label-position="top">
        <!-- 角色名称 -->
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="例如：财务审核员" />
        </el-form-item>
        <!-- 角色编码（编辑时不可修改，带自动格式化和校验提示） -->
        <el-form-item label="角色编码" required :error="roleCodeError">
          <el-input
            v-model="roleForm.roleCode"
            :disabled="isEdit"
            maxlength="64"
            placeholder="例如：FINANCE_APPROVER"
            @input="normalizeRoleCode"
          />
          <div v-if="!roleCodeError" class="field-hint">以英文字母开头，只能包含字母、数字和下划线。</div>
        </el-form-item>
        <!-- 作用范围：部门范围 / 全局范围 -->
        <el-form-item label="作用范围" required>
          <el-segmented v-model="roleForm.roleScope" :disabled="isEdit" :options="scopeOptions" block />
        </el-form-item>
        <!-- 说明 -->
        <el-form-item label="说明">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" maxlength="300" show-word-limit />
        </el-form-item>
        <!-- 编辑模式下可修改启用状态 -->
        <el-form-item v-if="isEdit" label="状态">
          <el-switch v-model="roleForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员授权弹窗 -->
    <el-dialog v-model="assignmentDialogVisible" :title="assignmentTitle" width="780px" destroy-on-close>
      <!-- 授权工具栏：部门选择（部门范围时）+ 用户选择 + 授权按钮 -->
      <div class="assignment-toolbar">
        <el-select
          v-if="selectedRole?.roleScope === 'department'"
          v-model="assignmentForm.departmentId"
          filterable
          placeholder="选择部门"
          @change="assignmentForm.userId = null"
        >
          <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="assignmentForm.userId" filterable placeholder="选择成员" :disabled="!canSelectUser">
          <el-option v-for="item in assignableUsers" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button type="primary" :icon="UserFilled" :loading="assigning" @click="addAssignment">授权</el-button>
      </div>

      <!-- 已授权成员列表 -->
      <el-table :data="assignments" border v-loading="assignmentLoading" empty-text="暂无授权成员">
        <!-- 成员名称 -->
        <el-table-column prop="userName" label="成员" min-width="150" />
        <!-- 账号 -->
        <el-table-column prop="username" label="账号" min-width="130" />
        <!-- 授权部门（全局角色显示"全局"） -->
        <el-table-column label="部门" min-width="150">
          <template #default="{ row }">{{ row.departmentName || '全局' }}</template>
        </el-table-column>
        <!-- 授权时间 -->
        <el-table-column prop="createdAt" label="授权时间" min-width="170" />
        <!-- 撤销操作 -->
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text type="danger" @click="revokeAssignment(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UserFilled } from '@element-plus/icons-vue'
import { getOrganizationDepartments, getOrganizationUsers } from '@/api/organizationDirectory'
import type { OrganizationDepartmentOption, OrganizationUserOption } from '@/api/organizationDirectory'
import {
  assignWorkflowRole,
  createWorkflowRole,
  deleteWorkflowRole,
  getRoleAssignments,
  getWorkflowRoles,
  revokeWorkflowRoleAssignment,
  updateWorkflowRole
} from '@/api/workflowRoleAdmin'
import type { WorkflowRole, WorkflowRoleAssignment, WorkflowRoleScope } from '@/api/workflowRoleAdmin'

/** 作用范围选项 */
const scopeOptions = [
  { label: '部门范围', value: 'department' },
  { label: '全局范围', value: 'global' }
]
/** 角色列表加载状态 */
const loading = ref(false)
/** 角色保存加载状态 */
const saving = ref(false)
/** 授权操作加载状态 */
const assigning = ref(false)
/** 成员列表加载状态 */
const assignmentLoading = ref(false)
/** 角色列表 */
const roles = ref<WorkflowRole[]>([])
/** 部门列表 */
const departments = ref<OrganizationDepartmentOption[]>([])
/** 用户列表 */
const users = ref<OrganizationUserOption[]>([])
/** 授权成员列表 */
const assignments = ref<WorkflowRoleAssignment[]>([])
/** 角色弹窗可见性 */
const roleDialogVisible = ref(false)
/** 成员授权弹窗可见性 */
const assignmentDialogVisible = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)
/** 编辑模式下的角色 ID */
const editId = ref<number | null>(null)
/** 当前选中的角色 */
const selectedRole = ref<WorkflowRole | null>(null)

/** 角色表单数据 */
const roleForm = reactive({
  roleName: '',
  roleCode: '',
  roleScope: 'department' as WorkflowRoleScope,
  description: '',
  enabled: 1
})
/** 授权表单数据 */
const assignmentForm = reactive({
  departmentId: null as number | null,
  userId: null as number | null
})

/** 授权弹窗标题 */
const assignmentTitle = computed(() => selectedRole.value ? `${selectedRole.value.roleName} - 成员授权` : '成员授权')
/** 角色编码校验错误信息 */
const roleCodeError = computed(() => {
  if (isEdit.value || !roleForm.roleCode) return ''
  return /^[A-Z][A-Z0-9_]{0,63}$/.test(roleForm.roleCode)
    ? ''
    : '角色编码必须以英文字母开头，只能包含字母、数字和下划线'
})
/** 是否可以选择用户（全局角色或已选部门） */
const canSelectUser = computed(() => selectedRole.value?.roleScope === 'global' || Boolean(assignmentForm.departmentId))
/** 可授权的用户列表（排除已授权成员） */
const assignableUsers = computed(() => {
  const selectedIds = new Set(assignments.value
    .filter(item => selectedRole.value?.roleScope === 'global' || item.departmentId === assignmentForm.departmentId)
    .map(item => item.userId))
  return users.value.filter(item => {
    const sameDepartment = selectedRole.value?.roleScope === 'global'
      || item.departmentId === assignmentForm.departmentId
    return sameDepartment && !selectedIds.has(item.id)
  })
})

/** 页面挂载时并行加载角色和组织目录 */
onMounted(async () => {
  await Promise.all([loadRoles(), loadDirectory()])
})

/** 加载角色列表 */
async function loadRoles() {
  loading.value = true
  try {
    roles.value = await getWorkflowRoles()
  } finally {
    loading.value = false
  }
}

/** 加载组织目录（部门 + 用户） */
async function loadDirectory() {
  const [departmentItems, userItems] = await Promise.all([
    getOrganizationDepartments(),
    getOrganizationUsers()
  ])
  departments.value = departmentItems
  users.value = userItems
}

/** 作用范围转中文标签 */
function scopeLabel(scope: WorkflowRoleScope) {
  return scope === 'global' ? '全局' : '部门'
}

/**
 * 标准化角色编码：转为大写并替换非法字符为下划线
 * @param value - 原始输入值
 */
function normalizeRoleCode(value: string) {
  roleForm.roleCode = value.toUpperCase().replace(/[^A-Z0-9_]/g, '_')
}

/** 重置角色表单为初始状态 */
function resetRoleForm() {
  roleForm.roleName = ''
  roleForm.roleCode = ''
  roleForm.roleScope = 'department'
  roleForm.description = ''
  roleForm.enabled = 1
}

/** 打开新建角色弹窗 */
function openCreate() {
  isEdit.value = false
  editId.value = null
  resetRoleForm()
  roleDialogVisible.value = true
}

/** 打开编辑角色弹窗，预填表单数据 */
function openEdit(role: WorkflowRole) {
  isEdit.value = true
  editId.value = role.id
  roleForm.roleName = role.roleName
  roleForm.roleCode = role.roleCode
  roleForm.roleScope = role.roleScope
  roleForm.description = role.description || ''
  roleForm.enabled = role.enabled
  roleDialogVisible.value = true
}

/** 保存新增或编辑角色 */
async function saveRole() {
  if (!roleForm.roleName.trim() || !roleForm.roleCode.trim()) {
    ElMessage.warning('请填写角色名称和角色编码')
    return
  }
  if (roleCodeError.value) {
    ElMessage.warning(roleCodeError.value)
    return
  }
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      // 更新角色（编码和作用范围不可修改）
      await updateWorkflowRole(editId.value, {
        roleName: roleForm.roleName.trim(),
        description: roleForm.description.trim(),
        enabled: roleForm.enabled
      })
    } else {
      // 创建新角色
      await createWorkflowRole({
        roleName: roleForm.roleName.trim(),
        roleCode: roleForm.roleCode.trim(),
        roleScope: roleForm.roleScope,
        description: roleForm.description.trim(),
        enabled: 1
      })
    }
    ElMessage.success(isEdit.value ? '流程角色已更新' : '流程角色已创建')
    roleDialogVisible.value = false
    await loadRoles()
  } finally {
    saving.value = false
  }
}

/** 删除角色（需二次确认，将同时撤销全部成员授权） */
async function handleDelete(role: WorkflowRole) {
  try {
    await ElMessageBox.confirm(`删除"${role.roleName}"将同时撤销全部成员授权，是否继续？`, '删除流程角色', { type: 'warning' })
    await deleteWorkflowRole(role.id)
    ElMessage.success('流程角色已删除')
    await loadRoles()
  } catch {
    // User cancelled the confirmation.
  }
}

/** 打开成员授权弹窗 */
async function openAssignments(role: WorkflowRole) {
  selectedRole.value = role
  assignmentForm.departmentId = null
  assignmentForm.userId = null
  assignmentDialogVisible.value = true
  await loadAssignments()
}

/** 加载角色成员授权列表 */
async function loadAssignments() {
  if (!selectedRole.value) return
  assignmentLoading.value = true
  try {
    assignments.value = await getRoleAssignments(selectedRole.value.id)
  } finally {
    assignmentLoading.value = false
  }
}

/** 添加角色成员授权 */
async function addAssignment() {
  if (!selectedRole.value || !assignmentForm.userId) {
    ElMessage.warning('请选择需要授权的成员')
    return
  }
  if (selectedRole.value.roleScope === 'department' && !assignmentForm.departmentId) {
    ElMessage.warning('请选择授权部门')
    return
  }
  assigning.value = true
  try {
    await assignWorkflowRole(selectedRole.value.id, assignmentForm.userId, assignmentForm.departmentId)
    assignmentForm.userId = null
    ElMessage.success('成员授权成功')
    // 刷新授权列表和角色列表（memberCount 会变化）
    await Promise.all([loadAssignments(), loadRoles()])
  } finally {
    assigning.value = false
  }
}

/** 撤销角色成员授权 */
async function revokeAssignment(assignment: WorkflowRoleAssignment) {
  try {
    await ElMessageBox.confirm(`确认撤销"${assignment.userName}"的角色授权？`, '撤销授权', { type: 'warning' })
    await revokeWorkflowRoleAssignment(assignment.id)
    ElMessage.success('授权已撤销')
    await Promise.all([loadAssignments(), loadRoles()])
  } catch {
    // User cancelled the confirmation.
  }
}
</script>

<style scoped>
.role-admin-page { width: 100%; max-width: 1280px; margin: 0 auto; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; gap: 20px; }
.page-head h1 { margin: 0 0 4px; font-size: 24px; }
.page-head p { margin: 0; color: var(--muted); font-size: 13px; }
.table-panel { width: 100%; min-width: 0; padding: 16px; border: 1px solid var(--line); border-radius: 8px; background: #fff; box-shadow: var(--shadow); }
.table-actions { display: flex; align-items: center; justify-content: center; gap: 4px; white-space: nowrap; }
.table-actions :deep(.el-button + .el-button) { margin-left: 0; }
.field-hint { margin-top: 4px; color: var(--muted); font-size: 12px; }
.assignment-toolbar { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.2fr) auto; gap: 12px; margin-bottom: 16px; }
@media (max-width: 760px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .assignment-toolbar { grid-template-columns: 1fr; }
}
</style>
