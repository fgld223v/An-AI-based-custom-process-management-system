<template>
  <div class="forbidden-page">
    <div class="forbidden-card">
      <el-icon class="forbidden-icon"><Lock /></el-icon>
      <h1>403</h1>
      <h2>访问被拒绝</h2>
      <p>当前账号没有访问此页面的权限，请联系管理员开通权限。</p>
      <div class="forbidden-actions">
        <el-button type="primary" round @click="goHome">返回首页</el-button>
        <el-button round @click="router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function goHome() {
  router.push(authStore.user?.systemRole === 'normal_user' ? '/process/start-preview' : '/workbench')
}
</script>

<style scoped>
.forbidden-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.forbidden-card {
  text-align: center;
  padding: 48px;
}

.forbidden-icon {
  margin-bottom: 16px;
  font-size: 64px;
  color: var(--el-color-warning);
}

.forbidden-card h1 {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 72px;
  font-weight: 800;
}

.forbidden-card h2 {
  margin: 12px 0;
  color: var(--text);
  font-size: 24px;
}

.forbidden-card p {
  margin: 8px 0 28px;
  color: var(--muted);
  font-size: 14px;
}

.forbidden-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
