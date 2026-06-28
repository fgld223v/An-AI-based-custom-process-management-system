<template>
  <!-- AI 审批建议面板：展示 AI 辅助审批结果、置信度和风险提示 -->
  <div class="ai-suggestion-panel">
    <!-- 加载骨架屏 -->
    <el-skeleton v-if="loading" :rows="3" animated />

    <!-- 建议结果展示 -->
    <div v-else-if="suggestion" class="suggestion-content">
      <!-- 头部：标签 + 建议结论标签 -->
      <div class="suggestion-header">
        <span class="suggestion-label">AI 辅助审批建议</span>
        <el-tag :type="tagType" effect="dark" size="default">{{ suggestionText }}</el-tag>
      </div>
      <!-- 建议理由 -->
      <p class="suggestion-reason">{{ suggestion.reason }}</p>
      <!-- 置信度 -->
      <div class="suggestion-meta">
        <span class="confidence">置信度：{{ Math.round((suggestion.confidence || 0) * 100) }}%</span>
      </div>
      <!-- 风险提示列表 -->
      <div v-if="suggestion.riskPoints?.length" class="risk-list">
        <span class="risk-label">风险提示：</span>
        <el-tag v-for="(point, i) in suggestion.riskPoints" :key="i" type="warning" size="small" effect="plain" class="risk-tag">
          {{ point }}
        </el-tag>
      </div>
      <!-- 操作按钮 -->
      <div class="suggestion-actions">
        <el-button type="primary" size="small" :disabled="disabled" @click="$emit('adopt', suggestion)">采纳建议</el-button>
        <el-button text size="small" :loading="loading" @click="fetchSuggestion">重新分析</el-button>
      </div>
    </div>

    <!-- 错误/空状态：显示错误消息 + 重试按钮 -->
    <div v-else-if="!loading && errorMessage" class="suggestion-empty">
      <span>{{ errorMessage }}</span>
      <el-button text type="primary" size="small" @click="fetchSuggestion">重试</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { AiApprovalSuggestion } from '@/api/ai'

/** 组件属性 */
const props = defineProps<{
  /** 流程实例 ID */
  instanceId: number
  /** 当前节点 Key */
  nodeKey: string
  /** 是否禁用采纳按钮 */
  disabled?: boolean
}>()

/** 组件事件 */
defineEmits<{
  adopt: [suggestion: AiApprovalSuggestion]
}>()

/** 加载状态 */
const loading = ref(false)
/** AI 建议结果 */
const suggestion = ref<AiApprovalSuggestion | null>(null)
/** 错误消息 */
const errorMessage = ref('')

/** 建议类型对应的 Element UI Tag 类型 */
const tagType = computed(() => {
  const map: Record<string, string> = {
    approve: 'success',
    reject: 'danger',
    supplement: 'warning'
  }
  return map[suggestion.value?.suggestion || ''] || 'info'
})

/** 建议类型转中文文本 */
const suggestionText = computed(() => {
  const map: Record<string, string> = {
    approve: '建议通过',
    reject: '建议驳回',
    supplement: '建议补充材料'
  }
  return map[suggestion.value?.suggestion || ''] || suggestion.value?.suggestion || ''
})

/**
 * 获取 AI 审批建议
 * 根据当前实例 ID 和节点 Key 向 AI 服务请求审批建议
 */
async function fetchSuggestion() {
  if (!props.instanceId || !props.nodeKey) return
  loading.value = true
  errorMessage.value = ''
  suggestion.value = null
  try {
    // 动态导入 AI API 模块，避免循环依赖
    const { suggestApproval } = await import('@/api/ai')
    suggestion.value = await suggestApproval(props.instanceId, props.nodeKey)
  } catch (e: any) {
    const code = e?.response?.status
    if (code === 501) {
      errorMessage.value = 'AI 审批建议功能暂未启用'
    } else {
      errorMessage.value = e?.response?.data?.message || 'AI 建议获取失败'
    }
  } finally {
    loading.value = false
  }
}

/** 监听实例 ID 和节点 Key 变化，自动获取建议 */
watch(() => [props.instanceId, props.nodeKey], () => {
  if (props.instanceId && props.nodeKey) fetchSuggestion()
})

/** 组件挂载时自动获取建议 */
onMounted(() => {
  if (props.instanceId && props.nodeKey) fetchSuggestion()
})
</script>

<style scoped>
.ai-suggestion-panel {
  padding: 18px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--panel-soft);
}

.suggestion-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.suggestion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.suggestion-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.suggestion-reason {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.suggestion-meta {
  font-size: 12px;
  color: var(--muted);
}

.confidence {
  font-family: var(--fm);
}

.risk-list {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.risk-label {
  font-size: 12px;
  color: #9e7530;
}

.risk-tag {
  margin: 0;
}

.suggestion-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}
</style>
