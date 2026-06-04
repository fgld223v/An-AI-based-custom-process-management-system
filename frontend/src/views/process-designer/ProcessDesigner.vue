<template>
  <div class="process-designer-page">
    <aside class="node-toolbar">
      <button v-for="item in nodeTools" :key="item.label" type="button" @click="showToolHint(item.label)">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </aside>

    <main class="process-workspace">
      <section class="process-header-card">
        <div>
          <el-tag type="success" effect="plain">在线</el-tag>
          <el-tag effect="plain">v1.0</el-tag>
          <el-tag effect="plain">节点 {{ elementCount }}</el-tag>
          <el-tag type="warning" effect="plain">提示 0</el-tag>
          <el-tag effect="plain">{{ zoomPercent }}%</el-tag>
          <h1>流程设计器</h1>
          <p>使用 bpmn.js Modeler 绘制基础 BPMN 流程，支持导出 XML、导入 XML 和保存回传。</p>
        </div>
        <div class="process-actions">
          <el-button round :icon="Refresh" @click="loadDefaultDiagram">默认流程</el-button>
          <el-button round :icon="Upload" @click="importVisible = true">回显 XML</el-button>
          <el-button round :icon="Download" @click="exportXml">导出 XML</el-button>
          <el-button round type="success" :icon="Check" @click="saveXml">保存</el-button>
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
        <small>{{ selectedElement ? '已选中' : '未选择' }}</small>
      </div>

      <el-empty v-if="!selectedElement" description="点击画布节点查看配置" />
      <el-form v-else label-position="top" class="property-form">
        <el-form-item label="节点ID">
          <el-input :model-value="selectedElement.id" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="selectedName" placeholder="请输入节点名称" @change="updateSelectedName" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-input :model-value="selectedElement.type" disabled />
        </el-form-item>
        <el-form-item label="配置项">
          <el-select model-value="manual" disabled>
            <el-option label="人工处理" value="manual" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时策略">
          <el-select model-value="none" disabled>
            <el-option label="暂不启用" value="none" />
          </el-select>
        </el-form-item>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Check,
  Connection,
  Download,
  Finished,
  MagicStick,
  More,
  Promotion,
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

interface BpmnElement {
  id: string
  type: string
  businessObject?: {
    name?: string
  }
}

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
const selectedName = ref('')
const xmlVisible = ref(false)
const importVisible = ref(false)
const xmlText = ref('')
const importXmlText = ref('')
const elementCount = ref(0)
const zoomPercent = ref(100)

const currentXml = ref(props.modelValue || defaultBpmnXml())

const nodeTools = [
  { label: '触发', icon: VideoPlay },
  { label: '审批', icon: Check },
  { label: '动作', icon: Promotion },
  { label: '分支', icon: Switch },
  { label: 'AI', icon: MagicStick },
  { label: '通知', icon: Bell },
  { label: '结束', icon: Finished },
  { label: '更多', icon: More }
]

const selectedLabel = computed(() => selectedElement.value?.businessObject?.name || selectedElement.value?.id || '')

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
    selectedName.value = selectedLabel.value
  })
  eventBus.on('commandStack.changed', async () => {
    await syncXml(false)
    refreshStats()
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
    await syncXml(false)
    refreshStats()
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

function updateSelectedName() {
  if (!selectedElement.value || !modeler.value) return
  const modeling = modeler.value.get('modeling')
  modeling.updateProperties(selectedElement.value, { name: selectedName.value })
}

function deleteSelected() {
  if (!selectedElement.value || !modeler.value) return
  const modeling = modeler.value.get('modeling')
  modeling.removeElements([selectedElement.value])
  selectedElement.value = null
}

function refreshStats() {
  if (!modeler.value) return
  const elementRegistry = modeler.value.get('elementRegistry')
  elementCount.value = elementRegistry.filter((element: BpmnElement) => !element.type.includes('Label')).length
}

function showToolHint(label: string) {
  ElMessage.info(`${label} 节点可通过画布左侧 bpmn.js 工具栏创建`)
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
        <bpmndi:BPMNLabel>
          <dc:Bounds x="174" y="223" width="48" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_ManagerApprove_di" bpmnElement="UserTask_ManagerApprove">
        <dc:Bounds x="280" y="158" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Approved_di" bpmnElement="Gateway_Approved" isMarkerVisible="true">
        <dc:Bounds x="470" y="173" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="470" y="230" width="50" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Done_di" bpmnElement="EndEvent_Done">
        <dc:Bounds x="590" y="180" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="584" y="223" width="48" height="14" />
        </bpmndi:BPMNLabel>
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
