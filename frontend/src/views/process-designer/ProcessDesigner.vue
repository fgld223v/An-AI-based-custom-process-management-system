<template>
  <div class="process-designer-page">
    <main class="process-workspace">
      <section class="process-header-card">
        <div>
          <el-tag type="success" effect="plain">在线</el-tag>
          <el-tag effect="plain">v1.0</el-tag>
          <el-tag effect="plain">节点 {{ elementCount }}</el-tag>
          <el-tag type="warning" effect="plain">提示 0</el-tag>
          <el-tag effect="plain">{{ zoomPercent }}%</el-tag>
          <h1>流程设计器</h1>
          <p>使用 bpmn.js 原生工具栏绘制流程，也可以点击常用业务节点快速创建。</p>
        </div>
        <div class="process-actions">
          <el-button round :icon="Refresh" @click="loadDefaultDiagram">默认流程</el-button>
          <el-button round :icon="Upload" @click="importVisible = true">回显 XML</el-button>
          <el-button round :icon="Download" @click="exportXml">导出 XML</el-button>
          <el-button round type="success" :icon="Check" @click="saveXml">保存</el-button>
        </div>
      </section>

      <section class="quick-node-card">
        <div class="quick-node-title">
          <strong>常用业务节点</strong>
          <span>用业务语言创建 BPMN 元素，配置会保存在前端状态中</span>
        </div>
        <div class="quick-node-list">
          <el-button
            v-for="node in BUSINESS_NODE_CONFIGS"
            :key="node.businessType"
            round
            :icon="node.icon"
            @click="createBusinessNode(node)"
          >
            {{ node.label }}
          </el-button>
        </div>
      </section>

      <section class="bpmn-shell">
        <div ref="canvasRef" class="bpmn-canvas"></div>
        <div class="minimap-card">
          <div class="minimap-title">Mini map</div>
          <div class="minimap-track">
            <span></span>
            <i></i>
          </div>
        </div>
      </section>
    </main>

    <aside class="process-property-panel">
      <div class="designer-panel-head">
        <span>节点属性</span>
        <small>{{ selectedConfig ? getBusinessLabel(selectedConfig.businessType) : '未选择' }}</small>
      </div>

      <el-empty v-if="!selectedElement || !selectedConfig" description="点击画布节点查看配置" />
      <el-form v-else label-position="top" class="property-form">
        <el-form-item label="节点ID">
          <el-input :model-value="selectedConfig.nodeId" disabled />
        </el-form-item>
        <el-form-item label="BPMN 类型">
          <el-input :model-value="selectedConfig.bpmnType" disabled />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input :model-value="getBusinessLabel(selectedConfig.businessType)" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="selectedConfig.nodeName" placeholder="请输入节点名称" @change="updateSelectedName" />
        </el-form-item>

        <template v-if="selectedConfig.businessType === 'start'">
          <el-form-item label="发起方式">
            <el-select v-model="selectedConfig.startMode" @change="syncNodeConfig">
              <el-option label="手动发起" value="MANUAL" />
              <el-option label="表单提交触发" value="FORM_SUBMIT" />
              <el-option label="定时触发" value="TIMER" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起权限">
            <el-select v-model="selectedConfig.startPermission" @change="syncNodeConfig">
              <el-option label="所有人" value="ALL" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="指定部门" value="DEPARTMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联申请表单">
            <el-input v-model="selectedConfig.formId" placeholder="例如 leave_apply_form" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否需要登录">
            <el-switch v-model="selectedConfig.loginRequired" active-text="需要" inactive-text="不需要" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'form_fill'">
          <el-form-item label="绑定表单">
            <el-input v-model="selectedConfig.formId" placeholder="只保存表单ID或编码" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="表单模式">
            <el-select v-model="selectedConfig.formMode" @change="syncNodeConfig">
              <el-option label="新建填写" value="CREATE" />
              <el-option label="补充填写" value="SUPPLEMENT" />
              <el-option label="修改已有数据" value="EDIT_EXISTING" />
            </el-select>
          </el-form-item>
          <el-form-item label="可编辑字段">
            <el-input
              :model-value="selectedConfig.editableFields.join(',')"
              placeholder="多个字段用英文逗号分隔"
              @change="updateListField('editableFields', $event)"
            />
          </el-form-item>
          <el-form-item label="必填校验">
            <el-input
              :model-value="selectedConfig.requiredFields.join(',')"
              placeholder="多个字段用英文逗号分隔"
              @change="updateListField('requiredFields', $event)"
            />
          </el-form-item>
          <el-form-item label="是否允许上传附件">
            <el-switch v-model="selectedConfig.attachmentAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否允许暂存草稿">
            <el-switch v-model="selectedConfig.draftAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="提交按钮文案">
            <el-input v-model="selectedConfig.submitButtonText" placeholder="例如 提交申请" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'approval'">
          <el-form-item label="审批人类型">
            <el-select v-model="selectedConfig.assigneeType" @change="syncNodeConfig">
              <el-option label="指定用户" value="USER" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="发起人主管" value="MANAGER" />
              <el-option label="部门负责人" value="DEPT_LEADER" />
            </el-select>
          </el-form-item>
          <el-form-item label="审批人或角色">
            <el-input v-model="selectedConfig.assigneeValue" placeholder="用户ID、角色编码或部门编码" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="审批方式">
            <el-select v-model="selectedConfig.approvalMode" @change="syncNodeConfig">
              <el-option label="单人审批" value="SINGLE" />
              <el-option label="会签" value="ALL" />
              <el-option label="或签" value="ANY" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否允许转交">
            <el-switch v-model="selectedConfig.transferAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否允许加签">
            <el-switch v-model="selectedConfig.addSignAllowed" active-text="允许" inactive-text="不允许" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="审批意见是否必填">
            <el-switch v-model="selectedConfig.commentRequired" active-text="必填" inactive-text="选填" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="超时设置">
            <el-input v-model="selectedConfig.timeoutConfig.remindAfter" placeholder="例如 24h 后提醒" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="驳回规则">
            <el-select v-model="selectedConfig.rejectRule" @change="syncNodeConfig">
              <el-option label="退回申请人" value="TO_APPLICANT" />
              <el-option label="退回上一节点" value="TO_PREVIOUS" />
              <el-option label="直接结束流程" value="END_PROCESS" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'condition'">
          <el-form-item label="判断字段">
            <el-input v-model="selectedConfig.conditionField" placeholder="例如 leaveDays / amount / approvalResult" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="条件表达式">
            <el-input
              v-model="selectedConfig.conditionExpression"
              type="textarea"
              :rows="3"
              placeholder="例如 请假天数 > 3 / 报销金额 >= 10000 / 审批结果 == 同意"
              @change="syncNodeConfig"
            />
          </el-form-item>
          <el-form-item label="默认分支">
            <el-input v-model="selectedConfig.defaultFlow" placeholder="默认连线ID" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="分支说明">
            <el-input v-model="selectedConfig.branchDescription" type="textarea" :rows="2" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'parallel'">
          <el-form-item label="并行说明">
            <el-input v-model="selectedConfig.parallelDescription" type="textarea" :rows="2" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="等待策略">
            <el-select v-model="selectedConfig.waitStrategy" @change="syncNodeConfig">
              <el-option label="全部完成后继续" value="ALL_COMPLETED" />
              <el-option label="任一完成后继续" value="ANY_COMPLETED" />
            </el-select>
          </el-form-item>
          <el-form-item label="分支完成条件">
            <el-input v-model="selectedConfig.branchCompletionCondition" placeholder="例如 所有审批人已处理" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="异常分支处理">
            <el-select v-model="selectedConfig.exceptionStrategy" @change="syncNodeConfig">
              <el-option label="等待人工处理" value="MANUAL" />
              <el-option label="跳过异常分支" value="SKIP" />
              <el-option label="终止流程" value="TERMINATE" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'notify'">
          <el-form-item label="通知对象">
            <el-select v-model="selectedConfig.notifyTarget" @change="syncNodeConfig">
              <el-option label="申请人" value="APPLICANT" />
              <el-option label="审批人" value="APPROVER" />
              <el-option label="指定用户" value="USER" />
              <el-option label="指定角色" value="ROLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知渠道">
            <el-select v-model="selectedConfig.notifyChannel" @change="syncNodeConfig">
              <el-option label="站内信" value="IN_APP" />
              <el-option label="邮件" value="EMAIL" />
              <el-option label="短信" value="SMS" />
              <el-option label="企业微信" value="WE_COM" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知模板">
            <el-input v-model="selectedConfig.notifyTemplate" placeholder="模板编码或模板名称" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="发送时机">
            <el-select v-model="selectedConfig.notifyTiming" @change="syncNodeConfig">
              <el-option label="进入节点时" value="ON_ENTER" />
              <el-option label="流程完成时" value="ON_COMPLETE" />
              <el-option label="流程驳回时" value="ON_REJECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否记录通知日志">
            <el-switch v-model="selectedConfig.notifyLogEnabled" active-text="记录" inactive-text="不记录" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'system_action'">
          <el-form-item label="动作类型">
            <el-select v-model="selectedConfig.actionType" @change="syncNodeConfig">
              <el-option label="调用接口" value="HTTP" />
              <el-option label="写入数据库" value="DB_WRITE" />
              <el-option label="生成编号" value="GENERATE_CODE" />
              <el-option label="创建工单" value="CREATE_TICKET" />
              <el-option label="Webhook" value="WEBHOOK" />
            </el-select>
          </el-form-item>
          <el-form-item label="接口地址或动作标识">
            <el-input v-model="selectedConfig.apiUrl" placeholder="例如 /api/work-order/create" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="请求方式">
            <el-select v-model="selectedConfig.requestMethod" @change="syncNodeConfig">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="参数映射">
            <el-input v-model="selectedConfig.parameterMapping" type="textarea" :rows="3" placeholder="{ applicant: form.applicant }" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="失败处理">
            <el-select v-model="selectedConfig.failureStrategy" @change="syncNodeConfig">
              <el-option label="重试" value="RETRY" />
              <el-option label="跳过" value="SKIP" />
              <el-option label="转人工" value="TO_MANUAL" />
              <el-option label="终止流程" value="TERMINATE" />
            </el-select>
          </el-form-item>
          <el-form-item label="最大重试次数">
            <el-input-number v-model="selectedConfig.retryCount" :min="0" :max="10" controls-position="right" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else-if="selectedConfig.businessType === 'end'">
          <el-form-item label="结束状态">
            <el-select v-model="selectedConfig.endStatus" @change="syncNodeConfig">
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已取消" value="CANCELED" />
              <el-option label="异常结束" value="ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否发送完成通知">
            <el-switch v-model="selectedConfig.completionNotifyEnabled" active-text="发送" inactive-text="不发送" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否归档流程数据">
            <el-switch v-model="selectedConfig.archiveEnabled" active-text="归档" inactive-text="不归档" @change="syncNodeConfig" />
          </el-form-item>
          <el-form-item label="是否锁定表单数据">
            <el-switch v-model="selectedConfig.lockFormData" active-text="锁定" inactive-text="不锁定" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="业务备注">
            <el-input v-model="selectedConfig.remark" type="textarea" :rows="3" placeholder="预留配置" @change="syncNodeConfig" />
          </el-form-item>
        </template>

        <div class="node-config-preview">
          <span>预留保存结构</span>
          <pre>{{ JSON.stringify(selectedConfig, null, 2) }}</pre>
        </div>

        <div class="property-footer">
          <el-button round @click="showComingSoon">复制</el-button>
          <el-button round type="danger" plain @click="deleteSelected">删除</el-button>
          <el-button round type="success" @click="saveXml">保存</el-button>
        </div>
      </el-form>
    </aside>

    <el-dialog v-model="xmlVisible" title="当前 BPMN XML" width="760px">
      <el-input v-model="xmlText" type="textarea" :rows="18" />
      <template #footer>
        <el-button round @click="copyXml">复制 XML</el-button>
        <el-button round type="success" @click="xmlVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="从 BPMN XML 回显" width="760px">
      <el-input v-model="importXmlText" type="textarea" :rows="18" placeholder="粘贴已有 BPMN XML" />
      <template #footer>
        <el-button round @click="importVisible = false">取消</el-button>
        <el-button round type="success" @click="importFromText">导入并回显</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Check,
  Download,
  EditPen,
  Finished,
  Operation,
  Refresh,
  Share,
  Switch,
  Upload,
  VideoPlay
} from '@element-plus/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { showComingSoon } from '@/utils/feedback'

