<template>
  <!-- 注册页面：左侧品牌介绍 + 右侧注册面板 -->
  <div class="login-page">
    <!-- 左侧品牌展示区域 -->
    <section class="login-hero">
      <div class="hero-pill">AI Workflow Builder</div>
      <h1>AI Flow</h1>
      <p>注册新账号，加入流程自动化工作区。</p>
    </section>

    <!-- 右侧注册表单面板 -->
    <section class="login-panel">
      <!-- 面板标题：品牌标识 + 页面标题及说明 -->
      <div class="panel-heading">
        <div class="brand-mark large">AF</div>
        <div>
          <h2>注册账号</h2>
          <p>创建一个新工作区账号</p>
        </div>
      </div>

      <!-- 注册表单：用户名、昵称、密码、确认密码 -->
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <!-- 用户名字段 -->
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <!-- 昵称字段（可选） -->
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（可选）" :prefix-icon="User" />
        </el-form-item>
        <!-- 密码字段 -->
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <!-- 确认密码字段，用于二次校验 -->
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" placeholder="确认密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <!-- 注册提交按钮 -->
        <el-button class="login-button" type="success" size="large" :loading="loading" @click="submit">
          注册
        </el-button>
      </el-form>

      <!-- 底部链接：跳转到登录页 -->
      <div class="login-footer">
        <span class="footer-link" @click="router.push('/login')">已有账号？返回登录</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import request from '@/api/request'

const router = useRouter()
/** 表单实例引用，用于触发表单校验 */
const formRef = ref<FormInstance>()
/** 注册按钮加载状态 */
const loading = ref(false)

/** 注册表单数据，使用 reactive 实现双向绑定 */
const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

/**
 * 自定义校验：确认密码是否与密码一致
 * @param _rule - 校验规则（未使用）
 * @param value - 当前输入值
 * @param callback - 校验结果回调
 */
const validateConfirm = (_rule: any, value: string, callback: Function) => {
  if (value !== form.password) { callback(new Error('两次输入的密码不一致')) }
  else { callback() }
}

/** 表单校验规则配置 */
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3-32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

/**
 * 提交注册表单
 * 先执行表单校验，通过后调用注册 API，成功则跳转到登录页
 */
async function submit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    // 调用后端注册接口
    await request.post('/api/auth/register', {
      username: form.username.trim(),
      nickname: form.nickname.trim() || undefined,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex; min-height: 100vh; align-items: center; justify-content: center;
  gap: 80px; padding: 40px; flex-wrap: wrap;
}
.login-hero { max-width: 380px; }
.hero-pill {
  display: inline-block; padding: 4px 14px; border-radius: 20px;
  background: var(--el-color-success-light-9); color: var(--el-color-success);
  font-size: 12px; font-weight: 700; margin-bottom: 16px;
}
.login-hero h1 { font-size: 42px; margin: 0 0 12px; font-weight: 800; }
.login-hero p { color: var(--muted); line-height: 1.7; }
.login-panel {
  width: 380px; padding: 36px 32px; border: 1px solid var(--line);
  border-radius: 24px; background: var(--panel); box-shadow: var(--shadow);
}
.panel-heading { display: flex; align-items: center; gap: 14px; margin-bottom: 28px; }
.brand-mark {
  width: 44px; height: 44px; border-radius: 12px; background: var(--el-color-success);
  color: #fff; display: flex; align-items: center; justify-content: center;
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
}
</style>
