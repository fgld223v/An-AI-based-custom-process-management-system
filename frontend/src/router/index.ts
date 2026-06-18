import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { SystemRole } from '@/types/auth'

// 角色-路由可见性：
//   super_admin → 全部
//   biz_admin   → 流程/表单/模板/实例/任务（不含系统管理）
//   normal_user → 流程发起/我的待办/已办/通知
type RouteMeta = {
  public?: boolean
  title?: string
  group?: string
  roles?: SystemRole[]   // 允许访问的角色列表，不填 = 所有已登录角色
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { public: true, title: '403 无权限' }
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
        meta: { title: '工作台', group: '概览' }
      },
      // ---- 流程设计（super_admin / biz_admin）----
      {
        path: 'form-designer',
        name: 'FormDesigner',
        component: () => import('@/views/form-designer/FormDesigner.vue'),
        meta: { title: '表单设计器', group: '流程', roles: ['super_admin', 'biz_admin'] }
      },
      {
        path: 'process-designer',
        name: 'ProcessDesigner',
        component: () => import('@/views/process-designer/ProcessDesigner.vue'),
        meta: { title: '流程编辑器', group: '流程', roles: ['super_admin', 'biz_admin'] }
      },
      {
        path: 'templates',
        name: 'TemplateList',
        component: () => import('@/views/template/TemplateList.vue'),
        meta: { title: '流程模板管理', group: '资源', roles: ['super_admin', 'biz_admin'] }
      },
      {
        path: 'template-market',
        name: 'TemplateMarket',
        component: () => import('@/views/template/TemplateMarket.vue'),
        meta: { title: '模板市场', group: '资源', roles: ['super_admin', 'biz_admin'] }
      },
      // ---- AI 功能（super_admin / biz_admin）----
      {
        path: 'ai/generate-process',
        name: 'AiGenerateProcess',
        component: () => import('@/views/ai/AiGenerateProcess.vue'),
        meta: { title: 'AI智能生成流程', group: '流程', roles: ['super_admin', 'biz_admin'] }
      },
      {
        path: 'ai/generate-form',
        name: 'AiGenerateForm',
        component: () => import('@/views/ai/AiGenerateForm.vue'),
        meta: { title: 'AI智能生成表单', group: '流程', roles: ['super_admin', 'biz_admin'] }
      },
      // ---- 流程运行（所有角色）----
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '运行监控', group: '运行' }
      },
      {
        path: 'process/start-preview',
        name: 'StartPreview',
        component: () => import('@/views/process/StartPreview.vue'),
        meta: { title: '流程发起', group: '运行' }
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
      // ---- 系统管理（仅 super_admin）----
      {
        path: 'admin/users',
        name: 'UserAdmin',
        component: () => import('@/views/admin/UserAdmin.vue'),
        meta: { title: '用户管理', group: '系统', roles: ['super_admin'] }
      },
      {
        path: 'admin/departments',
        name: 'DepartmentAdmin',
        component: () => import('@/views/admin/DepartmentAdmin.vue'),
        meta: { title: '部门管理', group: '系统', roles: ['super_admin'] }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/Settings.vue'),
        meta: { title: '个人设置', group: '系统' }
      },
      {
        path: 'settings/automation',
        name: 'AutomationSettings',
        component: () => import('@/views/settings/AutomationSettings.vue'),
        meta: { title: '自动化策略', group: '系统', roles: ['super_admin', 'biz_admin'] }
      },
      // ---- 占位页面 ----
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

/** 检查用户角色是否有权访问路由 */
function hasRouteAccess(userRole: SystemRole | undefined, routeRoles?: SystemRole[]): boolean {
  if (!routeRoles || routeRoles.length === 0) return true // 不限制角色
  if (!userRole) return false
  return routeRoles.includes(userRole)
}

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const meta = to.meta as RouteMeta

  // 公开页面直接放行
  if (meta.public) return true

  // 未登录 → 登录页
  if (!authStore.isLoggedIn) return '/login'

  // 登录后访问登录页 → 按角色跳转
  if (to.path === '/login') {
    const role = authStore.user?.systemRole
    if (role === 'normal_user') return '/process/start-preview'
    return '/workbench'
  }

  // 角色权限检查
  const userRole = authStore.user?.systemRole
  if (!hasRouteAccess(userRole, meta.roles)) {
    return '/403'
  }

  return true
})

export default router
