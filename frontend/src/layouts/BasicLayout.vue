<template>
  <div class="app-shell">
    <SidebarNav />
    <main class="main-shell">
      <TopBar />
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
