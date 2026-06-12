<template>
  <header class="topbar">
    <div class="topbar-left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>PROCESS OS</el-breadcrumb-item>
        <el-breadcrumb-item>{{ route.meta.group || '工作区' }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="flow-title" v-if="templateStore.currentTemplate">
        <span>{{ templateStore.currentFlowName }}</span>
        <el-tag size="small" effect="plain" type="success">编辑中</el-tag>
      </div>
    </div>

    <div class="topbar-actions">
      <el-button round :icon="MagicStick" @click="showComingSoon">AI助手</el-button>
      <el-button circle :icon="Bell" @click="showComingSoon" />
      <el-dropdown>
        <div class="user-chip">
          <el-avatar :size="32">{{ authStore.username.slice(0, 1) }}</el-avatar>
          <span>{{ authStore.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/settings')">账号设置</el-dropdown-item>
            <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell, MagicStick, Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTemplateStore } from '@/stores/template'
import { showComingSoon } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const templateStore = useTemplateStore()

const currentTitle = computed(() => {
  if (templateStore.currentTemplate) {
    return templateStore.currentTemplate.templateName
  }
  return (route.meta.title as string) || '工作台'
})

function logout() {
  authStore.logout()
  router.replace('/login')
}
</script>
