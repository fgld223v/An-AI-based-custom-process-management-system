import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    redirect: '/workbench',
    children: [
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/workbench/Workbench.vue'),
        meta: { title: '工作台', group: '流程' }
      },
      {
        path: 'form-designer',
        name: 'FormDesigner',
        component: () => import('@/views/form-designer/FormDesigner.vue'),
        meta: { title: '表单设计器', group: '流程' }
      },
      {
        path: 'process-designer',
        name: 'ProcessDesigner',
        component: () => import('@/views/process-designer/ProcessDesigner.vue'),
        meta: { title: '流程编辑器', group: '流程' }
      },
      {
        path: 'templates',
        name: 'TemplateList',
        component: () => import('@/views/template/TemplateList.vue'),
        meta: { title: '流程模板管理', group: '资源' }
      },
      {
        path: 'template-market',
        name: 'TemplateMarket',
        component: () => import('@/views/template/TemplateMarket.vue'),
        meta: { title: '模板市场', group: '资源' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/Settings.vue'),
        meta: { title: '设置/权限', group: '系统' }
      },
      {
        path: 'placeholder/:feature',
        name: 'Placeholder',
        component: () => import('@/views/placeholder/Placeholder.vue'),
        meta: { title: '功能预告' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (!to.meta.public && !authStore.isLoggedIn) {
    return '/login'
  }
  if (to.path === '/login' && authStore.isLoggedIn) {
    return '/workbench'
  }
  return true
})

export default router
