import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// Configure marked with highlight.js for code blocks
marked.setOptions({
  // @ts-ignore highlight option is valid in marked v4 but types may lag
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch {
        // fall through to auto-detect
      }
    }
    try {
      return hljs.highlightAuto(code).value
    } catch {
      return code
    }
  },
  breaks: true
})

/**
 * Render markdown string to HTML.
 * Sanitization: marked by default escapes raw HTML in the input,
 * but we also strip dangerous tags as a defense-in-depth measure.
 */
export function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(text) as string
  return sanitizeHtml(html)
}

/** Remove potentially dangerous HTML tags */
function sanitizeHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[\s\S]*?>/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/on\w+\s*=/gi, '')
}
