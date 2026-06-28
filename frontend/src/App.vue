<!--
  应用根组件。
  - RouterView：根据当前路由动态渲染页面内容
  - AiChatPanel：全局 AI 对话浮层，在所有页面可用
-->
<template>
  <RouterView />
  <AiChatPanel />
</template>

<script setup lang="ts">
import AiChatPanel from '@/components/ai/AiChatPanel.vue'
</script>
