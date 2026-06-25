<template>
  <div class="admin-page user-admin-page">
    <div class="page-head">
      <div>
        <h1>用户管理</h1>
        <p>管理系统用户、角色分配、所属部门和业务管辖范围。</p>
      </div>
      <div class="head-actions">
        <el-button round :icon="Download" @click="handleExport">导出</el-button>
        <el-button round :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button type="primary" round :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <el-alert v-if="msg" :title="msg" :type="msgType" show-icon closable @close="msg = ''" />

    <section class="table-panel" v-loading="loading">
      <el-table :data="users" border stripe table-layout="fixed">
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="nickname" label="昵称" min-width="130" />
        <el-table-column label="系统角色" width="130">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.systemRole)" size="small" effect="plain">
              {{ roleLabel(row.systemRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属部门" min-width="140">
          <template #default="{ row }">{{ deptName(row.departmentId) }}</template>
        </el-table-column>
        <el-table-column label="管辖业务" min-width="190">
          <template #default="{ row }">
            <span v-if="row.managedBizTypeIds" class="small-text">
              {{ bizTypeNames(row.managedBizTypeIds) || row.managedBizTypeIds }}
            </span>
            <span v-else class="muted-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item :label="isEdit ? '新密码（留空不修改）' : '密码'" :required="!isEdit">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="页面展示名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="系统角色">
              <el-select v-model="form.systemRole" style="width: 100%">
                <el-option label="超级管理员" value="super_admin" />
                <el-option label="业务管理员" value="biz_admin" />
                <el-option label="普通用户" value="normal_user" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门">
              <el-select v-model="form.departmentId" clearable filterable placeholder="选择部门" style="width: 100%">
                <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="直属上级">
          <el-select v-model="form.supervisorId" clearable filterable placeholder="选择上级用户" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.value" :label="u.label" :value="u.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.systemRole === 'biz_admin'" label="管辖业务类型">
          <el-select v-model="form.bizTypeList" multiple filterable placeholder="选择可管理的业务类型" style="width: 100%">
            <el-option v-for="b in bizTypeOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
          <div class="hint">业务管理员只能查看和管理管辖范围内的流程数据。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.enabledBool"
            active-text="启用"
            inactive-text="禁用"
            :active-value="true"
            :inactive-value="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="dialogVisible = false">取消</el-button>
        <el-button round type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ImportExcelDialog
      v-model:visible="importVisible"
      title="导入用户"
      :template-url="USER_TEMPLATE_URL"
      :import-url="USER_IMPORT_URL"
      @imported="loadUsers"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Upload } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser, USER_TEMPLATE_URL, USER_EXPORT_URL, USER_IMPORT_URL } from '@/api/admin'
import type { AdminUser, CreateUserPayload, UpdateUserPayload } from '@/api/admin'
import request from '@/api/request'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import { downloadBlob } from '@/utils/download'

type TagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

interface Option {
  value: number
  label: string
}

const loading = ref(false)
const saving = ref(false)
const users = ref<AdminUser[]>([])
const dialogVisible = ref(false)
const importVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const deptOptions = ref<Option[]>([])
const userOptions = ref<Option[]>([])
const bizTypeOptions = ref<Option[]>([])

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  systemRole: 'normal_user',
  departmentId: null as number | null,
  supervisorId: null as number | null,
  bizTypeList: [] as number[],
  enabledBool: true
})

onMounted(async () => {
  await Promise.all([loadUsers(), loadOptions()])
})

async function loadUsers() {
  loading.value = true
  try {
    users.value = await getUsers()
  } catch (e: any) {
    msg.value = e?.message || '加载用户失败'
    msgType.value = 'error'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [depts, usersOpts, bizTypes] = await Promise.all([
      request.get<Option[]>('/api/admin/departments/options'),
      request.get<Option[]>('/api/admin/users/options'),
      request.get<Option[]>('/api/admin/biz-types/options')
    ])
    deptOptions.value = depts
    userOptions.value = usersOpts
    bizTypeOptions.value = bizTypes
  } catch {
    // 下拉选项加载失败时，主列表仍可使用。
  }
}

function deptName(id: number | null | undefined) {
  if (id == null) return '-'
  return deptOptions.value.find(d => d.value === id)?.label || String(id)
}

function bizTypeNames(ids: string | null | undefined) {
  if (!ids) return ''
  try {
    const arr = JSON.parse(ids) as number[]
    return arr.map(id => bizTypeOptions.value.find(b => b.value === id)?.label || id).join(', ')
  } catch {
    return ids
  }
}

function roleTag(role: string): TagType {
  const map: Record<string, TagType> = {
    super_admin: 'danger',
    biz_admin: 'warning',
    normal_user: 'info'
  }
  return map[role] || 'info'
}

function roleLabel(role: string) {
  const map: Record<string, string> = {
    super_admin: '超级管理员',
    biz_admin: '业务管理员',
    normal_user: '普通用户'
  }
  return map[role] || role
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.systemRole = 'normal_user'
  form.departmentId = null
  form.supervisorId = null
  form.bizTypeList = []
  form.enabledBool = true
  dialogVisible.value = true
}

function openEdit(row: AdminUser) {
  isEdit.value = true
  editId.value = row.id
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname || ''
  form.systemRole = row.systemRole || 'normal_user'
  form.departmentId = row.departmentId ?? null
  form.supervisorId = row.supervisorId ?? null
  form.bizTypeList = parseBizIds(row.managedBizTypeIds)
  form.enabledBool = row.enabled === 1
  dialogVisible.value = true
}

function parseBizIds(ids: string | null | undefined): number[] {
  if (!ids) return []
  try {
    return JSON.parse(ids) as number[]
  } catch {
    return []
  }
}

async function handleSave() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!isEdit.value && !form.password) {
    ElMessage.warning('请输入密码')
    return
  }

  saving.value = true
  try {
    const managedBizTypeIds =
      form.systemRole === 'biz_admin' && form.bizTypeList.length > 0
        ? JSON.stringify(form.bizTypeList)
        : null

    if (isEdit.value && editId.value) {
      const payload: UpdateUserPayload = {
        nickname: form.nickname,
        systemRole: form.systemRole,
        password: form.password || undefined,
        departmentId: form.departmentId,
        supervisorId: form.supervisorId,
        managedBizTypeIds,
        enabled: form.enabledBool ? 1 : 0
      }
      await updateUser(editId.value, payload)
      ElMessage.success('用户已更新')
    } else {
      const payload: CreateUserPayload = {
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname,
        systemRole: form.systemRole,
        departmentId: form.departmentId,
        supervisorId: form.supervisorId,
        managedBizTypeIds,
        enabled: form.enabledBool ? 1 : 0
      }
      await createUser(payload)
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    await loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleExport() {
  try {
    await downloadBlob(USER_EXPORT_URL, '用户数据.xlsx')
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function handleDelete(row: AdminUser) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.nickname || row.username}」吗？`, '确认删除', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await loadUsers()
  } catch {
    // 用户取消删除。
  }
}
</script>

<style scoped>
.user-admin-page {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 20px;
}

.page-head h1 {
  margin: 0 0 4px;
  font-size: 24px;
}

.page-head p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.table-panel {
  width: 100%;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow);
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.small-text {
  font-size: 12px;
}

.muted-text,
.hint {
  color: var(--muted);
}

.hint {
  margin-top: 4px;
  font-size: 12px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 760px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
