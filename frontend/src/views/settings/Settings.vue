<template>
  <div class="settings-page">
    <section class="settings-card">
      <div>
        <el-tag type="success" effect="plain">权限基础</el-tag>
        <h1>设置/权限</h1>
        <p>当前登录角色会影响菜单和后续接口权限。第一阶段先展示基础账号信息。</p>
      </div>
      <div class="identity-panel">
        <el-avatar :size="56">{{ authStore.username.slice(0, 1) }}</el-avatar>
        <div>
          <strong>{{ authStore.username }}</strong>
          <span>{{ authStore.role }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>
