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
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" placeholder="密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-button class="login-button" type="success" size="large" :loading="loading" @click="submit">
          登录工作台
        </el-button>
      </el-form>

      <div class="login-hint">
        <span>默认账号</span>
        <strong>admin / admin123</strong>
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
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123'
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
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.push('/workbench')
  } finally {
    loading.value = false
  }
}
</script>
