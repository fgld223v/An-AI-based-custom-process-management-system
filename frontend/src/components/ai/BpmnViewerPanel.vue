<template>
  <!-- BPMN 流程图渲染画布 -->
  <div ref="canvasRef" class="bpmn-canvas" />
</template>

<script setup lang="ts">
/**
 * BpmnViewerPanel - BPMN 流程图查看器
 *
 * 基于 bpmn-js 的 Viewer 模式渲染 BPMN 2.0 XML，
 * 监听 bpmnXml 属性的变化并自动重新渲染。
 * 组件销毁时释放 bpmn-js 实例。
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'

/** 父组件传入的 BPMN XML 字符串 */
const props = defineProps<{ bpmnXml: string }>()
const canvasRef = ref<HTMLElement>()

let viewer: any = null

/** 初始化（或重建） BPMN 查看器实例 */
async function initViewer() {
  if (!canvasRef.value) return
  // 销毁已存在的查看器实例
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
  viewer = new BpmnViewer({ container: canvasRef.value })
  try {
    await viewer.importXML(props.bpmnXml)
    // 自适应视口缩放
    const canvas = viewer.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
  } catch (e) {
    console.error('BPMN 渲染失败:', e)
  }
}

/** 挂载时若有 XML 数据则立即初始化 */
onMounted(async () => {
  if (props.bpmnXml) {
    await initViewer()
  }
})

/** 监听 bpmnXml 变化，自动重新渲染 */
watch(() => props.bpmnXml, async (xml) => {
  if (xml) {
    await initViewer()
  }
})

/** 销毁前清理 bpmn-js 实例 */
onBeforeUnmount(() => {
  if (viewer) {
    viewer.destroy()
    viewer = null
  }
})
</script>

<style>
/* 引入 bpmn-js 官方样式 */
@import 'bpmn-js/dist/assets/diagram-js.css';
@import 'bpmn-js/dist/assets/bpmn-js.css';
@import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
</style>

<style scoped>
/* 画布容器固定高度 */
.bpmn-canvas {
  width: 100%;
  height: 480px;
}
</style>