type BusinessType =
  | 'start'
  | 'form_fill'
  | 'approval'
  | 'condition'
  | 'parallel'
  | 'notify'
  | 'system_action'
  | 'end'
  | 'generic_task'

interface BpmnElement {
  id: string
  type: string
  businessObject?: {
    name?: string
  }
}

interface NodeBusinessConfig {
  nodeId: string
  bpmnType: string
  businessType: BusinessType
  nodeName: string
  formId: string
  editableFields: string[]
  requiredFields: string[]
  assigneeType: string
  assigneeValue: string
  approvalMode: string
  rejectRule: string
  conditionField: string
  conditionExpression: string
  defaultFlow: string
  notifyTarget: string
  notifyChannel: string
  notifyTemplate: string
  notifyTiming: string
  notifyLogEnabled: boolean
  actionType: string
  apiUrl: string
  requestMethod: string
  parameterMapping: string
  retryCount: number
  timeoutConfig: {
    remindAfter: string
    autoAction: string
  }
  endStatus: string
  startMode: string
  startPermission: string
  loginRequired: boolean
  formMode: string
  attachmentAllowed: boolean
  draftAllowed: boolean
  submitButtonText: string
  transferAllowed: boolean
  addSignAllowed: boolean
  commentRequired: boolean
  branchDescription: string
  parallelDescription: string
  waitStrategy: string
  branchCompletionCondition: string
  exceptionStrategy: string
  failureStrategy: string
  completionNotifyEnabled: boolean
  archiveEnabled: boolean
  lockFormData: boolean
  remark: string
}

