<template>
  <div class="page-shell ai-generate-page">
    <div class="page-header">
      <h1>AI 智能流程生成</h1>
      <p>用自然语言描述你的业务流程，AI 将自动生成对应的 BPMN 流程图和节点配置</p>
    </div>

    <div class="input-card">
      <el-input
        v-model="description"
        type="textarea"
        :rows="6"
        placeholder="例如：创建一个请假审批流程，3 天内自动通过，超过 3 天需要部门经理审批，超过 7 天需要总经理审批"
        :disabled="generating"
        maxlength="2000"
        show-word-limit
        resize="none"
      />
      <div class="input-actions">
        <el-button
          type="success"
          :icon="MagicStick"
          :loading="generating"
          size="large"
          round
          @click="handleGenerate"
        >
          {{ generating ? 'AI 正在解析您的需求...' : '生成流程' }}
        </el-button>
        <el-button
          :disabled="generating"
          size="large"
          round
          @click="handleClear"
        >
          清空
        </el-button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="generating" class="loading-card">
      <el-skeleton :rows="8" animated />
      <p class="loading-text">AI 正在分析您的需求并生成 BPMN 流程图...</p>
    </div>

    <!-- 生成结果（D3 填充预览面板） -->
    <div v-if="!generating && result" class="result-card">
      <p>生成结果将在此展示（D3 实现 BPMN 预览面板）</p>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      class="error-alert"
      @close="errorMessage = ''"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const description = ref('')
const generating = ref(false)
const result = ref(null)
const errorMessage = ref('')

async function handleGenerate() {
  const text = description.value.trim()
  if (!text) {
    ElMessage.warning('请先描述您想要的业务流程')
    return
  }

  generating.value = true
  errorMessage.value = ''
  result.value = null

  try {
    const { generateProcess } = await import('@/api/ai')
    const data = await generateProcess(text)
    result.value = data
  } catch (e: any) {
    errorMessage.value = e?.message || '生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

function handleClear() {
  description.value = ''
  result.value = null
  errorMessage.value = ''
}
</script>

<style scoped>
.ai-generate-page {
  max-width: 840px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 28px;
  text-align: center;
}

.page-header h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 800;
  color: var(--text);
  letter-spacing: 0;
}

.page-header p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.input-card {
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.input-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  justify-content: center;
}

.loading-card {
  margin-top: 22px;
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.loading-text {
  margin: 20px 0 0;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}

.result-card {
  margin-top: 22px;
  padding: 60px 0;
  text-align: center;
  color: var(--muted);
  border: 1px dashed var(--line);
  border-radius: 22px;
  background: var(--panel-soft);
}

.error-alert {
  margin-top: 22px;
}
</style>
