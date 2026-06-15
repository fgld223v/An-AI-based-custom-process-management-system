import request from './request'
import type { NotificationItem, NotificationQuery } from '@/types/workflow'

export async function getNotifications(params?: NotificationQuery) {
  return await request.get<NotificationItem[]>('/api/notifications', { params }) || []
}

export async function markNotificationRead(id: number) {
  return await request.put<NotificationItem>(`/api/notifications/${id}/read`)
}

export async function markNotificationUnread(id: number) {
  return await request.put<NotificationItem>(`/api/notifications/${id}/unread`)
}

export async function getUnreadNotificationCount() {
  return await request.get<{ count: number }>('/api/notifications/unread-count')
}
