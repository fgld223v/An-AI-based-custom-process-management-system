<template>
  <div class="dashboard-container">
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <h1>基于AI的自定义流程管理系统</h1>
        </div>
        <div class="header-right">
          <span>欢迎, {{ userStore.userInfo?.username }}</span>
          <el-button type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>仪表盘</span>
            </div>
          </template>
          <el-alert
            :title="message"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 20px"
          />
          <p>这是一个基于 Vue 3 + Spring Boot 3 的 基于AI的自定义流程管理系统。</p>
          <p>系统已集成 Flowable 流程引擎和 Spring Security + JWT 认证。</p>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import request from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const message = ref('')

onMounted(async () => {
  try {
    const res: any = await request.get('/hello-auth')
    message.value = res.message
  } catch (error) {
    console.error(error)
  }
})

const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.dashboard-container {
  height: 100%;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #409eff;
  color: white;
}

.header-left h1 {
  margin: 0;
  font-size: 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.main {
  padding: 20px;
  background-color: #f5f7fa;
}

.card-header {
  font-weight: bold;
}
</style>
