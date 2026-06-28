<template>
  <!-- 聊天消息气泡组件：支持用户和 AI 助手两种角色，助手消息支持 Markdown 渲染 -->
  <div :class="['chat-bubble', role]">
    <!-- 头像区域：AI 助手用魔法棒图标，用户用人像图标 -->
    <div class="bubble-avatar">
      <el-icon v-if="role === 'assistant'" :size="18"><MagicStick /></el-icon>
      <el-icon v-else :size="18"><UserFilled /></el-icon>
    </div>
    <!-- 消息主体 -->
    <div class="bubble-body">
      <!-- 消息头部：角色名 + 时间 -->
      <div class="bubble-header">
        <span class="bubble-role">{{ role === 'assistant' ? 'AI 助手' : '我' }}</span>
        <span class="bubble-time">{{ timeText }}</span>
      </div>
      <!-- 助手消息使用 Markdown 渲染 -->
      <div
        v-if="role === 'assistant'"
        class="bubble-content markdown-body"
        v-html="renderedContent"
      />
      <!-- 用户消息纯文本展示 -->
      <div v-else class="bubble-content">
        {{ content }}
      </div>
      <!-- 流式输出光标动画 -->
      <div v-if="streaming" class="streaming-cursor">|</div>
    </div>
    <!-- 复制按钮（仅助手消息且非流式输出时显示） -->
    <el-button
      v-if="role === 'assistant' && !streaming && content"
      class="copy-btn"
      text
      size="small"
      @click="copyContent"
    >
      <el-icon :size="14"><CopyDocument /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MagicStick, UserFilled, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  /** 消息角色：用户或助手 */
  role: 'user' | 'assistant'
  /** 消息文本内容 */
  content: string
  /** 是否处于流式输出状态 */
  streaming?: boolean
  /** 消息创建时间（ISO 字符串） */
  createdAt?: string
}>()

/**
 * 计算相对时间文本
 * 小于1分钟显示"刚刚"，小于1小时显示"x分钟前"，小于1天显示"x小时前"，否则显示月日
 */
const timeText = computed(() => {
  if (!props.createdAt) return ''
  try {
    const d = new Date(props.createdAt)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
    if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
})

/**
 * 将 Markdown 内容渲染为 HTML
 * 流式输出时在末尾添加闪烁光标
 */
const renderedContent = computed(() => {
  if (!props.content) return ''
  let html = renderMarkdown(props.content)
  if (props.streaming) {
    html += '<span class="cursor-blink">|</span>'
  }
  return html
})

/** 复制消息内容到剪贴板 */
async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.chat-bubble {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  animation: bubbleIn 0.3s ease;
}

.chat-bubble.user {
  flex-direction: row-reverse;
}

.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4px;
}

.chat-bubble.assistant .bubble-avatar {
  background: linear-gradient(135deg, #00a3ff, #19c37d);
  color: #fff;
}

.chat-bubble.user .bubble-avatar {
  background: var(--el-color-primary);
  color: #fff;
}

.bubble-body {
  max-width: 75%;
  min-width: 0;
}

.chat-bubble.user .bubble-body {
  text-align: right;
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.chat-bubble.user .bubble-header {
  justify-content: flex-end;
}

.bubble-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
}

.bubble-time {
  font-size: 11px;
  color: var(--muted);
  opacity: 0.7;
}

.bubble-content {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.65;
  word-break: break-word;
}

.chat-bubble.user .bubble-content {
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-bubble.assistant .bubble-content {
  background: var(--el-fill-color-lighter);
  border-bottom-left-radius: 4px;
}

/* Markdown overrides inside bubble */
.bubble-content.markdown-body :deep(pre) {
  margin: 10px 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #1e1e2e;
  color: #cdd6f4;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.5;
  position: relative;
}

.bubble-content.markdown-body :deep(code) {
  font-family: var(--fm, 'SF Mono', 'Fira Code', monospace);
  font-size: 12.5px;
}

.bubble-content.markdown-body :deep(p) {
  margin: 4px 0;
}

.bubble-content.markdown-body :deep(ul),
.bubble-content.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.bubble-content.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--el-color-primary);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--muted);
  background: rgba(0, 0, 0, 0.02);
  border-radius: 0 6px 6px 0;
}

.bubble-content.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12.5px;
}

.bubble-content.markdown-body :deep(th),
.bubble-content.markdown-body :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 6px 10px;
}

.bubble-content.markdown-body :deep(th) {
  background: var(--el-fill-color);
}

.copy-btn {
  align-self: flex-start;
  margin-top: 24px;
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-bubble:hover .copy-btn {
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1 !important;
}

.streaming-cursor {
  display: inline-block;
  color: var(--el-color-primary);
  font-weight: bold;
}

@keyframes bubbleIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.cursor-blink {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
