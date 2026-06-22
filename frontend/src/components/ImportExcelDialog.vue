<template>
  <el-dialog v-model="visible" :title="title" width="520px" destroy-on-close @close="reset">
    <div class="import-dialog-body">
      <div class="import-step">
        <p class="step-label">1. 下载 Excel 模板</p>
        <el-button type="primary" plain round :icon="Download" @click="handleTemplate">
          下载模板
        </el-button>
      </div>

      <el-divider />

      <div class="import-step">
        <p class="step-label">2. 填写数据后上传</p>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleRemove"
          drag
        >
          <el-icon :size="40"><UploadFilled /></el-icon>
          <div class="upload-text">
            <p>将 Excel 文件拖到此处，或<em>点击选择</em></p>
            <p class="upload-hint">支持 .xlsx / .xls 格式</p>
          </div>
        </el-upload>
      </div>
    </div>

    <template #footer>
      <el-button round @click="visible = false">取消</el-button>
      <el-button round type="primary" :loading="importing" :disabled="!uploadFile" @click="handleImport">
        开始导入
      </el-button>
    </template>

    <!-- 导入结果 -->
    <el-dialog v-model="resultVisible" title="导入结果" width="460px" append-to-body>
      <div class="result-summary">
        <div class="result-stat">
          <span class="result-num">{{ result.total }}</span>
          <span class="result-label">总计</span>
        </div>
        <div class="result-stat success">
          <span class="result-num">{{ result.success }}</span>
          <span class="result-label">成功</span>
        </div>
        <div class="result-stat failed" v-if="result.failed > 0">
          <span class="result-num">{{ result.failed }}</span>
          <span class="result-label">失败</span>
        </div>
      </div>
      <div v-if="result.errors && result.errors.length > 0" class="result-errors">
        <p v-for="(err, idx) in result.errors" :key="idx" class="error-item">
          <el-tag type="danger" size="small" effect="plain">第 {{ err.row }} 行</el-tag>
          {{ err.reason }}
        </p>
      </div>
      <template #footer>
        <el-button round type="primary" @click="resultVisible = false; onImportDone()">完成</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { downloadBlob, uploadExcel } from '@/utils/download'
import type { ImportResult } from '@/api/admin'
import type { UploadFile, UploadInstance } from 'element-plus'

const props = defineProps<{
  title: string
  templateUrl: string
  importUrl: string
}>()

const emit = defineEmits<{
  imported: []
}>()

const visible = defineModel<boolean>('visible', { required: true })

const uploadRef = ref<UploadInstance>()
const uploadFile = ref<File | null>(null)
const importing = ref(false)
const resultVisible = ref(false)
const result = ref<ImportResult>({ total: 0, success: 0, failed: 0, errors: [] })

function handleTemplate() {
  const filename = props.templateUrl.includes('departments') ? '部门导入模板.xlsx' : '用户导入模板.xlsx'
  downloadBlob(props.templateUrl, filename)
}

function handleFileChange(file: UploadFile) {
  uploadFile.value = file.raw || null
}

function handleRemove() {
  uploadFile.value = null
}

async function handleImport() {
  if (!uploadFile.value) return
  importing.value = true
  try {
    result.value = await uploadExcel(props.importUrl, uploadFile.value)
    if (result.value.failed === 0) {
      ElMessage.success(`全部导入成功，共 ${result.value.success} 条`)
    } else {
      ElMessage.warning(`导入完成：成功 ${result.value.success} 条，失败 ${result.value.failed} 条`)
    }
    resultVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function onImportDone() {
  visible.value = false
  reset()
  emit('imported')
}

function reset() {
  uploadFile.value = null
  result.value = { total: 0, success: 0, failed: 0, errors: [] }
}
</script>

<style scoped>
.import-dialog-body {
  padding: 8px 0;
}

.import-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.step-label {
  font-size: 14px;
  color: #555;
}

.upload-text {
  text-align: center;
}

.upload-text p {
  margin: 4px 0;
}

.upload-text em {
  color: var(--primary);
  font-style: normal;
}

.upload-hint {
  color: var(--muted);
  font-size: 12px;
}

.result-summary {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-bottom: 16px;
}

.result-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.result-num {
  font-size: 28px;
  font-weight: 700;
}

.result-stat.success .result-num { color: #67c23a; }
.result-stat.failed .result-num { color: #f56c6c; }

.result-label {
  font-size: 12px;
  color: #999;
}

.result-errors {
  max-height: 200px;
  overflow-y: auto;
  background: #fef0f0;
  border-radius: 8px;
  padding: 12px;
}

.error-item {
  margin: 6px 0;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
