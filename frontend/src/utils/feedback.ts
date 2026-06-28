import { ElMessage } from 'element-plus'

/**
 * 显示“功能暂未开放”提示。
 * 用于占位页面或尚未实现的功能入口，告知用户将在后续版本开放。
 */
export function showComingSoon() {
  ElMessage.info('该功能暂未开放，后续版本开放')
}
