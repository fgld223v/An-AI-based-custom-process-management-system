<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-pill">AI Workflow Builder</div>
      <h1>AI Flow</h1>
      <p>面向流程自动化、表单采集与低代码编排的现代流程操作系统。</p>
      <div class="hero-grid">
        <div v-for="item in heroItems" :key="item.title" class="hero-card">
          <span>{{ item.value }}</span>
          <small>{{ item.title }}</small>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="panel-heading">
        <div class="brand-mark large">AF</div>
        <div>
          <h2>欢迎回来</h2>
          <p>登录 PROCESS OS 工作区</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="请输入密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-button class="login-button" type="success" size="large" :loading="loading" @click="submit">
          登录工作台
        </el-button>
      </el-form>

    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const heroItems = [
  { value: 'MVP', title: '第一阶段' },
  { value: 'BPMN', title: '流程建模' },
  { value: 'JSON', title: '表单配置' }
]

async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    // 登录前清除旧 token，避免 JWT 过滤器干扰
    authStore.logout()
    await authStore.login(form)
    ElMessage.success('登录成功')
    const role = authStore.user?.systemRole
    router.push(role === 'normal_user' ? '/process/start-preview' : '/workbench')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px;
  flex-wrap: wrap;
}
.login-hero { max-width: 380px; }
.hero-pill {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 20px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 16px;
}
.login-hero h1 { font-size: 42px; margin: 0 0 12px; font-weight: 800; }
.login-hero p { color: var(--muted); line-height: 1.7; margin-bottom: 28px; }
.hero-grid { display: flex; gap: 16px; }
.hero-card {
  padding: 16px 20px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--panel);
  text-align: center;
}
.hero-card span { display: block; font-size: 22px; font-weight: 700; }
.hero-card small { color: var(--muted); font-size: 12px; }

.login-panel {
  width: 380px;
  padding: 36px 32px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--panel);
  box-shadow: var(--shadow);
}
.panel-heading { display: flex; align-items: center; gap: 14px; margin-bottom: 28px; }
.brand-mark {
  width: 44px; height: 44px;
  border-radius: 12px;
  background: var(--el-color-success);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 17px;
}
.brand-mark.large { width: 52px; height: 52px; font-size: 20px; }
.panel-heading h2 { margin: 0; font-size: 22px; }
.panel-heading p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.login-button { width: 100%; margin-top: 8px; }
.login-footer { margin-top: 18px; text-align: center; }
.footer-link { color: var(--el-color-primary); cursor: pointer; font-size: 13px; }
.footer-link:hover { text-decoration: underline; }

@media (max-width: 860px) {
  .login-page { flex-direction: column; gap: 32px; }
  .login-hero { text-align: center; }
  .hero-grid { justify-content: center; }
}
</style>