interface BusinessNodeConfig {
  label: string
  businessType: BusinessType
  bpmnType: string
  defaultName: string
  icon: object
}

const BUSINESS_NODE_CONFIGS: BusinessNodeConfig[] = [
  { label: '流程开始', businessType: 'start', bpmnType: 'bpmn:StartEvent', defaultName: '流程开始', icon: VideoPlay },
  { label: '申请填写', businessType: 'form_fill', bpmnType: 'bpmn:UserTask', defaultName: '填写申请', icon: EditPen },
  { label: '审批处理', businessType: 'approval', bpmnType: 'bpmn:UserTask', defaultName: '审批处理', icon: Check },
  { label: '条件分支', businessType: 'condition', bpmnType: 'bpmn:ExclusiveGateway', defaultName: '条件判断', icon: Switch },
  { label: '并行处理', businessType: 'parallel', bpmnType: 'bpmn:ParallelGateway', defaultName: '并行处理', icon: Share },
  { label: '抄送通知', businessType: 'notify', bpmnType: 'bpmn:SendTask', defaultName: '抄送通知', icon: Bell },
  { label: '系统动作', businessType: 'system_action', bpmnType: 'bpmn:ServiceTask', defaultName: '系统处理', icon: Operation },
  { label: '流程结束', businessType: 'end', bpmnType: 'bpmn:EndEvent', defaultName: '流程结束', icon: Finished }
]

