/** 当前编辑中的流程模板（仅名称/ID，用于 TopBar 和 ProcessDesigner 间共享） */
export interface CurrentTemplate {
  id?: number
  templateName?: string
}
