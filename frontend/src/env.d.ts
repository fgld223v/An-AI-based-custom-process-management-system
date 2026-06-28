/// <reference types="vite/client" />

/**
 * Vite 环境类型声明，为 .vue 单文件组件和 bpmn-js Modeler 提供 TypeScript 模块声明。
 */

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, any>
  export default component
}

declare module 'bpmn-js/lib/Modeler'
