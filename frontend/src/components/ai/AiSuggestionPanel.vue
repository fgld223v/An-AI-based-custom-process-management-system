<template>
  <div class="ai-suggestion-panel">
    <!-- 加载态 -->
    <el-skeleton v-if="loading" :rows="3" animated />

    <!-- 结果 -->
    <div v-else-if="suggestion" class="suggestion-content">
      <div class="suggestion-header">
        <span class="suggestion-label">AI 辅助审批建议</span>
        <el-tag :type="tagType" effect="dark" size="default">{{ suggestionText }}</el-tag>
      </div>
      <p class="suggestion-reason">{{ suggestion.reason }}</p>
      <div class="suggestion-meta">
        <span class="confidence">置信度：{{ Math.round((suggestion.confidence || 0) * 100) }}%</span>
      </div>
      <div v-if="suggestion.riskPoints?.length" class="risk-list">
        <span class="risk-label">风险提示：</span>
        <el-tag v-for="(point, i) in suggestion.riskPoints" :key="i" type="warning" size="small" effect="plain" class="risk-tag">
          {{ point }}
        </el-tag>
      </div>
      <el-button type="primary" size="small" :disabled="disabled" @click="$emit('adopt', suggestion)">
        采纳建议
      </el-button>
    </div>

    <!-- 错误/无建议 -->
    <div v-else-if="!loading && errorMessage" class="suggestion-empty">
      <span>{{ errorMessage }}</span>
      <el-button text type="primary" size="small" @click="fetchSuggestion">重试</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'

const props = defineProps<{
  instanceId: number
  nodeKey: string
  disabled?: boolean
}>()

defineEmits<{
  adopt: [suggestion: Suggestion]
}>()

interface Suggestion {
  suggestion: string   // approve / reject / supplement
  reason: string
  confidence: number
  riskPoints?: string[]
}

const loading = ref(false)
const suggestion = ref<Suggestion | null>(null)
const errorMessage = ref('')

const tagType = computed(() => {
  const map: Record<string, string> = {
    approve: 'success',
    reject: 'danger',
    supplement: 'warning'
  }
  return map[suggestion.value?.suggestion || ''] || 'info'
})

const suggestionText = computed(() => {
  const map: Record<string, string> = {
    approve: '建议通过',
    reject: '建议驳回',
    supplement: '建议补充材料'
  }
  return map[suggestion.value?.suggestion || ''] || suggestion.value?.suggestion || ''
})

async function fetchSuggestion() {
  if (!props.instanceId || !props.nodeKey) return
  loading.value = true
  errorMessage.value = ''
  suggestion.value = null
  try {
    const { suggestApproval } = await import('@/api/ai')
    const data = await suggestApproval(props.instanceId, props.nodeKey)
    suggestion.value = data
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

watch(() => [props.instanceId, props.nodeKey], () => {
  if (props.instanceId && props.nodeKey) fetchSuggestion()
})

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
