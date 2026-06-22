<template>
  <Teleport to="body">
    <div v-if="store.isOpen" :class="['chat-panel-overlay', { minimized: store.minimized }]" :style="store.minimized ? {} : panelStyle">
      <div
        v-if="!store.minimized"
        class="chat-panel"
        @keydown.escape="store.closePanel()"
      >
        <!-- Header (draggable) -->
        <div class="chat-header" @mousedown="startDrag" @touchstart="startDrag">
          <div class="chat-header-left">
            <el-icon :size="18" color="#00a3ff"><MagicStick /></el-icon>
            <span>AI 助手</span>
            <span v-if="store.currentSession" class="session-title-badge">
              {{ store.currentSession.title }}
            </span>
          </div>
          <div class="chat-header-right">
            <el-button text size="small" @click="store.toggleMinimize()">
              <el-icon :size="16"><Minus /></el-icon>
            </el-button>
            <el-button text size="small" @click="store.closePanel()">
              <el-icon :size="16"><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- Body -->
        <div class="chat-body">
          <!-- Session Sidebar -->
          <div class="chat-sidebar">
            <el-button
              type="primary"
              size="small"
              :icon="Plus"
              class="new-chat-btn"
              :disabled="store.streaming"
              @click="handleNewChat"
            >
              新对话
            </el-button>
            <div class="session-list" v-loading="store.sessionsLoading">
              <div
                v-for="s in store.sessions"
                :key="s.id"
                :class="['session-item', { active: s.id === store.currentSessionId }]"
                @click="store.switchSession(s.id)"
              >
                <div class="session-item-body">
                  <span class="session-title">{{ s.title }}</span>
                  <span class="session-meta">{{ s.messageCount }} 条消息</span>
                </div>
                <el-button
                  text
                  size="small"
                  class="session-delete-btn"
                  @click.stop="handleDeleteSession(s)"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                </el-button>
              </div>
              <el-empty
                v-if="!store.sessionsLoading && store.sessions.length === 0"
                description="暂无对话"
                :image-size="48"
              />
            </div>
          </div>

          <!-- Message Area -->
          <div class="chat-main">
            <div class="chat-messages" ref="messagesRef">
              <div v-if="store.messagesLoading" class="chat-center-hint">
                <el-icon class="is-loading" :size="20"><Loading /></el-icon>
              </div>
              <el-empty
                v-else-if="store.messages.length === 0 && !store.streaming"
                description="开始和 AI 助手对话吧"
                :image-size="60"
              />
              <template v-else>
                <ChatMessageBubble
                  v-for="msg in store.messages"
                  :key="msg.id"
                  :role="msg.role"
                  :content="msg.content"
                  :created-at="msg.createdAt"
                />
                <!-- Streaming message -->
                <ChatMessageBubble
                  v-if="store.streaming && store.streamingContent"
                  role="assistant"
                  :content="store.streamingContent"
                  :streaming="true"
                />
                <!-- Empty streaming placeholder -->
                <div v-if="store.streaming && !store.streamingContent" class="chat-center-hint">
                  <span>AI 思考中...</span>
                </div>
              </template>
            </div>

            <!-- Input Area -->
            <div class="chat-input-area" @mousedown.stop @click.stop>
              <el-input
                ref="inputRef"
                v-model="inputText"
                type="textarea"
                :rows="2"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                resize="none"
                @keydown="handleKeydown"
              />
              <el-button
                type="primary"
                :icon="Promotion"
                :disabled="!inputText.trim() || store.streaming"
                :loading="store.streaming"
                @click="handleSend"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>

        <!-- Resize handles -->
        <div class="resize-handle resize-t" @mousedown.stop="startResizeT" @touchstart.stop="startResizeT" />
        <div class="resize-handle resize-br" @mousedown.stop="startResizeBR" @touchstart.stop="startResizeBR">
          <svg width="12" height="12" viewBox="0 0 12 12"><path d="M0 12L12 0M4 12L12 4M8 12L12 8" stroke="#999" stroke-width="1.2"/></svg>
        </div>
        <div class="resize-handle resize-l" @mousedown.stop="startResizeL" @touchstart.stop="startResizeL" />
        <div class="resize-handle resize-r" @mousedown.stop="startResizeR" @touchstart.stop="startResizeR" />
        <div class="resize-handle resize-b" @mousedown.stop="startResizeB" @touchstart.stop="startResizeB" />
      </div>

      <!-- Minimized Bar -->
      <div v-else class="chat-minimized-bar" @click="store.toggleMinimize()">
        <el-icon :size="18" color="#00a3ff"><MagicStick /></el-icon>
        <span>AI 助手</span>
        <el-button text size="small" @click.stop="store.closePanel()">
          <el-icon :size="14"><Close /></el-icon>
        </el-button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { MagicStick, Minus, Close, Plus, Delete, Loading, Promotion } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import ChatMessageBubble from './ChatMessageBubble.vue'
