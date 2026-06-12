import request from './request'
import type { TaskCompletePayload, TaskItem } from '@/types/workflow'

/** 待办列表 */
export async function getMyTasks() {
  return await request.get<TaskItem[]>('/api/tasks/my') || []
}

/** 已办列表 */
export async function getDoneTasks() {
  return await request.get<TaskItem[]>('/api/tasks/done') || []
}

/** 任务详情 */
export async function getTask(taskId: string) {
  return await request.get<TaskItem>(`/api/tasks/${taskId}`)
}

/** 完成任务 */
export async function completeTask(taskId: string, data: TaskCompletePayload) {
  return await request.post<TaskItem | null>(`/api/tasks/${taskId}/complete`, data)
}
