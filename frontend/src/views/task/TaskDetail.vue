<template>
  <div class="task-page">
    <section class="page-head">
      <div>
        <el-tag v-if="task?.status === 'active'" type="warning" effect="plain">待处理</el-tag>
        <el-tag v-else type="success" effect="plain">已完成</el-tag>
        <h1>{{ task?.taskName || '任务详情' }}</h1>
        <p>{{ task?.instanceTitle || '-' }} · {{ task?.instanceCode || '-' }}</p>
      </div>
      <div class="head-actions">
        <el-button round @click="router.back()">返回</el-button>
      </div>
    </section>

    <el-alert v-if="message" type="warning" show-icon :closable="false" :title="message" />
    <el-alert v-if="successMsg" type="success" show-icon :closable="false" :title="successMsg" />

    <section class="info-panel" v-loading="loading">
      <div class="info-grid">
        <div class="info-card">
          <span>任务ID</span>
          <strong>{{ task?.taskId?.substring(0, 12) || '-' }}...</strong>
        </div>
        <div class="info-card">
          <span>任务节点 Key</span>
          <strong>{{ task?.taskDefinitionKey || '-' }}</strong>
        </div>
        <div class="info-card">
          <span>处理人</span>
          <strong>{{ task?.assignee || '未分配' }}</strong>
        </div>
        <div class="info-card">
          <span>审批方式</span>
          <strong>
            <el-tag v-if="task?.approvalMode === 'ALL'" type="primary" size="small" effect="plain">会签（全部通过）</el-tag>
            <el-tag v-else-if="task?.approvalMode === 'ANY'" type="success" size="small" effect="plain">或签（任一通过）</el-tag>
            <span v-else>单人审批</span>
          </strong>
        </div>
        <div class="info-card">
          <span>创建时间</span>
          <strong>{{ task?.createTime || '-' }}</strong>
        </div>
        <div class="info-card">
          <span>到期时间</span>
          <strong>{{ task?.dueDate || '无' }}</strong>
        </div>
        <div class="info-card">
          <span>完成时间</span>
          <strong>{{ task?.endTime || '-' }}</strong>
        </div>
        <div class="info-card">
          <span>Flowable 流程实例ID</span>
          <strong>{{ task?.processInstanceId?.substring(0, 12) || '-' }}...</strong>
        </div>
        <div class="info-card">
          <span>业务实例ID</span>
          <strong>{{ task?.businessInstanceId || '-' }}</strong>
        </div>
        <div class="info-card">
          <span>关联表单</span>
          <strong>{{ task?.formId || '-' }}</strong>
        </div>
        <div v-if="task?.allAssignees" class="info-card">
          <span>所有审批人</span>
          <strong>{{ task.allAssignees }}</strong>
        </div>
      </div>
    </section>

    <!-- 多实例审批进度（会签/或签） -->
    <section v-if="task?.approvalMode === 'ALL' || task?.approvalMode === 'ANY'" class="progress-panel">
      <div class="progress-header">
        <h2>{{ task?.approvalMode === 'ALL' ? '会签进度' : '或签进度' }}</h2>
        <span class="progress-subtitle">
          {{ task?.approvalMode === 'ALL' ? '所有审批人通过后流程继续' : '任一审批人通过后流程继续' }}
        </span>
      </div>
      <div class="progress-content">
        <div class="progress-numbers">
          <div class="progress-stat">
            <span class="stat-value">{{ task?.nrOfCompletedInstances ?? 0 }} / {{ task?.nrOfInstances ?? '?' }}</span>
            <span class="stat-label">已完成 / 总数</span>
          </div>
          <div class="progress-stat">
            <span class="stat-value">{{ task?.nrOfActiveInstances ?? 0 }}</span>
            <span class="stat-label">进行中</span>
          </div>
        </div>
        <el-progress
          v-if="task?.nrOfInstances && task.nrOfInstances > 0"
          :percentage="Math.round(((task.nrOfCompletedInstances ?? 0) / task.nrOfInstances) * 100)"
          :stroke-width="16"
          :color="task.approvalMode === 'ALL' ? '#409EFF' : '#67C23A'"
        />
      </div>
    </section>

    <!-- AI 审批建议（仅 active 状态） -->
    <AiSuggestionPanel
      v-if="task?.status === 'active'"
      :instance-id="task.businessInstanceId"
      :node-key="task.taskDefinitionKey"
      :disabled="submitting"
      @adopt="handleAdoptSuggestion"
    />

    <!-- 审批表单（仅 active 状态） -->
    <section v-if="task?.status === 'active'" class="form-panel">
      <div class="section-title">
        <h2>审批处理</h2>
      </div>
      <el-form :model="form" label-width="100px" label-position="top">
        <el-form-item label="审批结果">
          <el-radio-group v-model="form.approvalResult">
            <el-radio value="agree">同意</el-radio>
            <el-radio value="reject">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="form.approvalComment"
            type="textarea"
            :rows="4"
            placeholder="请输入审批意见..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" round @click="handleSubmit">
            提交审批
          </el-button>
          <el-button size="large" round @click="router.push('/tasks/todo')">取消</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 已办任务查看 -->
    <section v-if="task?.status === 'completed'" class="form-panel">
      <el-empty description="该任务已处理完成">
        <el-button type="primary" round @click="router.push('/tasks/todo')">返回待办</el-button>
      </el-empty>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiSuggestionPanel from '@/components/ai/AiSuggestionPanel.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeTask, getTask, rejectTask as rejectTaskApi } from '@/api/task'
