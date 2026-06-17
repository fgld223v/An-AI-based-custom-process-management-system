<template>
  <div class="forbidden-page">
    <div class="forbidden-card">
      <div class="forbidden-icon">🔒</div>
      <h1>403</h1>
      <h2>访问被拒绝</h2>
      <p>您没有权限访问此页面，请联系管理员开通权限。</p>
      <div class="forbidden-actions">
        <el-button type="primary" round @click="router.push('/workbench')">返回工作台</el-button>
        <el-button round @click="router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
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
.forbidden-icon { font-size: 64px; margin-bottom: 16px; }
.forbidden-card h1 { font-size: 72px; margin: 0; color: var(--el-color-danger); font-weight: 800; }
.forbidden-card h2 { font-size: 24px; margin: 12px 0; color: var(--text); }
.forbidden-card p { color: var(--muted); margin: 8px 0 28px; font-size: 14px; }
.forbidden-actions { display: flex; gap: 12px; justify-content: center; }
</style>