import type { ChatSession } from '@/types/chat'

const store = useChatStore()
const inputText = ref('')

// ---- Drag & Resize State ----
const panelX = ref(window.innerWidth - 500)  // left edge
const panelY = ref(window.innerHeight - 580) // top edge
const panelW = ref(480)
const panelH = ref(560)
const MIN_W = 360
const MIN_H = 400
const MAX_W = 900
const MAX_H = 800

const dragging = ref(false)
const dragOffX = ref(0)
const dragOffY = ref(0)

const resizing = ref(false)
const resizeOffX = ref(0)
const resizeOffY = ref(0)

const panelStyle = computed(() => ({
  left: panelX.value + 'px',
  top: panelY.value + 'px',
  width: panelW.value + 'px',
  height: panelH.value + 'px'
}))

function getClient(e: MouseEvent | TouchEvent) {
  return 'touches' in e
    ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
    : { x: (e as MouseEvent).clientX, y: (e as MouseEvent).clientY }
}

function startDrag(e: MouseEvent | TouchEvent) {
  if (resizing.value) return
  const target = e.target as HTMLElement
  if (target.closest('button')) return

  dragging.value = true
  const { x, y } = getClient(e)
  dragOffX.value = x - panelX.value
  dragOffY.value = y - panelY.value

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', stopDrag)
}

function onDrag(e: MouseEvent | TouchEvent) {
  if (!dragging.value) return
  e.preventDefault()
  const { x, y } = getClient(e)
  panelX.value = Math.max(0, Math.min(window.innerWidth - 80, x - dragOffX.value))
  panelY.value = Math.max(0, Math.min(window.innerHeight - 40, y - dragOffY.value))
}

function stopDrag() {
  dragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
}

// ---- Resize (bottom-right corner: changes W+H) ----
function startResizeBR(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  resizeOffX.value = panelX.value + panelW.value
  resizeOffY.value = panelY.value + panelH.value
  document.addEventListener('mousemove', onResizeBR)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', onResizeBR, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function onResizeBR(e: MouseEvent | TouchEvent) {
  if (!resizing.value) return
  e.preventDefault()
  const { x, y } = getClient(e)
  panelW.value = clampW(x - panelX.value)
  panelH.value = clampH(y - panelY.value)
}

// ---- Resize (left edge: changes X+W) ----
function startResizeL(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  const rightEdge = panelX.value + panelW.value
  resizeOffX.value = rightEdge
  document.addEventListener('mousemove', onResizeL)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', onResizeL, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function onResizeL(e: MouseEvent | TouchEvent) {
  if (!resizing.value) return
  e.preventDefault()
  const { x } = getClient(e)
  const rightEdge = resizeOffX.value
  const newX = Math.max(0, x)
  panelX.value = newX
  panelW.value = clampW(rightEdge - newX)
}

// ---- Resize (right edge: changes W only) ----
function startResizeR(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  resizeOffX.value = panelX.value
  document.addEventListener('mousemove', onResizeR)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', onResizeR, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function onResizeR(e: MouseEvent | TouchEvent) {
  if (!resizing.value) return
  e.preventDefault()
  const { x } = getClient(e)
  panelW.value = clampW(x - panelX.value)
}

// ---- Resize (top edge: changes Y+H) ----
function startResizeT(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  const bottomEdge = panelY.value + panelH.value
  resizeOffY.value = bottomEdge
  document.addEventListener('mousemove', onResizeT)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', onResizeT, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function onResizeT(e: MouseEvent | TouchEvent) {
  if (!resizing.value) return
  e.preventDefault()
  const { y } = getClient(e)
  const bottomEdge = resizeOffY.value
  const newY = Math.max(0, Math.min(bottomEdge - MIN_H, y))
  panelY.value = newY
  panelH.value = clampH(bottomEdge - newY)
}

// ---- Resize (bottom edge: changes H, top stays fixed) ----
function startResizeB(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  resizeOffY.value = panelY.value // Lock the top edge position
  document.addEventListener('mousemove', onResizeB)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', onResizeB, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function onResizeB(e: MouseEvent | TouchEvent) {
  if (!resizing.value) return
  e.preventDefault()
  const { y } = getClient(e)
  const topEdge = resizeOffY.value
  panelH.value = clampH(y - topEdge)
}

function stopResize() {
  resizing.value = false
  document.removeEventListener('mousemove', onResizeT)
  document.removeEventListener('mousemove', onResizeBR)
  document.removeEventListener('mousemove', onResizeL)
  document.removeEventListener('mousemove', onResizeR)
  document.removeEventListener('mousemove', onResizeB)
  document.removeEventListener('mouseup', stopResize)
  document.removeEventListener('touchmove', onResizeT)
  document.removeEventListener('touchmove', onResizeBR)
  document.removeEventListener('touchmove', onResizeL)
  document.removeEventListener('touchmove', onResizeR)
  document.removeEventListener('touchmove', onResizeB)
  document.removeEventListener('touchend', stopResize)
}