const PALETTE_TOOLTIPS = [
  { selectors: ['[data-action="hand-tool"]', '.bpmn-icon-hand-tool'], text: '拖动画布' },
  { selectors: ['[data-action="lasso-tool"]', '.bpmn-icon-lasso-tool'], text: '框选多个节点' },
  { selectors: ['[data-action="space-tool"]', '.bpmn-icon-space-tool'], text: '调整流程间距' },
  { selectors: ['[data-action="global-connect-tool"]', '.bpmn-icon-connection-multi'], text: '快速连接节点' },
  { selectors: ['[data-action="create.start-event"]', '.bpmn-icon-start-event-none'], text: '开始事件，表示流程开始' },
  { selectors: ['[data-action="create.intermediate-event"]', '.bpmn-icon-intermediate-event-none'], text: '中间事件，表示流程过程中的等待、消息或时间触发' },
  { selectors: ['[data-action="create.end-event"]', '.bpmn-icon-end-event-none'], text: '结束事件，表示流程结束' },
  { selectors: ['[data-action="create.exclusive-gateway"]', '.bpmn-icon-gateway-xor'], text: '排他网关，用于条件判断，只会进入一个分支' },
  { selectors: ['[data-action="create.parallel-gateway"]', '.bpmn-icon-gateway-parallel'], text: '并行网关，用于多个分支同时执行' },
  { selectors: ['[data-action="create.task"]', '.bpmn-icon-task'], text: '普通任务，表示一个待处理工作' },
  { selectors: ['[data-action="create.user-task"]', '.bpmn-icon-user-task'], text: '人工任务，表示需要用户填写、审批或处理' },
  { selectors: ['[data-action="create.service-task"]', '.bpmn-icon-service-task'], text: '服务任务，表示系统自动执行接口、脚本或后台操作' },
  { selectors: ['[data-action="create.data-object"]', '.bpmn-icon-data-object'], text: '数据对象，表示流程中使用的业务数据或表单数据' },
  { selectors: ['[data-action="create.data-store"]', '.bpmn-icon-data-store'], text: '数据存储，表示外部数据库、文件或业务系统' },
  { selectors: ['[data-action="create.group"]', '.bpmn-icon-group'], text: '分组，用于视觉上归类一组节点' },
  { selectors: ['[data-action="create.subprocess-expanded"]', '.bpmn-icon-subprocess-expanded'], text: '子流程，用于封装一段可复用流程' }
]

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  save: [value: string]
  change: [value: string]
}>()

