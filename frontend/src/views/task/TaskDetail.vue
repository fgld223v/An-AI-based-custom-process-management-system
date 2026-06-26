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

    <!-- 表单填写节点（form_fill） — 仅 active 状态 -->
    <section v-if="task?.status === 'active' && isFormFill" class="form-panel">
      <div class="section-title">
        <h2>填写表单</h2>
        <span class="section-hint">{{ task?.taskName }}</span>
      </div>
      <DynamicFormRenderer
        v-if="formFields.length > 0"
        v-model="formData"
        :field-list="formFields"
      />
      <el-empty v-else-if="formLoaded" description="表单无字段配置" />
      <div class="form-actions">
        <el-button type="primary" size="large" :loading="submitting" round @click="handleFormFillSubmit">
          提交表单
        </el-button>
        <el-button size="large" round @click="router.push('/tasks/todo')">取消</el-button>
      </div>
    </section>

    <!-- 审批节点（approval） — 仅 active 状态 -->
    <section v-if="task?.status === 'active' && isApproval" class="form-panel">

      <!-- 申请人表单数据（只读） -->
      <div v-if="applicantFormLoaded && applicantFormFields.length > 0" class="applicant-data-section">
        <div class="section-title">
          <h2>申请人填写信息</h2>
          <span class="section-hint">以下为申请人提交的表单数据，请据此做出审批决定</span>
        </div>
        <DynamicFormRenderer
          :field-list="applicantFormFields"
          :model-value="applicantFormData"
          readonly
        />
      </div>
      <el-empty
        v-else-if="applicantFormLoaded && applicantFormFields.length === 0"
        description="未找到申请人表单数据"
      />

      <el-divider v-if="applicantFormLoaded && applicantFormFields.length > 0" />

      <div class="section-title">
        <h2>审批处理</h2>
        <span class="section-hint">{{ task?.taskName }}</span>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiSuggestionPanel from '@/components/ai/AiSuggestionPanel.vue'
import DynamicFormRenderer from '@/components/form/DynamicFormRenderer.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeTask, getTask, rejectTask as rejectTaskApi } from '@/api/task'
import { getFormDetail } from '@/api/formDefinition'
import { getProcessInstanceSubmissions } from '@/api/processInstance'
import type { TaskItem, FormSubmission } from '@/types/workflow'

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

// ---- 表单填写模式 ----
const formData = ref<Record<string, unknown>>({})
const formFields = ref<any[]>([])
const formLoaded = ref(false)

const isFormFill = computed(() => task.value?.businessType === 'form_fill')
const isApproval = computed(() => !task.value?.businessType || task.value.businessType === 'approval')

// ---- 申请人表单数据（审批人查看） ----
const applicantFormData = ref<Record<string, unknown>>({})
const applicantFormFields = ref<any[]>([])
const applicantFormLoaded = ref(false)

async function loadFormDefinition() {
  if (!task.value?.formId) {
    formLoaded.value = true
    return
  }
  try {
    const def = await getFormDetail(task.value.formId)
    // 解析 fieldList
    const raw = def.fieldList
    if (typeof raw === 'string') {
      formFields.value = JSON.parse(raw)
    } else if (Array.isArray(raw)) {
      formFields.value = raw
    }
  } catch { /* ignore */ }
  formLoaded.value = true
}

/** 加载申请人填写的表单数据，供审批人查看 */
async function loadApplicantFormData() {
  if (!task.value?.businessInstanceId) {
    applicantFormLoaded.value = true
    return
  }
  try {
    const submissions = await getProcessInstanceSubmissions(task.value.businessInstanceId)
    const bizSubmission = findBusinessSubmission(submissions)
    if (bizSubmission?.formDataJson) {
      applicantFormData.value = JSON.parse(bizSubmission.formDataJson)
      if (bizSubmission.formId) {
        try {
          const def = await getFormDetail(bizSubmission.formId)
          const raw = def.fieldList
          applicantFormFields.value = typeof raw === 'string' ? JSON.parse(raw)
            : Array.isArray(raw) ? raw : []
        } catch { /* ignore */ }
      }
      if (applicantFormFields.value.length === 0 && Object.keys(applicantFormData.value).length > 0) {
        applicantFormFields.value = Object.keys(applicantFormData.value).map(key => ({
          field: key, label: key, type: 'input'
        }))
      }
    }
  } catch { /* ignore */ }
  applicantFormLoaded.value = true
}

function findBusinessSubmission(submissions: FormSubmission[]): FormSubmission | null {
  const approvalKeys = ['approvalResult', 'approvalComment', 'approvalOpinion',
    'approved', 'rejected', 'operatedAt', 'automatic', 'automaticReason']
  for (const sub of submissions) {
    if (sub.businessType === 'start' || sub.businessType === 'form_fill') {
      if (sub.formDataJson) {
        try {
          const data = JSON.parse(sub.formDataJson)
          if (Object.keys(data).some(k => !approvalKeys.includes(k))) return sub
        } catch { /* ignore */ }
      }
    }
  }
  for (const sub of submissions) {
    if (sub.formDataJson) {
      try {
        const data = JSON.parse(sub.formDataJson)
        if (Object.keys(data).some(k => !approvalKeys.includes(k))) return sub
      } catch { /* ignore */ }
    }
  }
  return null
}

watch(() => task.value?.formId, () => {
  if (isFormFill.value) loadFormDefinition()
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
    if (isFormFill.value) await loadFormDefinition()
    if (isApproval.value) await loadApplicantFormData()
  } catch (error) {
    message.value = normalizeError(error, '任务详情加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleFormFillSubmit() {
  if (!task.value) return
  submitting.value = true
  message.value = ''
  successMsg.value = ''
  try {
    await completeTask(task.value.taskId, {
      instanceId: task.value.businessInstanceId,
      nodeKey: task.value.taskDefinitionKey,
      formId: task.value.formId ?? null,
      formData: formData.value
    })
    successMsg.value = '表单已提交！流程已流转。'
    setTimeout(() => router.push('/tasks/done'), 1200)
  } catch (error) {
    message.value = normalizeError(error, '表单提交失败。')
  } finally {
    submitting.value = false
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
.section-hint { color: var(--muted); font-size: 13px; }
.applicant-data-section {
  margin-bottom: 8px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
.applicant-data-section .section-title { margin-bottom: 12px; }
.form-actions { display: flex; gap: 12px; margin-top: 20px; }
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