function clampW(w: number) { return Math.max(MIN_W, Math.min(MAX_W, w)) }
function clampH(h: number) { return Math.max(MIN_H, Math.min(MAX_H, h)) }

onBeforeUnmount(() => {
  stopDrag()
  stopResize()
  store.abortStream()
})
const messagesRef = ref<HTMLElement>()
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const inputRef = ref<any>()

function focusInput() {
  nextTick(() => {
    inputRef.value?.focus?.()
    inputRef.value?.textarea?.focus?.()
  })
}

// Auto-scroll when messages change
watch(
  () => [store.messages.length, store.streamingContent],
  () => {
    nextTick(() => {
      if (messagesRef.value) {
        messagesRef.value.scrollTop = messagesRef.value.scrollHeight
      }
    })
  }
)

// Focus input when panel opens
watch(
  () => store.isOpen,
  (open) => {
    if (open && !store.minimized) {
      nextTick(() => {
        inputText.value = ''
      })
      focusInput()
    }
  }
)

// Re-focus textarea after streaming ends so the next question can be typed immediately.
watch(
  () => store.streaming,
  (isStreaming, wasStreaming) => {
    if (!isStreaming && wasStreaming) {
      focusInput()
    }
  }
)

watch(
  () => store.minimized,
  (minimized) => {
    if (!minimized && store.isOpen) {
      focusInput()
    }
  }
)

async function handleSend() {
  const text = inputText.value
  if (!text.trim() || store.streaming) return
  inputText.value = ''
  await store.sendMessage(text)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleNewChat() {
  await store.createAndSwitch()
}

async function handleDeleteSession(session: ChatSession) {
  try {
    await ElMessageBox.confirm(
      `确定删除对话「${session.title}」吗？所有消息将被永久删除。`,
      '删除对话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await store.deleteSession(session.id)
  } catch {
    // cancelled
  }
}
</script>

<style scoped>
/* ---- Overlay ---- */
.chat-panel-overlay {
  position: fixed;
  z-index: 1000;
}

/* ---- Panel ---- */
.chat-panel {
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  background: var(--el-bg-color);
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.18);
  border: 1px solid var(--el-border-color);
  overflow: hidden;
  width: 100%;
  height: 100%;
}

/* ---- Header ---- */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  flex-shrink: 0;
  cursor: move;
  user-select: none;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  min-width: 0;
}

.session-title-badge {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
  padding: 1px 8px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ---- Body ---- */
.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* ---- Sidebar ---- */
.chat-sidebar {
  width: 170px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  padding: 8px;
  background: var(--el-fill-color-lighter);
}

.new-chat-btn {
  margin-bottom: 8px;
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover {
  background: var(--el-fill-color);
}

.session-item.active {
  background: var(--el-color-primary-light-9);
}

.session-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-title {
  font-size: 12.5px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  font-size: 11px;
  color: var(--muted);
}

.session-delete-btn {
  opacity: 0;
  flex-shrink: 0;
}

.session-item:hover .session-delete-btn {
  opacity: 0.5;
}

.session-item:hover .session-delete-btn:hover {
  opacity: 1;
}

/* ---- Main Chat ---- */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

.chat-center-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--muted);
  font-size: 13px;
  gap: 8px;
}

/* ---- Input ---- */
.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  flex-shrink: 0;
}

.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}

/* ---- Minimized Bar ---- */
.chat-minimized-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 12px;
  background: var(--el-bg-color);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--el-border-color);
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  user-select: none;
}

/* ---- Resize Handles ---- */
.resize-handle {
  position: absolute;
  z-index: 10;
}

.resize-br {
  bottom: -1px;
  right: -1px;
  width: 22px;
  height: 22px;
  cursor: nwse-resize;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 3px;
}

.resize-br:hover svg path {
  stroke: var(--el-color-primary);
}

.resize-l {
  left: 0;
  top: 40px;
  bottom: 76px;
  width: 6px;
  cursor: ew-resize;
}

.resize-l:hover {
  background: rgba(0, 163, 255, 0.15);
}

.resize-r {
  right: 0;
  top: 40px;
  bottom: 76px;
  width: 6px;
  cursor: ew-resize;
}

.resize-r:hover {
  background: rgba(0, 163, 255, 0.15);
}

.resize-t {
  top: 0;
  left: 72px;
  right: 72px;
  height: 6px;
  cursor: ns-resize;
}

.resize-t:hover {
  background: rgba(0, 163, 255, 0.15);
}

.resize-b {
  bottom: 0;
  left: 72px;
  right: 72px;
  height: 6px;
  cursor: ns-resize;
}

.resize-b:hover {
  background: rgba(0, 163, 255, 0.15);
}
</style>
