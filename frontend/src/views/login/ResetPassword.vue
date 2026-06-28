<template>
  <!-- 重置密码页面：左侧品牌介绍 + 右侧重置表单面板 -->
  <div class="login-page">
    <!-- 左侧品牌展示区域 -->
    <section class="login-hero">
      <div class="hero-pill">AI Workflow Builder</div>
      <h1>AI Flow</h1>
      <p>重置您的账号密码，重新获得工作区访问权限。</p>
    </section>

    <!-- 右侧重置密码表单面板 -->
    <section class="login-panel">
      <!-- 面板标题区：品牌标识 + 页面描述 -->
      <div class="panel-heading">
        <div class="brand-mark large">AF</div>
        <div>
          <h2>重置密码</h2>
          <p>输入用户名并设置新密码</p>
        </div>
      </div>

      <!-- 重置密码表单 -->
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <!-- 用户名字段 -->
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <!-- 验证方式选择：手机号或邮箱验证 -->
        <el-form-item>
          <el-radio-group v-model="form.verifyType" @change="form.verifyValue = ''">
            <el-radio-button value="phone">手机号验证</el-radio-button>
            <el-radio-button value="email">邮箱验证</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- 验证值输入（手机号或邮箱地址） -->
        <el-form-item prop="verifyValue">
          <el-input v-model="form.verifyValue" :placeholder="form.verifyType==='email' ? '请输入绑定邮箱' : '请输入绑定手机号'" :prefix-icon="Message" />
        </el-form-item>
        <!-- 新密码字段 -->
        <el-form-item prop="newPassword">
          <el-input v-model="form.newPassword" placeholder="请输入新密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <!-- 确认新密码字段 -->
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" placeholder="请确认新密码" type="password" show-password :prefix-icon="Lock" />
        </el-form-item>
        <!-- 提交重置密码按钮 -->
        <el-button class="login-button" type="success" size="large" :loading="loading" @click="submit">
          重置密码
        </el-button>
      </el-form>

      <!-- 底部链接：返回登录页 -->
      <div class="login-footer">
        <span class="footer-link" @click="router.push('/login')">想起密码了？返回登录</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'
import { resetPasswordApi } from '@/api/auth'

const router = useRouter()
/** 表单实例引用，用于触发表单校验 */
const formRef = ref<FormInstance>()
/** 重置密码按钮加载状态 */
const loading = ref(false)

/** 重置密码表单数据，验证方式支持手机号或邮箱 */
const form = reactive({
  username: '',
  verifyType: 'phone' as 'phone' | 'email',
  verifyValue: '',
  newPassword: '',
  confirmPassword: ''
})

/**
 * 自定义校验：确认密码是否与新密码一致
 * @param _rule - 校验规则（未使用）
 * @param value - 当前输入值
 * @param callback - 校验结果回调
 */
const validateConfirm = (_rule: any, value: string, callback: Function) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

/** 表单校验规则配置 */
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  verifyValue: [
    { required: true, message: '请输入绑定的手机号或邮箱', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

/**
 * 提交重置密码请求
 * 先执行表单校验，通过后调用重置密码 API，成功则跳转到登录页
 */
async function submit() {
  // 先执行表单校验，校验不通过则直接返回
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await resetPasswordApi({
      username: form.username.trim(),
      verifyType: form.verifyType,
      verifyValue: form.verifyValue.trim(),
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '密码重置失败')
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

.login-hero {
  max-width: 380px;
}

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
}

.login-panel {
  width: 380px;
  padding: 36px 32px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

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

.login-button {
  width: 100%;
  margin-top: 8px;
}

.login-footer {
  margin-top: 18px;
  text-align: center;
}

.footer-link {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 13px;
}

.footer-link:hover {
  text-decoration: underline;
}

@media (max-width: 860px) {
  .login-page {
    flex-direction: column;
    gap: 32px;
  }

  .login-hero {
    text-align: center;
  }
}
</style>
