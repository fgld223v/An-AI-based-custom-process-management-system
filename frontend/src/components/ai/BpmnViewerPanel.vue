<template>
  <div ref="canvasRef" class="bpmn-canvas" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'

const props = defineProps<{ bpmnXml: string }>()
const canvasRef = ref<HTMLElement>()

let viewer: any = null

async function initViewer() {
  if (!canvasRef.value) return
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
  viewer = new BpmnViewer({ container: canvasRef.value })
  try {
    await viewer.importXML(props.bpmnXml)
    const canvas = viewer.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
  } catch (e) {
    console.error('BPMN 渲染失败:', e)
  }
}

onMounted(async () => {
  if (props.bpmnXml) {
    await initViewer()
  }
})

watch(() => props.bpmnXml, async (xml) => {
  if (xml) {
    await initViewer()
  }
})

onBeforeUnmount(() => {
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
})
</script>

<style>
@import 'bpmn-js/dist/assets/diagram-js.css';
@import 'bpmn-js/dist/assets/bpmn-js.css';
@import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
</style>

<style scoped>
.bpmn-canvas {
  width: 100%;
  height: 480px;
}
</style>
