<template>
  <div class="placeholder-surface">
    <div class="placeholder-card">
      <el-tag v-if="is404" type="danger" effect="plain">404</el-tag>
      <el-tag v-else type="info" effect="plain">Coming Soon</el-tag>
      <h1>{{ is404 ? '页面未找到' : '该功能暂未开发' }}</h1>
      <p>{{ is404 ? '请检查 URL 是否正确，或返回首页。' : '后续版本开放。当前 MVP 优先完成登录、工作台、表单设计、流程设计与模板管理。' }}</p>
      <el-button round type="success" @click="goHome">{{ is404 ? '返回首页' : '返回工作台' }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ feature?: string }>()
const router = useRouter()
const authStore = useAuthStore()

const is404 = computed(() => props.feature === '404')

function goHome() {
  const role = authStore.user?.systemRole
  if (role === 'normal_user') {
    router.push('/process/start-preview')
  } else {
    router.push('/workbench')
  }
}
</script>
