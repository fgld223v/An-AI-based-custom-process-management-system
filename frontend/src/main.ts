/**
 * 应用入口 —— 挂载 Vue、状态管理与路由。
 *
 * 插件链：
 *  1. Pinia         全局状态管理（stores/）
 *  2. Vue Router    前端路由（router/）
 *  3. Element Plus  UI 组件库
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css' // Element Plus 基础样式
import './styles/index.scss'          // 项目全局样式覆盖
import App from './App.vue'
import router from './router'

// 创建 Vue 应用实例，按序挂载插件，最终挂载到 #app 节点
createApp(App)
  .use(createPinia())   // 注册 Pinia
  .use(router)          // 注册路由
  .use(ElementPlus)     // 注册 Element Plus
  .mount('#app')