const canvasRef = ref<HTMLDivElement>()
const modeler = ref<any>()
const selectedElement = ref<BpmnElement | null>(null)
const xmlVisible = ref(false)
const importVisible = ref(false)
const xmlText = ref('')
const importXmlText = ref('')
const elementCount = ref(0)
const zoomPercent = ref(100)
const nodeConfigMap = reactive<Record<string, NodeBusinessConfig>>({})
const currentXml = ref(props.modelValue || defaultBpmnXml())

const selectedConfig = computed(() => {
  if (!selectedElement.value) return null
  return ensureNodeConfig(selectedElement.value)
})

watch(
  () => props.modelValue,
  async (value) => {
    if (!value || value === currentXml.value || !modeler.value) return
    await importDiagram(value)
  }
)

onMounted(async () => {
  await nextTick()
  modeler.value = new BpmnModeler({
    container: canvasRef.value
  })
  bindModelerEvents()
  await importDiagram(currentXml.value)
})

onBeforeUnmount(() => {
  modeler.value?.destroy()
})

function bindModelerEvents() {
  const eventBus = modeler.value.get('eventBus')
  eventBus.on('selection.changed', (event: { newSelection: BpmnElement[] }) => {
    selectedElement.value = event.newSelection[0] || null
    if (selectedElement.value) {
      ensureNodeConfig(selectedElement.value)
    }
  })
  eventBus.on('commandStack.changed', async () => {
    await syncXml(false)
    refreshStats()
    addPaletteTooltips()
  })
  eventBus.on('canvas.viewbox.changed', () => {
    const canvas = modeler.value.get('canvas')
    zoomPercent.value = Math.round(canvas.zoom() * 100)
  })
}

async function importDiagram(xml: string) {
  try {
    await modeler.value.importXML(xml)
    const canvas = modeler.value.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
    currentXml.value = xml
    importXmlText.value = xml
    selectedElement.value = null
    await syncXml(false)
    refreshStats()
    setTimeout(addPaletteTooltips, 0)
    ElMessage.success('流程图已回显')
  } catch (error) {
    ElMessage.error('BPMN XML 导入失败')
    console.error(error)
  }
}

async function loadDefaultDiagram() {
  await importDiagram(defaultBpmnXml())
}

async function exportXml() {
  await syncXml(false)
  xmlText.value = currentXml.value
  xmlVisible.value = true
}

async function saveXml() {
  await syncXml(true)
  emit('save', currentXml.value)
  ElMessage.success('bpmnXml 已保存并回传给父组件')
}

async function syncXml(emitChange: boolean) {
  if (!modeler.value) return
  const result = await modeler.value.saveXML({ format: true })
  currentXml.value = result.xml || ''
  emit('update:modelValue', currentXml.value)
  if (emitChange) {
    emit('change', currentXml.value)
  }
}

async function importFromText() {
  if (!importXmlText.value.trim()) {
    ElMessage.warning('请先粘贴 BPMN XML')
    return
  }
  await importDiagram(importXmlText.value)
  importVisible.value = false
}

async function copyXml() {
  await navigator.clipboard.writeText(xmlText.value)
  ElMessage.success('BPMN XML 已复制')
}

