/**
 * AI 聊天相关类型定义。
 */

/** AI 聊天会话 */
export interface ChatSession {
  id: number
  title: string                    // 会话标题（AI 自动生成或用户自定义）
  model: string                    // 使用的 AI 模型标识
  messageCount: number             // 会话中的消息总数
  lastMessageAt: string | null     // 最后一条消息时间
  createdAt: string                // 创建时间
}

/** 聊天消息 */
export interface ChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'       // 消息角色：用户 或 AI 助手
  content: string                  // 消息正文（Markdown 格式）
  createdAt: string
}