import type { TaskItem } from '@/types/workflow'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const message = ref('')
const successMsg = ref('')
const task = ref<TaskItem | null>(null)

const form = reactive({
  approvalResult: 'agree',
  approvalComment: '',
  rejectReason: ''
})

onMounted(() => loadTask())

async function loadTask() {
  const taskId = route.params.taskId as string
  if (!taskId) {
    message.value = '任务ID无效。'
    return
  }
  loading.value = true
  try {
    task.value = await getTask(taskId)
  } catch (error) {
    message.value = normalizeError(error, '任务详情加载失败。')
  } finally {
    loading.value = false
  }
}

function handleAdoptSuggestion(s: { suggestion: string; reason: string }) {
  if (s.suggestion === 'approve') {
    form.approvalResult = 'agree'
    form.approvalComment = s.reason
  } else if (s.suggestion === 'reject') {
    form.approvalResult = 'reject'
    form.approvalComment = s.reason
  }
  // supplement 不自动填入审批结果
}

async function handleSubmit() {
  if (!task.value) return
  submitting.value = true
  message.value = ''
  successMsg.value = ''
  try {
    if (form.approvalResult === 'reject') {
      // 驳回：调用 rejectTask
      if (!form.approvalComment?.trim()) {
        message.value = '驳回时必须填写审批意见'
        submitting.value = false
        return
      }
      await rejectTaskApi(task.value.taskId, {
        instanceId: task.value.businessInstanceId,
        rejectReason: form.approvalComment
      })
      successMsg.value = '已驳回，流程退回至上一节点。'
    } else {
      // 同意：调用 completeTask
      await completeTask(task.value.taskId, {
        instanceId: task.value.businessInstanceId,
        nodeKey: task.value.taskDefinitionKey,
        formId: task.value.formId ?? null,
        formData: {
          approvalResult: form.approvalResult,
          approvalComment: form.approvalComment
        }
      })
      successMsg.value = '审批通过！流程已流转。'
    }
    setTimeout(() => router.push('/tasks/done'), 1200)
  } catch (error) {
    message.value = normalizeError(error, '审批提交失败。')
  } finally {
    submitting.value = false
  }
}


async function handleReject() {
  if (!task.value) return
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因', '驳回任务', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      type: 'warning' as const,
      inputType: 'textarea',
      inputPlaceholder: '请填写驳回原因...'
    })
    const reason = result?.value
    if (!reason?.trim()) {
      ElMessage.warning('驳回原因不能为空')
      return
    }
    submitting.value = true
    await rejectTaskApi(task.value.taskId, {
      instanceId: task.value.businessInstanceId,
      rejectReason: reason.trim()
    })
    ElMessage.success('已驳回，流程退回至上一节点')
    setTimeout(() => router.push('/tasks/done'), 1000)
  } catch {
    // 用户取消驳回
  } finally {
    submitting.value = false
  }
}

function normalizeError(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  return fallback
}
</script>

<style scoped>
.task-page { display: flex; flex-direction: column; gap: 18px; }
.page-head, .info-panel, .form-panel {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255,255,255,0.94);
  box-shadow: var(--shadow);
  padding: 24px;
}
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h1 { margin: 10px 0 6px; font-size: 28px; }
.page-head p { margin: 0; color: var(--muted); }
.head-actions, .section-title { display: flex; align-items: center; gap: 10px; }
.info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.info-card {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.info-card span { display: block; margin-bottom: 6px; color: var(--muted); font-size: 13px; }
.info-card strong { display: block; overflow-wrap: anywhere; }
.section-title { justify-content: space-between; margin-bottom: 14px; }
.section-title h2 { margin: 0; font-size: 18px; }
.progress-panel {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgba(255,255,255,0.94);
  box-shadow: var(--shadow);
  padding: 24px;
}
.progress-header { margin-bottom: 18px; }
.progress-header h2 { margin: 0 0 6px; font-size: 18px; }
.progress-subtitle { color: var(--muted); font-size: 14px; }
.progress-content { display: flex; flex-direction: column; gap: 16px; }
.progress-numbers { display: flex; gap: 32px; }
.progress-stat { display: flex; flex-direction: column; gap: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--el-text-color-primary); }
.stat-label { font-size: 13px; color: var(--muted); }

@media (max-width: 900px) { .info-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .info-grid { grid-template-columns: 1fr; } }
</style>