function createBusinessNode(node: BusinessNodeConfig) {
  if (!modeler.value) return
  const elementFactory = modeler.value.get('elementFactory')
  const modeling = modeler.value.get('modeling')
  const canvas = modeler.value.get('canvas')
  const selection = modeler.value.get('selection')
  const rootElement = canvas.getRootElement()
  const viewbox = canvas.viewbox()
  const position = {
    x: viewbox.x + viewbox.width / 2 - 50 + Math.random() * 80,
    y: viewbox.y + viewbox.height / 2 - 30 + Math.random() * 60
  }
  const shape = elementFactory.createShape({ type: node.bpmnType })
  const created = modeling.createShape(shape, position, rootElement)
  modeling.updateProperties(created, { name: node.defaultName })
  ensureNodeConfig(created, node.defaultName, node.businessType)
  selection.select(created)
  selectedElement.value = created
  ElMessage.success(`已创建：${node.label}`)
}

function ensureNodeConfig(element: BpmnElement, defaultName?: string, businessType?: BusinessType) {
  if (!nodeConfigMap[element.id]) {
    const inferredBusinessType = businessType || inferBusinessType(element)
    const nodeName = defaultName || element.businessObject?.name || getDefaultNodeName(inferredBusinessType, element.type)
    nodeConfigMap[element.id] = createDefaultNodeConfig(element, inferredBusinessType, nodeName)
  }
  return nodeConfigMap[element.id]
}

function createDefaultNodeConfig(element: BpmnElement, businessType: BusinessType, nodeName: string): NodeBusinessConfig {
  const base: NodeBusinessConfig = {
    nodeId: element.id,
    bpmnType: element.type,
    businessType,
    nodeName,
    formId: '',
    editableFields: [],
    requiredFields: [],
    assigneeType: 'ROLE',
    assigneeValue: '',
    approvalMode: 'SINGLE',
    rejectRule: 'TO_APPLICANT',
    conditionField: '',
    conditionExpression: '',
    defaultFlow: '',
    notifyTarget: 'APPLICANT',
    notifyChannel: 'IN_APP',
    notifyTemplate: '',
    notifyTiming: 'ON_ENTER',
    notifyLogEnabled: true,
    actionType: 'HTTP',
    apiUrl: '',
    requestMethod: 'POST',
    parameterMapping: '',
    retryCount: 0,
    timeoutConfig: {
      remindAfter: '',
      autoAction: ''
    },
    endStatus: 'COMPLETED',
    startMode: 'MANUAL',
    startPermission: 'ALL',
    loginRequired: true,
    formMode: 'CREATE',
    attachmentAllowed: true,
    draftAllowed: true,
    submitButtonText: '提交申请',
    transferAllowed: false,
    addSignAllowed: false,
    commentRequired: true,
    branchDescription: '',
    parallelDescription: '',
    waitStrategy: 'ALL_COMPLETED',
    branchCompletionCondition: '',
    exceptionStrategy: 'MANUAL',
    failureStrategy: 'RETRY',
    completionNotifyEnabled: true,
    archiveEnabled: true,
    lockFormData: true,
    remark: ''
  }

  if (businessType === 'approval') {
    base.assigneeType = 'MANAGER'
    base.submitButtonText = ''
  }
  if (businessType === 'notify') {
    base.actionType = 'NOTIFY'
    base.notifyTiming = 'ON_COMPLETE'
  }
  if (businessType === 'system_action') {
    base.actionType = 'HTTP'
    base.failureStrategy = 'RETRY'
    base.retryCount = 3
  }
  return base
}

function inferBusinessType(element: BpmnElement): BusinessType {
  const typeMap: Record<string, BusinessType> = {
    'bpmn:StartEvent': 'start',
    'bpmn:UserTask': 'approval',
    'bpmn:Task': 'generic_task',
    'bpmn:ServiceTask': 'system_action',
    'bpmn:SendTask': 'notify',
    'bpmn:ExclusiveGateway': 'condition',
    'bpmn:ParallelGateway': 'parallel',
    'bpmn:EndEvent': 'end'
  }
  return typeMap[element.type] || 'generic_task'
}

function getDefaultNodeName(businessType: BusinessType, bpmnType: string) {
  const config = BUSINESS_NODE_CONFIGS.find((item) => item.businessType === businessType)
  if (config) return config.defaultName
  const names: Record<string, string> = {
    'bpmn:Task': '普通任务',
    'bpmn:IntermediateThrowEvent': '中间事件',
    'bpmn:DataObjectReference': '数据对象',
    'bpmn:DataStoreReference': '数据存储',
    'bpmn:Group': '分组',
    'bpmn:SubProcess': '子流程'
  }
  return names[bpmnType] || '流程节点'
}

