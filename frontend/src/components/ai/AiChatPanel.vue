<template>
  <!-- 通过 Teleport 将面板挂载到 body，避免父容器 overflow 裁剪 -->
  <Teleport to="body">
    <div v-if="store.isOpen" :class="['chat-panel-overlay', { minimized: store.minimized }]" :style="store.minimized ? {} : panelStyle">
      <!-- 展开状态：完整面板 -->
      <div
        v-if="!store.minimized"
        class="chat-panel"
        @keydown.escape="store.closePanel()"
      >
        <!-- 头部：可拖拽移动，显示 AI 图标、标题和当前会话名 -->
        <div class="chat-header" @mousedown="startDrag" @touchstart="startDrag">
          <div class="chat-header-left">
            <el-icon :size="18" color="#00a3ff"><MagicStick /></el-icon>
            <span>AI 助手</span>
            <!-- 当前会话名称徽章 -->
            <span v-if="store.currentSession" class="session-title-badge">
              {{ store.currentSession.title }}
            </span>
          </div>
          <div class="chat-header-right">
            <!-- 最小化按钮 -->
            <el-button text size="small" @click="store.toggleMinimize()">
              <el-icon :size="16"><Minus /></el-icon>
            </el-button>
            <!-- 关闭面板按钮 -->
            <el-button text size="small" @click="store.closePanel()">
              <el-icon :size="16"><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 面板主体：左侧会话列表 + 右侧消息区域 -->
        <div class="chat-body">
          <!-- 左侧会话列表栏 -->
          <div class="chat-sidebar">
            <!-- 新建对话按钮 -->
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
            <!-- 会话列表：带加载状态 -->
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
                <!-- 删除会话按钮（hover 时显示） -->
                <el-button
                  text
                  size="small"
                  class="session-delete-btn"
                  @click.stop="handleDeleteSession(s)"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                </el-button>
              </div>
              <!-- 无会话空状态 -->
              <el-empty
                v-if="!store.sessionsLoading && store.sessions.length === 0"
                description="暂无对话"
                :image-size="48"
              />
            </div>
          </div>

          <!-- 右侧消息与输入区域 -->
          <div class="chat-main">
            <!-- 消息列表容器 -->
            <div class="chat-messages" ref="messagesRef">
              <!-- 消息加载中 -->
              <div v-if="store.messagesLoading" class="chat-center-hint">
                <el-icon class="is-loading" :size="20"><Loading /></el-icon>
              </div>
              <!-- 无消息时的引导提示 -->
              <el-empty
                v-else-if="store.messages.length === 0 && !store.streaming"
                description="开始和 AI 助手对话吧"
                :image-size="60"
              />
              <!-- 已加载的历史消息 -->
              <template v-else>
                <ChatMessageBubble
                  v-for="msg in store.messages"
                  :key="msg.id"
                  :role="msg.role"
                  :content="msg.content"
                  :created-at="msg.createdAt"
                />
                <!-- 流式输出中的助手消息 -->
                <ChatMessageBubble
                  v-if="store.streaming && store.streamingContent"
                  role="assistant"
                  :content="store.streamingContent"
                  :streaming="true"
                />
                <!-- 流式等待提示（尚未收到内容时） -->
                <div v-if="store.streaming && !store.streamingContent" class="chat-center-hint">
                  <span>AI 思考中...</span>
                </div>
              </template>
            </div>

            <!-- 底部输入区域 -->
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

        <!-- 五个方向的拖拽调整大小手柄 -->
        <div class="resize-handle resize-t" @mousedown.stop="startResizeT" @touchstart.stop="startResizeT" />
        <!-- 右下角缩放手柄（同时改变宽度和高度） -->
        <div class="resize-handle resize-br" @mousedown.stop="startResizeBR" @touchstart.stop="startResizeBR">
          <svg width="12" height="12" viewBox="0 0 12 12"><path d="M0 12L12 0M4 12L12 4M8 12L12 8" stroke="#999" stroke-width="1.2"/></svg>
        </div>
        <div class="resize-handle resize-l" @mousedown.stop="startResizeL" @touchstart.stop="startResizeL" />
        <div class="resize-handle resize-r" @mousedown.stop="startResizeR" @touchstart.stop="startResizeR" />
        <div class="resize-handle resize-b" @mousedown.stop="startResizeB" @touchstart.stop="startResizeB" />
      </div>

      <!-- 最小化状态：仅显示一个可点击的小条 -->
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
/**
 * AiChatPanel - AI 对话面板
 *
 * 浮动的、可拖拽移动和调整大小的 AI 聊天窗口。
 * 支持多会话管理（新建/切换/删除）、流式消息输出、面板最小化。
 * 通过 Teleport 挂载到 body 层，避免父容器的 CSS 裁剪。
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { MagicStick, Minus, Close, Plus, Delete, Loading, Promotion } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import ChatMessageBubble from './ChatMessageBubble.vue'
import type { ChatSession } from '@/types/chat'

const store = useChatStore()
const inputText = ref('')

// ==================================================================
// 面板拖拽与缩放状态管理
// ==================================================================

// ---- 面板默认位置与尺寸 ----
const panelX = ref(window.innerWidth - 500)  // 面板左边缘 X 坐标
const panelY = ref(window.innerHeight - 580) // 面板顶部 Y 坐标
const panelW = ref(480)   // 面板宽度
const panelH = ref(560)   // 面板高度
// ---- 尺寸边界 ----
const MIN_W = 360
const MIN_H = 400
const MAX_W = 900
const MAX_H = 800

// ---- 拖拽状态 ----
const dragging = ref(false)   // 是否正在拖拽
const dragOffX = ref(0)       // 鼠标点击位置相对面板左边缘的偏移
const dragOffY = ref(0)       // 鼠标点击位置相对面板顶边缘的偏移

// ---- 缩放状态 ----
const resizing = ref(false)   // 是否正在缩放
const resizeOffX = ref(0)     // 缩放时的参考 X 坐标
const resizeOffY = ref(0)     // 缩放时的参考 Y 坐标

/** 计算面板定位样式 */
const panelStyle = computed(() => ({
  left: panelX.value + 'px',
  top: panelY.value + 'px',
  width: panelW.value + 'px',
  height: panelH.value + 'px'
}))

