/**
 * Markdown 渲染工具。
 *
 * 基于 marked + highlight.js：
 *  - marked 将 Markdown 文本解析为 HTML
 *  - highlight.js 对代码块进行语法高亮（支持语言自动检测）
 *  - 防御性 HTML 清洗：移除 script/iframe/object/embed 等危险标签
 */
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'  // GitHub 风格代码高亮主题

// 配置 marked 使用 highlight.js 处理代码块高亮
marked.setOptions({
  // @ts-ignore highlight 选项在 marked v4 中有效但类型定义可能滞后
  highlight(code: string, lang: string) {
    // 指定语言时尝试精确高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch {
        // 精确高亮失败，回退到自动检测
      }
    }
    // 自动检测语言
    try {
      return hljs.highlightAuto(code).value
    } catch {
      return code  // 最差情况：返回原始代码
    }
  },
  breaks: true  // 将换行符转换为 <br>
})

/**
 * 将 Markdown 文本渲染为安全 HTML。
 *
 * 安全措施：
 *  1. marked 默认转义原始 HTML
 *  2. sanitizeHtml 做深度防御，移除潜在危险标签
 *
 * @param text Markdown 原始文本
 * @returns 安全的 HTML 字符串
 */
export function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(text) as string
  return sanitizeHtml(html)
}

/**
 * 移除 HTML 中的潜在危险标签和属性。
 * 作为 marked 内置转义之外的深度防御措施。
 */
function sanitizeHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')   // 移除 script 标签
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')   // 移除 iframe
    .replace(/<object[\s\S]*?<\/object>/gi, '')   // 移除 object
    .replace(/<embed[\s\S]*?>/gi, '')             // 移除 embed
    .replace(/javascript:/gi, '')                  // 移除 javascript: 伪协议
    .replace(/on\w+\s*=/gi, '')                    // 移除内联事件处理器
}