function getBusinessLabel(businessType: BusinessType) {
  const label = BUSINESS_NODE_CONFIGS.find((item) => item.businessType === businessType)?.label
  return label || '普通任务'
}

function updateSelectedName() {
  if (!selectedElement.value || !modeler.value || !selectedConfig.value) return
  const modeling = modeler.value.get('modeling')
  modeling.updateProperties(selectedElement.value, { name: selectedConfig.value.nodeName })
  syncNodeConfig()
}

function updateListField(field: 'editableFields' | 'requiredFields', value: string | number | boolean) {
  if (!selectedConfig.value) return
  selectedConfig.value[field] = String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  syncNodeConfig()
}

function syncNodeConfig() {
  void syncXml(false)
}

function deleteSelected() {
  if (!selectedElement.value || !modeler.value) return
  const modeling = modeler.value.get('modeling')
  const nodeId = selectedElement.value.id
  modeling.removeElements([selectedElement.value])
  delete nodeConfigMap[nodeId]
  selectedElement.value = null
}

function refreshStats() {
  if (!modeler.value) return
  const elementRegistry = modeler.value.get('elementRegistry')
  elementCount.value = elementRegistry.filter((element: BpmnElement) => !element.type.includes('Label')).length
}

function addPaletteTooltips() {
  if (!canvasRef.value) return
  const entries = canvasRef.value.querySelectorAll('.djs-palette .entry')
  entries.forEach((entry) => {
    const tooltip = findPaletteTooltip(entry as HTMLElement)
    entry.setAttribute('title', tooltip)
    entry.setAttribute('aria-label', tooltip)
  })
}

function findPaletteTooltip(entry: HTMLElement) {
  for (const item of PALETTE_TOOLTIPS) {
    if (item.selectors.some((selector) => entry.matches(selector) || Boolean(entry.querySelector(selector)))) {
      return item.text
    }
  }
  return 'BPMN 建模工具'
}

function defaultBpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_AI_Flow" targetNamespace="http://ai-flow/process">
  <bpmn:process id="Process_Leave_Approval" name="员工请假审批流程" isExecutable="false">
    <bpmn:startEvent id="StartEvent_Apply" name="提交申请">
      <bpmn:outgoing>Flow_Start_To_Task</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="UserTask_ManagerApprove" name="主管审批">
      <bpmn:incoming>Flow_Start_To_Task</bpmn:incoming>
      <bpmn:outgoing>Flow_Task_To_Gateway</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_Approved" name="是否通过">
      <bpmn:incoming>Flow_Task_To_Gateway</bpmn:incoming>
      <bpmn:outgoing>Flow_Gateway_To_End</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_Done" name="流程结束">
      <bpmn:incoming>Flow_Gateway_To_End</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Start_To_Task" sourceRef="StartEvent_Apply" targetRef="UserTask_ManagerApprove" />
    <bpmn:sequenceFlow id="Flow_Task_To_Gateway" sourceRef="UserTask_ManagerApprove" targetRef="Gateway_Approved" />
    <bpmn:sequenceFlow id="Flow_Gateway_To_End" sourceRef="Gateway_Approved" targetRef="EndEvent_Done" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_AI_Flow">
    <bpmndi:BPMNPlane id="BPMNPlane_AI_Flow" bpmnElement="Process_Leave_Approval">
      <bpmndi:BPMNShape id="StartEvent_Apply_di" bpmnElement="StartEvent_Apply">
        <dc:Bounds x="180" y="180" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="174" y="223" width="48" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_ManagerApprove_di" bpmnElement="UserTask_ManagerApprove">
        <dc:Bounds x="280" y="158" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Approved_di" bpmnElement="Gateway_Approved" isMarkerVisible="true">
        <dc:Bounds x="470" y="173" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="470" y="230" width="50" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Done_di" bpmnElement="EndEvent_Done">
        <dc:Bounds x="590" y="180" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="584" y="223" width="48" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_To_Task_di" bpmnElement="Flow_Start_To_Task">
        <di:waypoint x="216" y="198" />
        <di:waypoint x="280" y="198" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Task_To_Gateway_di" bpmnElement="Flow_Task_To_Gateway">
        <di:waypoint x="400" y="198" />
        <di:waypoint x="470" y="198" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Gateway_To_End_di" bpmnElement="Flow_Gateway_To_End">
        <di:waypoint x="520" y="198" />
        <di:waypoint x="590" y="198" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}
</script>
