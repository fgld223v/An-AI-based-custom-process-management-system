export interface ChatSession {
  id: number
  title: string
  model: string
  messageCount: number
  lastMessageAt: string | null
  createdAt: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}
