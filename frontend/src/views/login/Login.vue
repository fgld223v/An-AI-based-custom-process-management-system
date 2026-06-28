<template>
  <!-- 登录页：左侧品牌介绍 + 右侧登录表单，双栏居中布局 -->
  <div class="login-page">
    <!-- 左侧：品牌介绍区域 -->
    <section class="login-hero">
      <div class="hero-pill">AI Workflow Builder</div>
      <h1>AI Flow</h1>
      <p>面向流程自动化、表单采集与低代码编排的现代流程管理系统。</p>
      <!-- 核心能力展示卡片 -->
      <div class="hero-grid">
        <div v-for="item in heroItems" :key="item.title" class="hero-card">
          <span>{{ item.value }}</span>
          <small>{{ item.title }}</small>
        </div>
      </div>
    </section>

    <!-- 右侧：登录表单面板 -->
    <section class="login-panel">
      <!-- 面板头部：品牌标识 + 欢迎标题 -->
      <div class="panel-heading">
        <div class="brand-mark large">AF</div>
        <div>
          <h2>欢迎回来</h2>
          <p>登录 PROCESS OS 工作区</p>
        </div>
      </div>

      <!-- 登录表单：用户名 + 密码 + 提交按钮 -->
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

      <!-- 注册入口 -->
      <div class="panel-footer">
        <span class="footer-text">还没有账号？</span>
        <el-button text type="success" size="default" @click="goRegister">
          立即注册
        </el-button>
      </div>

      <!-- 忘记密码入口 -->
      <div class="forgot-row">
        <el-button text type="primary" size="default" @click="goResetPassword">
          忘记密码？
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Login - 登录页面
 *
 * 收集用户名密码并调用认证接口，登录后根据用户角色（super_admin / biz_admin / 普通用户）
 * 跳转到不同的默认路由。
 */
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

/** 登录表单数据 */
const form = reactive({
  username: '',
  password: ''
})

/** 表单校验规则 */
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 左侧品牌区域展示的核心能力卡片数据 */
const heroItems = [
  { value: 'AI', title: '智能构建' },
  { value: 'BPMN', title: '流程建模' },
  { value: 'JSON', title: '表单配置' }
]

/** 提交登录：表单校验 -> 调用认证 -> 按角色路由 */
async function submit() {
  // 先触发表单校验
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    authStore.logout()  // 清除旧状态
    await authStore.login(form)
    ElMessage.success('登录成功')
    // 根据角色跳转不同首页
    const role = authStore.user?.systemRole
    if (role === 'super_admin') router.push('/workbench')
    else if (role === 'biz_admin') router.push('/my-processes')
    else router.push('/process/start-preview')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}

/** 跳转到注册页 */
function goRegister() {
  router.push('/register')
}

/** 跳转到密码重置页 */
function goResetPassword() {
  router.push('/reset-password')
}
</script>

<style scoped>
/* ---- 页面整体：双栏居中布局 ---- */
.login-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px;
  flex-wrap: wrap;
}

/* ---- 左侧品牌介绍区域 ---- */
.login-hero {
  max-width: 380px;
}

/* 顶部小标签 */
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

.login-hero h1 {
  font-size: 42px;
  margin: 0 0 12px;
  font-weight: 800;
}

.login-hero p {
  color: var(--muted);
  line-height: 1.7;
  margin-bottom: 28px;
}

/* 核心能力展示卡片行 */
.hero-grid {
  display: flex;
  gap: 16px;
}

.hero-card {
  padding: 16px 20px;
  border-radius: 14px;
  border: 1px solid var(--line);
  background: var(--panel);
  text-align: center;
}

.hero-card span {
  display: block;
  font-size: 22px;
  font-weight: 700;
}

.hero-card small {
  color: var(--muted);
  font-size: 12px;
}

/* ---- 右侧登录面板 ---- */
.login-panel {
  width: 380px;
  padding: 36px 32px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

/* 面板头部品牌标识与文字 */
.panel-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--el-color-success);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 17px;
}

.brand-mark.large {
  width: 52px;
  height: 52px;
  font-size: 20px;
}

.panel-heading h2 {
  margin: 0;
  font-size: 22px;
}

.panel-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

/* 登录按钮全宽 */
.login-button {
  width: 100%;
  margin-top: 8px;
}

/* 注册入口 */
.panel-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 18px;
  gap: 4px;
}

.footer-text {
  color: var(--muted);
  font-size: 13px;
}

/* 忘记密码入口 */
.forgot-row {
  text-align: center;
  margin-top: 8px;
}

/* ---- 响应式：窄屏幕时上下堆叠 ---- */
@media (max-width: 860px) {
  .login-page {
    flex-direction: column;
    gap: 32px;
  }

  .login-hero {
    text-align: center;
  }

  .hero-grid {
    justify-content: center;
  }
}
</style>
