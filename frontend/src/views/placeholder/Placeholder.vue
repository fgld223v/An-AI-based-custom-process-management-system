<template>
  <div class="placeholder-surface">
    <div class="placeholder-card">
      <el-tag type="info" effect="plain">Coming Soon</el-tag>
      <h1>该功能暂未开发</h1>
      <p>后续版本开放。当前 MVP 优先完成登录、工作台、表单设计、流程设计与模板管理。</p>
      <el-button round type="success" @click="router.push('/workbench')">返回工作台</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
