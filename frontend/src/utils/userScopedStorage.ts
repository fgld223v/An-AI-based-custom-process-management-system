/**
 * 用户作用域的 sessionStorage 工具。
 *
 * 每个用户拥有独立的 sessionStorage 命名空间：
 * 键格式：ai-flow:user-session:<用户标识>:<key>
 *
 * 作用：
 *  - 避免多用户登录同一浏览器时 session 数据互相覆盖
 *  - 同时提供便捷的 get/set/remove 方法
 *
 * 用户标识解析优先级：id > username > 'anonymous'
 */
import type { UserInfo } from '@/types/auth'

/** 键前缀常量 */
const PREFIX = 'ai-flow:user-session'

/**
 * 解析当前用户的作用域标识。
 * 优先级：用户 ID > 用户名 > 匿名
 */
function resolveUserScope(user?: UserInfo | null) {
  if (user?.id !== undefined && user.id !== null) {
    return String(user.id)
  }
  if (user?.username) {
    return user.username
  }
  return 'anonymous'
}

/**
 * 生成用户作用域下的完整 sessionStorage 键名。
 *
 * @param key  业务键名
 * @param user 当前用户对象（可选）
 * @returns "ai-flow:user-session:<用户标识>:<key>"
 */
export function userScopedSessionKey(key: string, user?: UserInfo | null) {
  return `${PREFIX}:${resolveUserScope(user)}:${key}`
}

/** 读取用户作用域下的 sessionStorage 值 */
export function getUserSessionItem(key: string, user?: UserInfo | null) {
  return window.sessionStorage.getItem(userScopedSessionKey(key, user))
}

/** 设置用户作用域下的 sessionStorage 值 */
export function setUserSessionItem(key: string, value: string, user?: UserInfo | null) {
  window.sessionStorage.setItem(userScopedSessionKey(key, user), value)
}

/** 移除用户作用域下的 sessionStorage 值 */
export function removeUserSessionItem(key: string, user?: UserInfo | null) {
  window.sessionStorage.removeItem(userScopedSessionKey(key, user))
}

/** 批量移除旧版（非作用域）sessionStorage 键，用于数据迁移 */
export function removeLegacySessionItems(keys: string[]) {
  for (const key of keys) {
    window.sessionStorage.removeItem(key)
  }
}