/** 统一获取鼠标/触摸事件的客户端坐标 */
function getClient(e: MouseEvent | TouchEvent) {
  return 'touches' in e
    ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
    : { x: (e as MouseEvent).clientX, y: (e as MouseEvent).clientY }
}

/** 开始拖拽：记录偏移量并注册全局事件监听 */
function startDrag(e: MouseEvent | TouchEvent) {
  if (resizing.value) return
  const target = e.target as HTMLElement
  if (target.closest('button')) return  // 忽略按钮点击

  dragging.value = true
  const { x, y } = getClient(e)
  dragOffX.value = x - panelX.value
  dragOffY.value = y - panelY.value

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', stopDrag)
}

/** 拖拽中：更新面板坐标，限制在可视窗口内 */
function onDrag(e: MouseEvent | TouchEvent) {
  if (!dragging.value) return
  e.preventDefault()
  const { x, y } = getClient(e)
  panelX.value = Math.max(0, Math.min(window.innerWidth - 80, x - dragOffX.value))
  panelY.value = Math.max(0, Math.min(window.innerHeight - 40, y - dragOffY.value))
}

/** 结束拖拽：移除全局事件监听 */
function stopDrag() {
  dragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', stopDrag)
}

// ==================================================================
// 缩放：右下角 -- 同时改变宽度和高度
// ==================================================================
function startResizeBR(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  // 记录右下角初始位置
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

// ==================================================================
// 缩放：左边缘 -- 改变 X 坐标和宽度
// ==================================================================
function startResizeL(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  const rightEdge = panelX.value + panelW.value
  resizeOffX.value = rightEdge  // 锁定右边缘位置
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

// ==================================================================
// 缩放：右边缘 -- 仅改变宽度
// ==================================================================
function startResizeR(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  resizeOffX.value = panelX.value  // 锁定左边缘位置
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

// ==================================================================
// 缩放：顶边缘 -- 改变 Y 坐标和高度
// ==================================================================
function startResizeT(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  const bottomEdge = panelY.value + panelH.value
  resizeOffY.value = bottomEdge  // 锁定底边缘位置
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

// ==================================================================
// 缩放：底边缘 -- 仅改变高度，顶部保持固定
// ==================================================================
function startResizeB(e: MouseEvent | TouchEvent) {
  if (dragging.value) return
  e.preventDefault(); e.stopPropagation()
  resizing.value = true
  resizeOffY.value = panelY.value // 锁定顶边缘位置
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

/** 结束缩放：清除所有方向的事件监听 */
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

/** 限制宽度在允许范围内 */
function clampW(w: number) { return Math.max(MIN_W, Math.min(MAX_W, w)) }
/** 限制高度在允许范围内 */
function clampH(h: number) { return Math.max(MIN_H, Math.min(MAX_H, h)) }

/** 组件卸载前清理：停止拖拽/缩放、中断流式请求 */
onBeforeUnmount(() => {
  stopDrag()
  stopResize()
  store.abortStream()
})
// ---- 模板引用 ----
const messagesRef = ref<HTMLElement>()
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const inputRef = ref<any>()

/** 聚焦到输入框（先尝试 el-input，再降级到原生 textarea） */
function focusInput() {
  nextTick(() => {
    inputRef.value?.focus?.()
    inputRef.value?.textarea?.focus?.()
  })
}

// ---- 消息变化时自动滚动到底部 ----
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

// ---- 面板打开时清空输入并聚焦 ----
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

// ---- 流式输出结束后重新聚焦输入框 ----
watch(
  () => store.streaming,
  (isStreaming, wasStreaming) => {
    if (!isStreaming && wasStreaming) {
      focusInput()
    }
  }
)

// ---- 从最小化恢复时聚焦输入框 ----
watch(
  () => store.minimized,
  (minimized) => {
    if (!minimized && store.isOpen) {
      focusInput()
    }
  }
)

/** 发送消息：清空输入框并调用 store 发送 */
async function handleSend() {
  const text = inputText.value
  if (!text.trim() || store.streaming) return
  inputText.value = ''
  await store.sendMessage(text)
}

/** 键盘处理：Enter 发送，Shift+Enter 换行 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

/** 新建 AI 对话 */
async function handleNewChat() {
  await store.createAndSwitch()
}

/** 删除会话：弹出确认框后执行删除 */
async function handleDeleteSession(session: ChatSession) {
  try {
    await ElMessageBox.confirm(
      `确定删除对话「${session.title}」吗？所有消息将被永久删除。`,
      '删除对话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await store.deleteSession(session.id)
  } catch {
    // 用户取消操作
  }
}
</script>

<style scoped>
/* ==================================================================
   外层浮动容器 -- 定位锚点
   ================================================================== */
/* ---- 面板覆盖层：fixed 定位在屏幕上方 ---- */
.chat-panel-overlay {
  position: fixed;
  z-index: 1000;
}

/* ==================================================================
   面板主体
   ================================================================== */
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

/* ==================================================================
   头部 -- 可拖拽区域
   ================================================================== */
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

/* 当前会话名称徽章 */
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

/* ==================================================================
   主体区域 -- 左侧边栏 + 右侧聊天
   ================================================================== */
.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* ==================================================================
   左侧会话列表栏
   ================================================================== */
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

/* 会话滚动列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 单个会话项 */
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

/* 删除按钮默认隐藏，hover 父元素时渐变显示 */
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

/* ==================================================================
   右侧聊天主区域
   ================================================================== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 消息列表容器 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

/* 居中提示（加载中/空状态） */
.chat-center-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--muted);
  font-size: 13px;
  gap: 8px;
}

/* ==================================================================
   底部输入区域
   ================================================================== */
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

/* ==================================================================
   最小化状态栏
   ================================================================== */
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

/* ==================================================================
   缩放拖拽手柄
   ================================================================== */
.resize-handle {
  position: absolute;
  z-index: 10;
}

/* 右下角 -- 同时调整宽高 */
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

/* 左边缘 -- 左右缩放 */
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

/* 右边缘 -- 左右缩放 */
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

/* 顶边缘 -- 上下缩放 */
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

/* 底边缘 -- 上下缩放 */
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
