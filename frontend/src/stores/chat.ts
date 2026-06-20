import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { ChatSession, ChatMessage } from '@/types/chat'
import * as chatApi from '@/api/chat'
import { useAuthStore } from './auth'

const LAST_SESSION_KEY = 'ai-chat-last-session'

export const useChatStore = defineStore('chat', () => {
  // ---- State ----
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isOpen = ref(false)
  const minimized = ref(false)
  const streaming = ref(false)
  const streamingContent = ref('')
  const sessionsLoading = ref(false)
  const messagesLoading = ref(false)

  // ---- Getters ----
  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) ?? null
  )

  // ---- Actions ----

  function togglePanel() {
    if (isOpen.value) {
      isOpen.value = false
      minimized.value = false
    } else {
      openPanel()
    }
  }

  function openPanel() {
    isOpen.value = true
    minimized.value = false
    loadSessions()
    restoreLastSession()
  }

  function closePanel() {
    isOpen.value = false
    minimized.value = false
  }

  function toggleMinimize() {
    minimized.value = !minimized.value
  }

  async function loadSessions() {
    sessionsLoading.value = true
    try {
      sessions.value = await chatApi.listSessions()
    } catch {
      // Silently fail — sessions will be empty
    } finally {
      sessionsLoading.value = false
    }
  }

  async function createAndSwitch(title?: string) {
    const session = await chatApi.createSession(title)
    sessions.value.unshift(session)
    currentSessionId.value = session.id
    messages.value = []
    saveLastSession(session.id)
    return session
  }

  async function switchSession(sessionId: number) {
    if (streaming.value) return // don't switch during streaming
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

  async function deleteSession(id: number) {
    await chatApi.deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = null
      messages.value = []
      streamingContent.value = ''
      clearLastSession()
      if (sessions.value.length > 0) {
        await switchSession(sessions.value[0].id)
      }
    }
  }

  async function sendMessage(content: string): Promise<void> {
    const authStore = useAuthStore()
    const trimmed = content.trim()
    if (!trimmed || streaming.value) return

    // Ensure we have a session
    if (!currentSessionId.value) {
      await createAndSwitch()
    }

    const sid = currentSessionId.value!
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

    try {
      const response = await chatApi.streamChat(sid, trimmed, authStore.token)

      if (!response.ok) {
        const errText = await response.text()
        let errMsg = `HTTP ${response.status}`
        try {
          const errJson = JSON.parse(errText)
          errMsg = errJson.message || errMsg
        } catch {}
        throw new Error(errMsg)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('浏览器不支持流式读取')

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine.startsWith('data:')) continue
          const dataStr = trimmedLine.slice(5).trim()
          if (!dataStr) continue

          try {
            const parsed = JSON.parse(dataStr)
            if (parsed.type === 'done') {
              // Stream complete — the message was saved server-side
              continue
            }
            if (parsed.type === 'error') {
              throw new Error(parsed.message || 'AI 回复出错')
            }
            if (parsed.content) {
              fullContent += parsed.content
              streamingContent.value = fullContent
            }
          } catch (e: any) {
            if (e.message && e.message.includes('AI 回复')) throw e
            // Skip unparseable data
          }
        }
      }

      // Add final assistant message
      if (fullContent) {
        const assistantMsg: ChatMessage = {
          id: -(Date.now() + 1),
          sessionId: sid,
          role: 'assistant',
          content: fullContent,
          createdAt: new Date().toISOString()
        }
        messages.value.push(assistantMsg)
      } else {
        // AI returned empty — add fallback message
        const fallbackMsg: ChatMessage = {
          id: -(Date.now() + 1),
          sessionId: sid,
          role: 'assistant',
          content: '(AI 未返回内容，请重试)',
          createdAt: new Date().toISOString()
        }
        messages.value.push(fallbackMsg)
      }

      // Reload sessions to get updated title/messageCount from server
      await loadSessions()

    } catch (e: any) {
      ElMessage.error('AI 回复失败: ' + (e.message || '未知错误'))
      // Remove the optimistic user message on failure
      messages.value.pop()
    } finally {
      streaming.value = false
      streamingContent.value = ''
    }
  }

  // ---- Persistence ----

  function saveLastSession(id: number) {
    try {
      sessionStorage.setItem(LAST_SESSION_KEY, String(id))
    } catch {}
  }

  function clearLastSession() {
    try {
      sessionStorage.removeItem(LAST_SESSION_KEY)
    } catch {}
  }

  function restoreLastSession() {
    try {
      const saved = sessionStorage.getItem(LAST_SESSION_KEY)
      if (saved) {
        const id = Number(saved)
        if (!isNaN(id) && sessions.value.some(s => s.id === id)) {
          switchSession(id)
        }
      }
    } catch {}
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
    sendMessage
  }
})
