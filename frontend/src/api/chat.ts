import request from './request'
import type { ChatSession, ChatMessage } from '@/types/chat'

/** List all chat sessions for current user */
export function listSessions() {
  return request.get<ChatSession[]>('/api/ai/chat/sessions')
}

/** Create a new chat session */
export function createSession(title?: string) {
  return request.post<ChatSession>('/api/ai/chat/sessions', { title })
}

/** Delete a chat session and all its messages */
export function deleteSession(id: number) {
  return request.delete<void>(`/api/ai/chat/sessions/${id}`)
}

/** Get all messages for a session */
export function getMessages(sessionId: number) {
  return request.get<ChatMessage[]>(`/api/ai/chat/sessions/${sessionId}/messages`)
}

/**
 * Stream chat via SSE.
 * Uses native fetch (not axios) because we need to read the response
 * body as a ReadableStream for token-by-token rendering.
 */
export function streamChat(
  sessionId: number,
  message: string,
  token: string
): Promise<Response> {
  return fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ sessionId, message })
  })
}
