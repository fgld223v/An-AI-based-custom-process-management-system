/**
 * 模板编辑状态管理（Pinia Store）。
 *
 * 用于在流程设计器 / 表单设计器中追踪当前正在编辑的模板，
 * 同时在 TopBar 中展示当前流程名称。
 */
import { defineStore } from 'pinia'
import type { CurrentTemplate } from '@/types/template'

export const useTemplateStore = defineStore('template', {
  state: () => ({
    /** 当前正在编辑的模板对象（null 表示未在编辑模式） */
    currentTemplate: null as CurrentTemplate | null,
    /** 当前流程名称（用于顶栏展示，默认为请假示例） */
    currentFlowName: '员工请假审批流程'
  }),
  actions: {
    /** 设置当前模板，同步更新展示名称 */
    setCurrentTemplate(template: CurrentTemplate | null) {
      this.currentTemplate = template
      this.currentFlowName = template?.templateName || '员工请假审批流程'
    },
    /** 单独更新流程名称（用于编辑过程中修改名称） */
    setCurrentFlowName(name: string) {
      this.currentFlowName = name
    }
  }
})
