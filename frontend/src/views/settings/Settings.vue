<template>
  <div class="settings-page">
    <section class="settings-card">
      <div>
        <el-tag type="success" effect="plain">权限基础</el-tag>
        <h1>个人设置</h1>
        <p>当前登录角色会影响侧边栏菜单和后端接口权限。此页面展示当前账号的基础信息。</p>
      </div>
      <div class="identity-panel">
        <el-avatar :size="56">{{ authStore.username.slice(0, 1) }}</el-avatar>
        <div>
          <strong>{{ authStore.username }}</strong>
          <span>{{ roleLabel }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    ADMIN: '系统管理员',
    MANAGER: '业务管理员',
    USER: '普通用户'
  }
  return map[authStore.role] || authStore.role
})
</script>
