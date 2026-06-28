/**
 * 前端路由配置。
 *
 * 路由分为两层：
 *  1. 公开路由（/login, /register 等）—— meta.public = true，无需登录即可访问。
 *  2. 布局路由（/）—— 包裹在 BasicLayout 中，按角色（roles）控制可见性。
 *
 * 路由守卫（beforeEach）负责：
 *  - 补全缺失的 systemRole（兼容旧 localStorage 数据）
 *  - 已登录用户访问 /login 时按角色跳转到默认页
 *  - 未登录用户强制跳转到 /login
 *  - 角色不满足时跳转到 403 页面
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { SystemRole } from '@/types/auth'

/** 路由元数据扩展 */
type RouteMeta = {
  public?: boolean      // 是否无需登录即可访问
  title?: string        // 页面标题（用于面包屑）
  group?: string        // 菜单分组标识
  roles?: SystemRole[]  // 允许访问的系统角色，空数组或不存在表示所有角色
}

// 预定义角色组合，避免处处重复字面量
const ADMIN_ROLES: SystemRole[] = ['super_admin', 'biz_admin']
const SUPER_ADMIN: SystemRole[] = ['super_admin']
const BIZ_ADMIN: SystemRole[] = ['biz_admin']

/** 全量路由定义 */
const routes: RouteRecordRaw[] = [
  // ==================== 公开路由（无需登录） ====================
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
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/login/ResetPassword.vue'),
    meta: { public: true, title: '重置密码' }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { public: true, title: '403 无权限' }
  },
  // ==================== 布局路由（需登录） ====================
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    // 根据角色重定向到各自的默认页
    redirect: () => {
      const authStore = useAuthStore()
      const role = authStore.user?.systemRole
      if (role === 'super_admin') return '/workbench'
      if (role === 'biz_admin') return '/my-processes'
      return '/process/start-preview'  // 普通用户默认页
    },
    children: [
      // ---- 概览 ----
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/workbench/Workbench.vue'),
        meta: { title: '工作台', group: '概览', roles: SUPER_ADMIN }
      },
      // ---- 流程设计 ----
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
        meta: { title: '我的流程', group: '流程', roles: ADMIN_ROLES }
      },
      // ---- 资源 ----
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
      // ---- AI ----
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
      // ---- 运行 ----
      {
        path: 'runtime-monitor',
        name: 'RuntimeMonitor',
        component: () => import('@/views/monitor/RuntimeMonitor.vue'),
        props: { scope: 'global' },
        meta: { title: '运行监控', group: '运行', roles: SUPER_ADMIN }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '仪表盘', group: '运行', roles: ADMIN_ROLES }
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
        meta: { title: '流程发起', group: '运行' }  // 所有角色均可访问
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
      // ---- 任务 ----
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
      // ---- 通知 ----
      {
        path: 'notifications',
        name: 'NotificationList',
        component: () => import('@/views/notification/NotificationList.vue'),
        meta: { title: '通知中心', group: '运行' }
      },
      // ---- 系统管理（仅超级管理员） ----
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
        path: 'admin/workflow-roles',
        name: 'WorkflowRoleAdmin',
        component: () => import('@/views/admin/WorkflowRoleAdmin.vue'),
        meta: { title: '流程角色管理', group: '系统', roles: SUPER_ADMIN }
      },
      // ---- 设置 ----
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
        path: 'settings/system',
        name: 'SystemSettings',
        component: () => import('@/views/settings/SystemSettings.vue'),
        meta: { title: '系统设置', group: '系统', roles: SUPER_ADMIN }
      },
      // ---- 占位页面（功能预告） ----
      {
        path: 'placeholder/:feature',
        name: 'Placeholder',
        component: () => import('@/views/placeholder/Placeholder.vue'),
        meta: { title: '功能预告' }
      },
      // ---- 404 兜底（布局内未匹配路径） ----
      {
        path: 'not-found',
        name: 'NotFound',
        component: () => import('@/views/placeholder/Placeholder.vue'),
        props: { feature: '404' },
        meta: { title: '页面未找到' }
      },
      {
        path: ':pathMatch(.*)*',
        name: 'LayoutCatchAll',
        redirect: (to) => {
          // 按角色兜底跳转到合适的默认页
          const authStore = useAuthStore()
          const role = authStore.user?.systemRole
          if (role === 'normal_user') return '/process/start-preview'
          if (role === 'biz_admin') return '/my-processes'
          if (role === 'super_admin') return '/workbench'
          return '/not-found'
        }
      }
    ]
  },
  // ---- 全局 404（未登录或未匹配任何路由，兜底跳转登录） ----
  {
    path: '/:pathMatch(.*)*',
    name: 'GlobalCatchAll',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),  // HTML5 History 模式，需要服务端配合 fallback
  routes,
  scrollBehavior: () => ({ top: 0 })  // 路由切换后滚动到顶部
})

/**
 * 检查用户角色是否有权访问某条路由。
 * @param userRole   当前用户角色
 * @param routeRoles 路由配置的允许角色列表（undefined/空数组表示公开）
 */
function hasRouteAccess(userRole: SystemRole | undefined, routeRoles?: SystemRole[]): boolean {
  if (!routeRoles || routeRoles.length === 0) return true  // 无角色限制
  return Boolean(userRole && routeRoles.includes(userRole))
}

/**
 * 全局前置路由守卫。
 *
 * 执行顺序：
 *  1. 尝试补全 systemRole（兼容旧 localStorage 数据）
 *  2. 已登录用户访问 /login → 按角色跳转默认页
 *  3. 公开路由直接放行
 *  4. 未登录 → /login
 *  5. 无角色权限 → /403
 */
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const meta = to.meta as RouteMeta

  // 确保 user 数据完整（localStorage 旧数据可能缺 systemRole）
  if (authStore.isLoggedIn && (!authStore.user || !authStore.user.systemRole)) {
    try {
      await authStore.fetchMe()
    } catch {
      // 补全失败说明 token 无效，退出并重定向
      authStore.logout()
      return to.path === '/login' ? true : '/login'
    }
  }

  // 已登录用户访问登录页 → 按角色跳转到默认页
  if (to.path === '/login' && authStore.isLoggedIn) {
    const role = authStore.user?.systemRole
    if (role === 'normal_user') return '/process/start-preview'
    if (role === 'biz_admin') return '/my-processes'
    return '/workbench'
  }

  // 公开路由无需登录
  if (meta.public) return true

  // 未登录 → 强制跳转登录页
  if (!authStore.isLoggedIn) return '/login'

  // 已登录但角色不满足路由要求的角色列表 → 403
  if (!hasRouteAccess(authStore.user?.systemRole, meta.roles)) {
    return '/403'
  }

  return true
})

export default router
