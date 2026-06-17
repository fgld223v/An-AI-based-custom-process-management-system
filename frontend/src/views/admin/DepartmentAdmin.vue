<template>
  <div class="page-shell dept-admin-page">
    <div class="page-head">
      <div><h1>部门管理</h1><p>组织架构部门 CRUD</p></div>
      <el-button type="primary" round :icon="Plus" @click="openCreate">新增部门</el-button>
    </div>

    <section class="table-panel" v-loading="loading">
      <el-table :data="departments" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="父部门" width="120">
          <template #default="{ row }">{{ deptName(row.parentId) }}</template>
        </el-table-column>
        <el-table-column prop="deptCode" label="部门编码" min-width="120" />
        <el-table-column prop="deptName" label="部门名称" min-width="140" />
        <el-table-column label="负责人" min-width="120">
          <template #default="{ row }">{{ userName(row.leaderUserId) }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑部门' : '新增部门'" width="520px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门编码" required>
              <el-input v-model="form.deptCode" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门名称" required>
              <el-input v-model="form.deptName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="父部门">
              <el-select v-model="form.parentId" clearable filterable placeholder="无（顶级部门）" style="width:100%">
                <el-option v-for="d in deptOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.leaderUserId" clearable filterable placeholder="选择负责人" style="width:100%">
                <el-option v-for="u in userOptions" :key="u.value" :label="u.label" :value="u.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" style="width:100%" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button round @click="dialogVisible = false">取消</el-button>
        <el-button round type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/api/request'

interface Department { id: number; parentId?: number | null; deptCode: string; deptName: string; sortOrder: number; leaderUserId?: number | null; status: number }
interface Option { value: number; label: string }

const loading = ref(false); const saving = ref(false)
const departments = ref<Department[]>([])
const deptOptions = ref<Option[]>([])
const userOptions = ref<Option[]>([])
const dialogVisible = ref(false); const isEdit = ref(false); const editId = ref<number | null>(null)

const form = reactive({ deptCode: '', deptName: '', parentId: null as number | null, leaderUserId: null as number | null, sortOrder: 0 })

onMounted(async () => { await Promise.all([loadDepts(), loadOptions()]) })

async function loadDepts() {
  loading.value = true
  try { departments.value = await request.get('/api/admin/departments') }
  finally { loading.value = false }
}

async function loadOptions() {
  try {
    const [depts, users] = await Promise.all([
      request.get('/api/admin/departments/options'),
      request.get('/api/admin/users/options')
    ])
    deptOptions.value = depts as Option[]
    userOptions.value = users as Option[]
  } catch { /* ignore */ }
}

function deptName(id: number | null | undefined) {
  if (id == null) return '—'
  return deptOptions.value.find(d => d.value === id)?.label || String(id)
}
function userName(id: number | null | undefined) {
  if (id == null) return '—'
  return userOptions.value.find(u => u.value === id)?.label || String(id)
}

function openCreate() {
  isEdit.value = false; editId.value = null
  form.deptCode = ''; form.deptName = ''; form.parentId = null; form.leaderUserId = null; form.sortOrder = 0
  dialogVisible.value = true
}
function openEdit(row: Department) {
  isEdit.value = true; editId.value = row.id
  form.deptCode = row.deptCode; form.deptName = row.deptName
  form.parentId = row.parentId ?? null; form.leaderUserId = row.leaderUserId ?? null; form.sortOrder = row.sortOrder
  dialogVisible.value = true
}
async function handleSave() {
  if (!form.deptCode.trim() || !form.deptName.trim()) { ElMessage.warning('请填写完整'); return }
  saving.value = true
  try {
    if (isEdit.value && editId.value) { await request.put(`/api/admin/departments/${editId.value}`, form) }
    else { await request.post('/api/admin/departments', form) }
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    dialogVisible.value = false; await loadDepts()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { saving.value = false }
}
async function handleDelete(row: Department) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.deptName}」吗？`, '确认删除', { type: 'warning' })
    await request.delete(`/api/admin/departments/${row.id}`)
    ElMessage.success('已删除'); await loadDepts()
  } catch { /* cancel */ }
}
</script>

<style scoped>
.dept-admin-page { max-width: 1000px; margin: 0 auto; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.page-head h1 { margin: 0 0 4px; font-size: 24px; }
.page-head p { margin: 0; color: var(--muted); font-size: 13px; }
.table-panel { border: 1px solid var(--line); border-radius: 18px; padding: 16px; background: rgba(255,255,255,0.94); box-shadow: var(--shadow); }
</style>