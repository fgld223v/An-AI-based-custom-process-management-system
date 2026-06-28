/**
 * AI 聊天状态管理（Pinia Store — Composition API 风格）。
 *
 * 职责：
 *  - 管理聊天面板的打开/关闭/最小化/折叠
 *  - 管理会话列表、当前会话、消息列表
 *  - 处理 SSE 流式消息接收与中止（超时 + 手动取消）
 *  - 通过 sessionStorage 记住上一次打开的会话
 *
 * 注意：流式请求使用 AbortController 实现超时和手动中止。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { ChatSession, ChatMessage } from '@/types/chat'
import * as chatApi from '@/api/chat'
import { useAuthStore } from './auth'

/** 记录上次打开的会话 ID 的 sessionStorage 键名 */
const LAST_SESSION_KEY = 'ai-chat-last-session'

/** 单次流式请求的最大执行时长（毫秒），超时后自动中止 */
const STREAM_TIMEOUT_MS = 60_000

export const useChatStore = defineStore('chat', () => {
  // ==================== State ====================
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isOpen = ref(false)
  const minimized = ref(false)
  const streaming = ref(false)           // 是否正在流式接收
  const streamingContent = ref('')       // 实时流式内容（用于逐字渲染）
  const sessionsLoading = ref(false)
  const messagesLoading = ref(false)

  // ==================== Stream 生命周期管理 ====================
  let activeAbortController: AbortController | null = null
  let activeTimeoutId: ReturnType<typeof setTimeout> | null = null

  // ==================== Getters ====================
  /** 当前选中的会话对象（或 null） */
  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) ?? null
  )

  // ==================== 面板控制 ====================

  /** 切换聊天面板：已打开则关闭，未打开则打开 */
  function togglePanel() {
    if (isOpen.value) {
      closePanel()
    } else {
      openPanel()
    }
  }

  /** 打开面板并恢复上一次会话 */
  function openPanel() {
    isOpen.value = true
    minimized.value = false
    loadSessions()
    restoreLastSession()
  }

  /** 关闭面板，中止进行中的流式请求 */
  function closePanel() {
    abortStream()
    isOpen.value = false
    minimized.value = false
  }

  /** 切换最小化状态 */
  function toggleMinimize() {
    minimized.value = !minimized.value
  }

  // ==================== 会话 CRUD ====================

  /** 加载全部会话列表 */
  async function loadSessions() {
    sessionsLoading.value = true
    try {
      sessions.value = await chatApi.listSessions()
    } catch {
      // 静默失败 —— 会话列表为空不影响面板使用
    } finally {
      sessionsLoading.value = false
    }
  }

  /** 创建新会话并立即切换到它 */
  async function createAndSwitch(title?: string) {
    const session = await chatApi.createSession(title)
    sessions.value.unshift(session)  // 新会话插入列表头部
    currentSessionId.value = session.id
    messages.value = []
    saveLastSession(session.id)
    return session
  }

  /** 切换到指定会话并加载其历史消息 */
  async function switchSession(sessionId: number) {
    if (streaming.value) {
      abortStream()  // 切换前中止当前流
    }
    currentSessionId.value = sessionId
    messages.value = []
    streamingContent.value = ''
    saveLastSession(sessionId)
    messagesLoading.value = true
    try {
      messages.value = await chatApi.getMessages(sessionId)
    } catch {
      messages.value = []
    } finally {
      messagesLoading.value = false
    }
  }

  /** 删除指定会话；如果删除的是当前会话，自动切换到第一条 */
  async function deleteSession(id: number) {
    await chatApi.deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      if (streaming.value) {
        abortStream()
      }
      currentSessionId.value = null
      messages.value = []
      streamingContent.value = ''
      clearLastSession()
      if (sessions.value.length > 0) {
        await switchSession(sessions.value[0].id)
      }
    }
  }

  // ==================== 消息发送与流式接收 ====================

  /**
   * 发送用户消息并处理 AI 流式回复。
   *
   * 流程：
   *  1. 确保会话存在（无会话时自动创建）
   *  2. 乐观插入用户消息
   *  3. 发起 SSE 流式请求（带超时中止）
   *  4. 逐 chunk 解析 data: 行，实时更新 streamingContent
   *  5. 收到 done 或流结束后插入 AI 最终回复
   *  6. 重新加载会话列表以获取 AI 自动生成的标题
   */
  async function sendMessage(content: string): Promise<void> {
    const authStore = useAuthStore()
    const trimmed = content.trim()
    if (!trimmed || streaming.value) return

    // 防御性中止上一个流（streaming 守卫正常情况下不会走到这里）
    abortStream()

    // 确保当前会话存在
    if (!currentSessionId.value) {
      await createAndSwitch()
    }

    const sid = currentSessionId.value!
    // 乐观创建用户消息（使用负时间戳作为临时 ID）
    const userMsg: ChatMessage = {
      id: -Date.now(),
      sessionId: sid,
      role: 'user',
      content: trimmed,
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMsg)

    streaming.value = true
    streamingContent.value = ''
    let fullContent = ''
    let streamFinished = false

    // 设置 AbortController，用于超时和手动中止
    const abortController = new AbortController()
    activeAbortController = abortController
    const timeoutId = setTimeout(() => {
      abortController.abort()
    }, STREAM_TIMEOUT_MS)
    activeTimeoutId = timeoutId

    try {
      const response = await chatApi.streamChat(
        sid, trimmed, authStore.token, abortController.signal
      )

      if (!response.ok) {
        const errText = await response.text()
        let errMsg = `HTTP ${response.status}`
        try {
          const errJson = JSON.parse(errText)
          errMsg = errJson.message || errMsg
        } catch {
          // 使用 HTTP 状态码作为错误信息
        }
        throw new Error(errMsg)
      }

      // 获取流式读取器
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('浏览器不支持流式读取')
      }

      const decoder = new TextDecoder()
      let buffer = ''  // 缓冲区：处理跨 chunk 的不完整行

      // 循环读取 SSE 数据流
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''  // 最后一行可能不完整，留到下次拼接

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine.startsWith('data:')) continue
          const dataStr = trimmedLine.slice(5).trim()  // 去掉 "data:" 前缀
          if (!dataStr) continue

          try {
            const parsed = JSON.parse(dataStr)
            if (parsed.type === 'done') {
              streamFinished = true
              break
            }
            if (parsed.type === 'error') {
              throw new Error(parsed.message || 'AI 回复出错')
            }
            if (parsed.content) {
              fullContent += parsed.content
              streamingContent.value = fullContent  // 实时更新，驱动 Markdown 渲染
            }
          } catch (e: any) {
            if (e.message && e.message.includes('AI 回复')) throw e
            // 跳过无法解析的数据块（可能是噪音或格式异常）
          }
        }

        if (streamFinished) {
          break
        }
      }

      if (streamFinished) {
        await reader.cancel()
      }

      // 插入 AI 最终回复消息
      if (fullContent) {
        messages.value.push({
          id: -(Date.now() + 1),
          sessionId: sid,
          role: 'assistant',
          content: fullContent,
          createdAt: new Date().toISOString()
        })
      } else {
        messages.value.push({
          id: -(Date.now() + 1),
          sessionId: sid,
          role: 'assistant',
          content: '(AI 未返回内容，请重试)',
          createdAt: new Date().toISOString()
        })
      }

      // 重新加载会话列表以获取 AI 自动生成的会话标题
      await loadSessions()
    } catch (e: any) {
      if (e.name === 'AbortError') {
        ElMessage.warning('AI 回复超时，请重试')
      } else {
        ElMessage.error('AI 回复失败: ' + (e.message || '未知错误'))
      }
      // 失败时移除乐观插入的用户消息
      if (messages.value.length > 0 && messages.value[messages.value.length - 1].id === userMsg.id) {
        messages.value.pop()
      }
    } finally {
      // 无论如何都要清理定时器和 AbortController
      clearTimeout(timeoutId)
      if (activeAbortController === abortController) {
        activeAbortController = null
      }
      if (activeTimeoutId === timeoutId) {
        activeTimeoutId = null
      }
      streaming.value = false
      streamingContent.value = ''
    }
  }

  // ==================== 内部工具函数 ====================

  /** 中止正在进行的流式请求，重置相关状态 */
  function abortStream() {
    if (activeTimeoutId !== null) {
      clearTimeout(activeTimeoutId)
      activeTimeoutId = null
    }
    if (activeAbortController !== null) {
      activeAbortController.abort()
      activeAbortController = null
    }
    streaming.value = false
    streamingContent.value = ''
  }

  // ==================== 会话持久化（sessionStorage） ====================

  /** 记住最后打开的会话 ID */
  function saveLastSession(id: number) {
    try {
      sessionStorage.setItem(LAST_SESSION_KEY, String(id))
    } catch {
      // 静默忽略（sessionStorage 不可用时不影响功能）
    }
  }

  /** 清除记住的会话 ID */
  function clearLastSession() {
    try {
      sessionStorage.removeItem(LAST_SESSION_KEY)
    } catch {
      // 静默忽略
    }
  }

  /** 打开面板时尝试恢复到上次使用的会话 */
  function restoreLastSession() {
    try {
      const saved = sessionStorage.getItem(LAST_SESSION_KEY)
      if (saved) {
        const id = Number(saved)
        if (!isNaN(id) && sessions.value.some(s => s.id === id)) {
          switchSession(id)
        }
      }
    } catch {
      // 静默忽略
    }
  }

  return {
    // state
    sessions,
    currentSessionId,
    messages,
    isOpen,
    minimized,
    streaming,
    streamingContent,
    sessionsLoading,
    messagesLoading,
    // getters
    currentSession,
    // actions
    togglePanel,
    openPanel,
    closePanel,
    toggleMinimize,
    loadSessions,
    createAndSwitch,
    switchSession,
    deleteSession,
    sendMessage,
    abortStream
  }
})
