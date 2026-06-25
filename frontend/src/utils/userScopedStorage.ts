import type { UserInfo } from '@/types/auth'

const PREFIX = 'ai-flow:user-session'

function resolveUserScope(user?: UserInfo | null) {
  if (user?.id !== undefined && user.id !== null) {
    return String(user.id)
  }
  if (user?.username) {
    return user.username
  }
  return 'anonymous'
}

export function userScopedSessionKey(key: string, user?: UserInfo | null) {
  return `${PREFIX}:${resolveUserScope(user)}:${key}`
}

export function getUserSessionItem(key: string, user?: UserInfo | null) {
  return window.sessionStorage.getItem(userScopedSessionKey(key, user))
}

export function setUserSessionItem(key: string, value: string, user?: UserInfo | null) {
  window.sessionStorage.setItem(userScopedSessionKey(key, user), value)
}

export function removeUserSessionItem(key: string, user?: UserInfo | null) {
  window.sessionStorage.removeItem(userScopedSessionKey(key, user))
}

export function removeLegacySessionItems(keys: string[]) {
  for (const key of keys) {
    window.sessionStorage.removeItem(key)
  }
}
