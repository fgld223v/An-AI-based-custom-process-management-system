import { defineStore } from 'pinia'
import type { CurrentTemplate } from '@/types/template'

export const useTemplateStore = defineStore('template', {
  state: () => ({
    currentTemplate: null as CurrentTemplate | null,
    currentFlowName: '员工请假审批流程'
  }),
  actions: {
    setCurrentTemplate(template: CurrentTemplate | null) {
      this.currentTemplate = template
      this.currentFlowName = template?.templateName || '员工请假审批流程'
    },
    setCurrentFlowName(name: string) {
      this.currentFlowName = name
    }
  }
})
