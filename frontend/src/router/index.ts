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
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '仪表盘', group: '流程' }
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
        path: 'process/start-preview',
        name: 'StartPreview',
        component: () => import('@/views/process/StartPreview.vue'),
        meta: { title: '流程发起预览', group: '运行' }
      },
      {
        path: 'process/instances',
        name: 'ProcessInstanceList',
        component: () => import('@/views/process/InstanceList.vue'),
        meta: { title: '流程实例', group: '运行' }
      },
      {
        path: 'process/instances/:id',
        name: 'ProcessInstanceDetail',
        component: () => import('@/views/process/InstanceDetail.vue'),
        meta: { title: '流程实例详情', group: '运行' }
      },
      {
        path: 'tasks/todo',
        name: 'TaskTodoList',
        component: () => import('@/views/task/TaskTodoList.vue'),
        meta: { title: '我的待办', group: '运行' }
      },
      {
        path: 'tasks/done',
        name: 'TaskDoneList',
        component: () => import('@/views/task/TaskDoneList.vue'),
        meta: { title: '我的已办', group: '运行' }
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskDetail',
        component: () => import('@/views/task/TaskDetail.vue'),
        meta: { title: '任务详情', group: '运行' }
      },
      {
        path: 'notifications',
        name: 'NotificationList',
        component: () => import('@/views/notification/NotificationList.vue'),
        meta: { title: '通知中心', group: '运行' }
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
        path: 'settings/automation',
        name: 'AutomationSettings',
        component: () => import('@/views/settings/AutomationSettings.vue'),
        meta: { title: '自动化策略', group: '系统' }
      },
      {
        path: 'placeholder/:feature',
        name: 'Placeholder',
        component: () => import('@/views/placeholder/Placeholder.vue'),
        meta: { title: '功能预告' }
      },
      {
        path: 'ai/generate-process',
        name: 'AiGenerateProcess',
        component: () => import('@/views/ai/AiGenerateProcess.vue'),
        meta: { title: 'AI智能生成流程', group: '流程' }
      },
      {
        path: 'ai/generate-form',
        name: 'AiGenerateForm',
        component: () => import('@/views/ai/AiGenerateForm.vue'),
        meta: { title: 'AI智能生成表单', group: '流程' }
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
