import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { SystemRole } from '@/types/auth'

type RouteMeta = {
  public?: boolean
  title?: string
  group?: string
  roles?: SystemRole[]
}

const ADMIN_ROLES: SystemRole[] = ['super_admin', 'biz_admin']
const SUPER_ADMIN: SystemRole[] = ['super_admin']
const BIZ_ADMIN: SystemRole[] = ['biz_admin']

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
    redirect: () => {
      const authStore = useAuthStore()
      return authStore.user?.systemRole === 'normal_user' ? '/process/start-preview' : '/workbench'
    },
    children: [
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/workbench/Workbench.vue'),
        meta: { title: '工作台', group: '概览', roles: ADMIN_ROLES }
      },
      {
        path: 'form-designer',
        name: 'FormDesigner',
        component: () => import('@/views/form-designer/FormDesigner.vue'),
        meta: { title: '表单设计器', group: '流程', roles: ADMIN_ROLES }
      },
      {
        path: 'process-designer',
        name: 'ProcessDesigner',
        component: () => import('@/views/process-designer/ProcessDesigner.vue'),
        meta: { title: '流程编辑器', group: '流程', roles: ADMIN_ROLES }
      },
      {
        path: 'my-processes',
        name: 'MyProcessList',
        component: () => import('@/views/process/MyProcessList.vue'),
        meta: { title: '我的流程', group: '流程', roles: BIZ_ADMIN }
      },
      {
        path: 'templates',
        name: 'TemplateList',
        component: () => import('@/views/template/TemplateList.vue'),
        meta: { title: '流程模板管理', group: '资源', roles: SUPER_ADMIN }
      },
      {
        path: 'template-market',
        name: 'TemplateMarket',
        component: () => import('@/views/template/TemplateMarket.vue'),
        meta: { title: '模板市场', group: '资源', roles: ADMIN_ROLES }
      },
      {
        path: 'ai/generate-process',
        name: 'AiGenerateProcess',
        component: () => import('@/views/ai/AiGenerateProcess.vue'),
        meta: { title: 'AI 生成流程', group: 'AI', roles: ADMIN_ROLES }
      },
      {
        path: 'ai/generate-form',
        name: 'AiGenerateForm',
        component: () => import('@/views/ai/AiGenerateForm.vue'),
        meta: { title: 'AI 生成表单', group: 'AI', roles: ADMIN_ROLES }
      },
      {
        path: 'ai/optimize',
        name: 'AiOptimize',
        component: () => import('@/views/ai/AiOptimize.vue'),
        meta: { title: 'AI 流程优化', group: 'AI', roles: ADMIN_ROLES }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/monitor/RuntimeMonitor.vue'),
        props: { scope: 'global' },
        meta: { title: '运行监控', group: '运行', roles: SUPER_ADMIN }
      },
      {
        path: 'business-monitor',
        name: 'BusinessMonitor',
        component: () => import('@/views/monitor/RuntimeMonitor.vue'),
        props: { scope: 'business' },
        meta: { title: '业务监控', group: '运行', roles: BIZ_ADMIN }
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
        meta: { title: '我的申请', group: '运行' }
      },
      {
        path: 'process/instances/:id',
        name: 'ProcessInstanceDetail',
        component: () => import('@/views/process/InstanceDetail.vue'),
        meta: { title: '我的申请详情', group: '运行' }
      },
      {
        path: 'tasks/todo',
        name: 'TaskTodoList',
        component: () => import('@/views/task/TaskTodoList.vue'),
        meta: { title: '待办任务', group: '运行' }
      },
      {
        path: 'tasks/done',
        name: 'TaskDoneList',
        component: () => import('@/views/task/TaskDoneList.vue'),
        meta: { title: '已办任务', group: '运行' }
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
        path: 'admin/users',
        name: 'UserAdmin',
        component: () => import('@/views/admin/UserAdmin.vue'),
        meta: { title: '用户管理', group: '系统', roles: SUPER_ADMIN }
      },
      {
        path: 'admin/departments',
        name: 'DepartmentAdmin',
        component: () => import('@/views/admin/DepartmentAdmin.vue'),
        meta: { title: '部门管理', group: '系统', roles: SUPER_ADMIN }
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
        meta: { title: '自动化策略', group: '系统', roles: ADMIN_ROLES }
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

function hasRouteAccess(userRole: SystemRole | undefined, routeRoles?: SystemRole[]): boolean {
  if (!routeRoles || routeRoles.length === 0) return true
  return Boolean(userRole && routeRoles.includes(userRole))
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const meta = to.meta as RouteMeta

  // 确保 user 数据完整（localStorage 旧数据可能缺 systemRole）
  if (authStore.isLoggedIn && (!authStore.user || !authStore.user.systemRole)) {
    try {
      await authStore.fetchMe()
    } catch {
      authStore.logout()
      return to.path === '/login' ? true : '/login'
    }
  }

  if (to.path === '/login' && authStore.isLoggedIn) {
    return authStore.user?.systemRole === 'normal_user' ? '/process/start-preview' : '/workbench'
  }

  if (meta.public) return true

  if (!authStore.isLoggedIn) return '/login'

  if (!hasRouteAccess(authStore.user?.systemRole, meta.roles)) {
    return '/403'
  }

  return true
})

export default router
