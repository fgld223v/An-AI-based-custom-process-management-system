<template>
  <!-- 个人设置页面 -->
  <div class="settings-page">
    <!-- 个人信息卡片：头像 + 姓名 + 角色标签 -->
    <section class="profile-card">
      <div class="profile-cover"></div>
      <div class="profile-body">
        <!-- 用户头像，取昵称或用户名的首字 -->
        <el-avatar :size="72" class="profile-avatar">{{ displayName.slice(0, 1) }}</el-avatar>
        <div class="profile-info">
          <h2>{{ displayName }}</h2>
          <p class="profile-username">@{{ authStore.user?.username }}</p>
          <!-- 角色和部门标签 -->
          <div class="profile-tags">
            <el-tag :type="roleTagType" effect="dark" size="small">{{ roleLabel }}</el-tag>
            <el-tag v-if="currentDeptName" type="success" effect="plain" size="small">{{ currentDeptName }}</el-tag>
          </div>
        </div>
      </div>
    </section>

    <!-- 组织信息表单卡片：部门 & 上级设置 -->
    <section class="form-card">
      <div class="form-card-head">
        <h3>组织信息</h3>
        <p>设置所属部门与直属上级，用于审批链路由。</p>
      </div>

      <!-- 保存结果提示 -->
      <el-alert
        v-if="saveMsg"
        :title="saveMsg"
        :type="saveError ? 'error' : 'success'"
        show-icon
        :closable="false"
        class="form-alert"
      />

      <el-form label-position="top" class="settings-form" :disabled="saving">
        <div class="form-row">
          <!-- 所属部门选择 -->
          <el-form-item label="所属部门">
            <el-select
              v-model="form.departmentId"
              placeholder="请选择部门"
              clearable
              filterable
              :loading="deptLoading"
            >
              <el-option
                v-for="d in departments"
                :key="d.id"
                :label="d.deptName"
                :value="d.id"
              />
            </el-select>
          </el-form-item>

          <!-- 直属上级选择（可选） -->
          <el-form-item label="直属上级">
            <el-select
              v-model="form.supervisorId"
              placeholder="请选择（可选）"
              clearable
              filterable
              :loading="userLoading"
            >
              <el-option
                v-for="u in userList"
                :key="u.id"
                :label="`${u.nickname || u.username}`"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <!-- 保存/刷新按钮 -->
        <div class="form-actions">
          <el-button type="success" :loading="saving" :icon="Check" round @click="saveProfile">
            {{ saving ? '保存中...' : '保存修改' }}
          </el-button>
          <el-button round :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
        </div>
      </el-form>
    </section>

    <!-- 联系信息表单卡片：手机号 + 邮箱 -->
    <section class="form-card">
      <div class="form-card-head">
        <h3>联系信息</h3>
        <p>填写个人电话与邮箱，用于密码重置等身份验证场景。</p>
      </div>

      <el-form label-position="top" class="settings-form" :disabled="saving">
        <div class="form-row">
          <!-- 手机号码 -->
          <el-form-item label="手机号码">
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="32" clearable />
          </el-form-item>
          <!-- 电子邮箱 -->
          <el-form-item label="电子邮箱">
            <el-input v-model="form.email" placeholder="请输入邮箱地址" maxlength="128" clearable />
          </el-form-item>
        </div>
      </el-form>
    </section>

    <!-- 账号详情只读卡片：ID、用户名、昵称、系统角色等 -->
    <section class="form-card">
      <div class="form-card-head">
        <h3>账号详情</h3>
        <p>以下信息由系统管理员维护，如需修改请联系管理员。</p>
      </div>
      <div class="info-grid">
        <div class="info-item">
          <span class="info-label">用户 ID</span>
          <span class="info-value">{{ authStore.user?.id ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">用户名</span>
          <span class="info-value">{{ authStore.user?.username ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">昵称</span>
          <span class="info-value">{{ authStore.user?.nickname ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">系统角色</span>
          <span class="info-value">{{ roleLabel }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">业务角色</span>
          <span class="info-value">{{ authStore.user?.role ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">管理部门</span>
          <span class="info-value">{{ authStore.user?.managedBizTypeIds || '全部' }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getDepartments, getUserList, updateUser, type DepartmentItem, type UserBrief } from '@/api/admin'

const authStore = useAuthStore()
/** 页面数据加载状态 */
const loading = ref(false)
/** 部门下拉加载状态 */
const deptLoading = ref(false)
/** 用户下拉加载状态 */
const userLoading = ref(false)
/** 保存按钮加载状态 */
const saving = ref(false)
/** 保存结果消息 */
const saveMsg = ref('')
/** 保存是否出错 */
const saveError = ref(false)
/** 部门列表 */
const departments = ref<DepartmentItem[]>([])
/** 用户列表（用于上级选择） */
const userList = ref<UserBrief[]>([])

/** 用户可编辑的表单数据 */
const form = reactive({
  phone: '' as string,
  email: '' as string,
  departmentId: null as number | null,
  supervisorId: null as number | null
})

/** 显示名称：优先昵称，其次用户名 */
const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '用户')

/** 当前所属部门名称 */
const currentDeptName = computed(() => {
  if (!authStore.user?.departmentId) return ''
  const d = departments.value.find(item => item.id === authStore.user?.departmentId)
  return d?.deptName || ''
})

/** 系统角色转中文标签 */
const roleLabel = computed(() => {
  const map: Record<string, string> = {
    super_admin: '超级管理员',
    biz_admin: '业务管理员',
    normal_user: '普通用户'
  }
  return map[authStore.user?.systemRole || ''] || authStore.user?.role || '-'
})

/** 角色对应的 Element UI Tag 类型 */
const roleTagType = computed(() => {
  const map: Record<string, string> = {
    super_admin: 'danger',
    biz_admin: 'warning',
    normal_user: 'info'
  }
  return map[authStore.user?.systemRole || ''] || 'info'
})

/** 页面挂载时加载用户数据 */
onMounted(() => loadData())

/** 加载用户信息和下拉选项数据 */
async function loadData() {
  loading.value = true
  saveMsg.value = ''
  try {
    // 从服务器获取最新用户信息
    await authStore.fetchMe()
    form.phone = authStore.user?.phone || ''
    form.email = authStore.user?.email || ''
    form.departmentId = authStore.user?.departmentId ?? null
    form.supervisorId = authStore.user?.supervisorId ?? null
    await Promise.all([loadDepartments(), loadUserList()])
  } finally {
    loading.value = false
  }
}

/** 加载部门下拉选项 */
async function loadDepartments() {
  deptLoading.value = true
  try {
    departments.value = await getDepartments()
  } catch { /* ignore */ }
  finally { deptLoading.value = false }
}

/** 加载用户下拉选项（用于直属上级选择） */
async function loadUserList() {
  userLoading.value = true
  try {
    userList.value = await getUserList() || []
  } catch { /* ignore */ }
  finally { userLoading.value = false }
}

/** 保存个人信息（部门、上级、电话、邮箱） */
async function saveProfile() {
  if (!authStore.user?.id) {
    ElMessage.warning('用户信息未加载，请刷新后重试')
    return
  }
  saving.value = true
  saveMsg.value = ''
  saveError.value = false
  try {
    await updateUser(authStore.user.id, {
      phone: form.phone || undefined,
      email: form.email || undefined,
      departmentId: form.departmentId,
      supervisorId: form.supervisorId
    })
    // 重新从数据库加载最新数据，确保页面状态与后端一致
    await authStore.fetchMe()
    saveError.value = false
    saveMsg.value = '个人信息已更新'
    ElMessage.success('个人信息已更新')
  } catch (e: any) {
    saveError.value = true
    saveMsg.value = e?.message || '保存失败，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 20px; /* 左右卡片间距 */
  align-items: start;
}

.profile-card {
  grid-column: 1 / -1;
}

/* ---- 个人信息卡片 ---- */
.profile-card {
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.profile-cover {
  height: 60px;
  background: #fff;
}

.profile-body {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 28px 28px;
  margin-top: -36px;
}

.profile-avatar {
  border: 4px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  background: linear-gradient(135deg, #10B981, #059669);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
}

.profile-info h2 {
  margin: 0 0 2px;
  font-size: 22px;
  font-weight: 700;
}

.profile-username {
  margin: 0 0 8px;
  color: #94a3b8;
  font-size: 14px;
}

.profile-tags {
  display: flex;
  gap: 8px;
}

/* ---- 表单卡片 ---- */
.form-card {
  padding: 28px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.form-card-head {
  margin-bottom: 20px;
}

.form-card-head h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
}

.form-card-head p {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
}

.form-alert {
  margin-bottom: 18px;
}

.settings-form :deep(.el-select) {
  width: 100%;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
}

.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}

/* ---- 账号详情只读 ---- */
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.info-item {
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
}

.info-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.info-value {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

@media (max-width: 640px) {
  .form-row { grid-template-columns: 1fr; }
  .info-grid { grid-template-columns: 1fr 1fr; }
}
</style>
