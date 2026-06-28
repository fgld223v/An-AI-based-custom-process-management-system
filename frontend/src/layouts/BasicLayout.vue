<!--
  基础布局组件。

  三层结构：
    1. SidebarNav   — 左侧固定侧边导航栏
    2. TopBar       — 顶部面包屑 + 操作按钮栏
    3. RouterView   — 右侧下方内容区域（子路由渲染目标）

  该布局包裹所有需登录后访问的页面（路由 "/" 及其子路由）。
-->
<template>
  <div class="app-shell">
    <!-- 左侧导航 -->
    <SidebarNav />
    <!-- 右侧主区域 -->
    <main class="main-shell">
      <!-- 顶栏 -->
      <TopBar />
      <!-- 页面内容区 -->
      <section class="page-shell">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import SidebarNav from '@/components/layout/SidebarNav.vue'
import TopBar from '@/components/layout/TopBar.vue'
</script>
