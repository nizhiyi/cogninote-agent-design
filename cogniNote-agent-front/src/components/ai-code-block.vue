<script setup>
// 渲染 AI Markdown 里的 fenced code，并特殊处理 Mermaid 图块。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Check, ChevronDown, ChevronUp, Copy, Maximize2, X } from 'lucide-vue-next'
import { MermaidBlockNode } from 'markstream-vue'
import { registerHighlight, renderCodeWithTokens } from 'stream-markdown'

const HIGHLIGHT_THEME = 'vitesse-dark'
const DEFAULT_COLLAPSED_LINES = 12

const SUPPORTED_LANGUAGES = [
  'bash',
  'bat',
  'c',
  'cmd',
  'cpp',
  'csharp',
  'css',
  'diff',
  'dockerfile',
  'go',
  'html',
  'ini',
  'java',
  'javascript',
  'jsx',
  'json',
  'kotlin',
  'lua',
  'markdown',
  'mermaid',
  'php',
  'powershell',
  'properties',
  'python',
  'ruby',
  'rust',
  'scala',
  'shellscript',
  'sh',
  'sql',
  'swift',
  'toml',
  'typescript',
  'tsx',
  'vue',
  'xml',
  'yaml',
  'zsh'
]

const LANGUAGE_ALIASES = {
  cplusplus: 'cpp',
  cs: 'csharp',
  docker: 'dockerfile',
  htm: 'html',
  js: 'javascript',
  jsx: 'jsx',
  md: 'markdown',
  ps1: 'powershell',
  py: 'python',
  rb: 'ruby',
  shell: 'bash',
  text: 'plaintext',
  ts: 'typescript',
  tsx: 'tsx',
  yml: 'yaml'
}

const props = defineProps({
  node: {
    type: Object,
    required: true
  },
  isDark: {
    type: Boolean,
    default: false
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  showCopyButton: {
    type: Boolean,
    default: true
  },
  showExpandButton: {
    type: Boolean,
    default: true
  },
  collapsedLines: {
    type: Number,
    default: DEFAULT_COLLAPSED_LINES
  }
})

const highlightedHtml = ref('')
const isHighlighted = ref(false)
const copied = ref(false)
const isModalOpen = ref(false)
// 高亮器按需加载是异步的，版本号用来丢弃已过期的渲染结果。
let renderVersion = 0
let copiedTimer = 0

const code = computed(() => String(props.node?.code ?? ''))
const renderCode = computed(() => trimFenceBoundaryBlankLines(code.value))
const originalLanguage = computed(() => String(props.node?.language ?? '').trim())
const normalizedLanguage = computed(() => normalizeLanguage(originalLanguage.value, renderCode.value))
const displayLanguage = computed(() => formatLanguageLabel(originalLanguage.value || normalizedLanguage.value))
const shouldRenderAsMermaid = computed(() => normalizedLanguage.value === 'mermaid')
// Mermaid 渲染对语法敏感，进入渲染前先做兼容清洗。
const mermaidRenderCode = computed(() => normalizeMermaidForRendering(renderCode.value))
const mermaidNode = computed(() => ({
  ...props.node,
  code: mermaidRenderCode.value,
  language: 'mermaid'
}))
const isRenderReady = computed(() => highlightedHtml.value.length > 0)
const visibleLineCount = computed(() => Math.max(1, Number(props.collapsedLines) || DEFAULT_COLLAPSED_LINES))
const codeLineCount = computed(() => {
  if (!renderCode.value) {
    return 0
  }
  return renderCode.value.split(/\r?\n/).length
})
const canCollapse = computed(() => normalizedLanguage.value !== 'mermaid' && codeLineCount.value > visibleLineCount.value)
const isCollapsed = ref(true)
const collapseSummary = computed(() => `已收起，显示前 ${visibleLineCount.value} 行，共 ${codeLineCount.value} 行`)

onBeforeUnmount(() => {
  window.clearTimeout(copiedTimer)
})

watch(
  [renderCode, normalizedLanguage],
  () => {
    // 新代码块重新回到默认折叠状态，避免上一条消息的展开状态串到下一条。
    isCollapsed.value = true
    renderHighlightedCode()
  },
  { immediate: true }
)

async function renderHighlightedCode() {
  const currentVersion = ++renderVersion
  const currentCode = renderCode.value
  const lang = normalizedLanguage.value
  if (shouldRenderAsMermaid.value) {
    highlightedHtml.value = ''
    isHighlighted.value = false
    return
  }

  try {
    // 高亮依赖按需加载，失败时必须保持原始代码可读。
    const highlighter = await registerHighlight({
      langs: SUPPORTED_LANGUAGES,
      themes: [HIGHLIGHT_THEME]
    })
    const html = renderCodeWithTokens(highlighter, currentCode, {
      lang,
      theme: HIGHLIGHT_THEME,
      preClass: `shiki language-${lang}`,
      codeClass: `language-${lang}`,
      htmlCache: true,
      tokenCache: true
    })
    if (currentVersion === renderVersion) {
      highlightedHtml.value = html
      isHighlighted.value = true
    }
  } catch {
    // 高亮依赖按需加载失败时保留可读代码块，避免一处语言异常影响整条 AI 回复。
    if (currentVersion === renderVersion) {
      highlightedHtml.value = `<pre class="shiki shiki-fallback"><code>${escapeHtml(currentCode)}</code></pre>`
      isHighlighted.value = false
    }
  }
}

async function copyCode() {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(code.value)
    } else {
      fallbackCopy(code.value)
    }
    copied.value = true
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => {
      copied.value = false
    }, 1400)
  } catch {
    copied.value = false
  }
}

function fallbackCopy(text) {
  // Clipboard API 受权限和安全上下文限制，textarea 兜底用于本地桌面 WebView。
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function toggleCollapsed() {
  isCollapsed.value = !isCollapsed.value
}

function normalizeLanguage(language, codeText = '') {
  const normalized = String(language || 'plaintext')
    .trim()
    .toLowerCase()
    .replace(/^language-/, '')
  const alias = LANGUAGE_ALIASES[normalized] || normalized || 'plaintext'
  if (alias === 'plaintext' && looksLikeMermaid(codeText)) {
    return 'mermaid'
  }
  return SUPPORTED_LANGUAGES.includes(alias) || alias === 'plaintext' ? alias : 'plaintext'
}

function formatLanguageLabel(language) {
  const normalized = normalizeLanguage(language, renderCode.value)
  const label = language || normalized
  if (!label || normalized === 'plaintext') {
    return 'Plain text'
  }
  if (normalized === 'mermaid') {
    return 'Mermaid'
  }
  return label.replace(/^language-/, '').replace(/[-_]+/g, ' ')
}

function looksLikeMermaid(value) {
  const firstContentLine = String(value)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line && !line.startsWith('%%'))
  if (!firstContentLine) {
    return false
  }
  return /^(?:graph|flowchart|sequenceDiagram|classDiagram(?:-v2)?|stateDiagram(?:-v2)?|erDiagram|gantt|journey|pie|quadrantChart|timeline|xychart(?:-beta)?|mindmap|gitGraph|requirementDiagram|c4Context|c4Container|c4Component|c4Dynamic|c4Deployment|block(?:-beta)?|sankey-beta)\b/i.test(firstContentLine)
}

function trimFenceBoundaryBlankLines(value) {
  // Markdown fenced code 常见写法会在围栏后/前多带一个空行；只裁边界空白，不碰代码内部空行。
  return String(value).replace(/^(?:\r?\n)+/, '').replace(/(?:\r?\n)+$/, '')
}

function normalizeMermaidForRendering(value) {
  const source = normalizeMermaidHtmlBreaks(trimFenceBoundaryBlankLines(value))
  if (!isMermaidFlowchart(source)) {
    return source
  }
  return source.split(/\r?\n/).map(sanitizeMermaidFlowchartLine).join('\n')
}

function isMermaidFlowchart(value) {
  const firstContentLine = String(value)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line && !line.startsWith('%%'))
  return /^(?:graph|flowchart)\b/i.test(firstContentLine || '')
}

/**
 * 清理模型常见的 flowchart 坏格式。
 *
 * 弱模型容易把 HTML 换行、长箭头或自然语言边说明直接塞进 Mermaid。
 * 这里只修高置信错误，避免重写整张图导致原本合法的语法被改坏。
 */
function sanitizeMermaidFlowchartLine(line) {
  const normalizedLine = normalizeMermaidEdgeOperators(normalizeMermaidHtmlBreaks(line))
  const labelSafeLine = quoteMermaidFlowchartLabels(normalizedLine)
  const edgeSafeLine = normalizeBrokenMermaidEdgeLabel(labelSafeLine)
  return isDanglingMermaidEdge(edgeSafeLine) || isBrokenSingleEndedMermaidEdge(edgeSafeLine)
    ? toMermaidComment(edgeSafeLine)
    : edgeSafeLine
}

function quoteMermaidFlowchartLabels(line) {
  let result = ''
  let index = 0

  while (index < line.length) {
    const token = line.slice(index).match(/^[A-Za-z_][\w.-]*/)
    if (!token) {
      result += line[index]
      index += 1
      continue
    }

    const nodeId = token[0]
    const labelStart = index + nodeId.length
    if (line[labelStart] !== '[' || line[labelStart + 1] === '[') {
      result += nodeId
      index = labelStart
      continue
    }

    const labelEnd = findSimpleSquareLabelEnd(line, labelStart + 1)
    if (labelEnd < 0) {
      result += nodeId
      index = labelStart
      continue
    }

    const label = line.slice(labelStart + 1, labelEnd)
    // Mermaid 渲染对语法敏感，进入渲染前先做兼容清洗。
    result += `${nodeId}[${normalizeMermaidSquareLabel(label)}]`
    index = labelEnd + 1
  }

  return result
}

function normalizeMermaidHtmlBreaks(value) {
  return String(value)
    .replace(/&lt;br\s*\/?&gt;/gi, ' / ')
    .replace(/<br\s*\/?>/gi, ' / ')
}

function normalizeMermaidEdgeOperators(line) {
  return String(line)
    .replace(/(^|\s)-{3,}>(?=\s|$)/g, '$1-->')
    .replace(/(^|\s)={3,}>(?=\s|$)/g, '$1==>')
    .replace(/(^|\s)-{2,}\^+(?=\s|$)/g, '$1-->')
    .replace(/(^|\s)-{4,}(?=\s|$)/g, '$1---')
}

function normalizeBrokenMermaidEdgeLabel(line) {
  return String(line).replace(
    /^(\s*[A-Za-z_][\w.-]*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*})?\s+)-->(?!\|)(\s+)(.+?)\s+([A-Za-z_][\w.-]*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*})?\s*;?\s*)$/,
    (match, source, _space, label, target) => {
      const normalizedLabel = label.trim()
      if (!normalizedLabel || isLikelyMermaidNodeReference(normalizedLabel) || isQuotedMermaidLabel(normalizedLabel)) {
        return match
      }
      return `${source}-- ${normalizeMermaidEdgeLabel(normalizedLabel)} --> ${target.trim()}`
    }
  )
}

function isDanglingMermaidEdge(line) {
  return /^\s*[A-Za-z_][\w.-]*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*})?\s+(?:-->|---|==>|-.->)\s*(?:[-=._\s]+)?;?\s*$/.test(line)
}

function isBrokenSingleEndedMermaidEdge(line) {
  const match = String(line).match(/^\s*[A-Za-z_][\w.-]*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*})?\s+(?:-->|---|==>|-.->)\s+(.+?)\s*;?\s*$/)
  if (!match) {
    return false
  }
  const target = match[1].trim()
  if (!target || /^[-=._\s]+$/.test(target)) {
    return true
  }
  if (/^\|[\s\S]*\|\s*[A-Za-z_]/.test(target) || /^[A-Za-z_][\w.-]*(?:\s*&\s*[A-Za-z_][\w.-]*)+$/.test(target)) {
    return false
  }
  if (isLikelyMermaidNodeReference(target) || isLikelyMermaidEdgeChain(target)) {
    return false
  }
  return /[\s<>"'()[\]{}，。；：、≥≤≈φ^+\u4e00-\u9fff]/.test(target)
}

function toMermaidComment(line) {
  const text = String(line).trim()
  return text ? `%% 已忽略无法自动修复的 Mermaid 行：${text}` : line
}

function findSimpleSquareLabelEnd(line, startIndex) {
  let quote = ''
  for (let index = startIndex; index < line.length; index += 1) {
    const char = line[index]
    if (quote) {
      if (char === quote && line[index - 1] !== '\\') {
        quote = ''
      }
      continue
    }
    if (char === '"' || char === '`') {
      quote = char
      continue
    }
    if (char === '[') {
      return -1
    }
    if (char === ']') {
      return index
    }
  }
  return -1
}

function normalizeMermaidSquareLabel(label) {
  const trimmed = label.trim()
  if (!trimmed || isQuotedMermaidLabel(trimmed)) {
    return label
  }
  return `"${escapeMermaidQuotedLabel(label)}"`
}

function normalizeMermaidEdgeLabel(label) {
  return `"${escapeMermaidQuotedLabel(label)}"`
}

function isQuotedMermaidLabel(label) {
  return (label.startsWith('"') && label.endsWith('"')) || (label.startsWith('`') && label.endsWith('`'))
}

function isLikelyMermaidNodeReference(value) {
  return /^[A-Za-z_][\w.-]*(?:\[[^\]]*]|\([^)]*\)|\{[^}]*})?(?:::+[A-Za-z_][\w.-]*)?$/.test(value)
}

function isLikelyMermaidEdgeChain(value) {
  const nodeRef = '[A-Za-z_][\\w.-]*(?:\\[[^\\]]*]|\\([^)]*\\)|\\{[^}]*})?(?:::+[A-Za-z_][\\w.-]*)?'
  return new RegExp(`^${nodeRef}(?:\\s+(?:-->|---|==>|-.->)\\s+${nodeRef})+$`).test(value)
}

function handleMermaidRenderError(error, failedCode, container) {
  if (typeof document === 'undefined' || !container) {
    return false
  }

  // onRenderError 返回 true 才能接管 markstream-vue 默认错误 UI，保留源码比暴露解析栈更可操作。
  const fallback = document.createElement('div')
  fallback.className = 'cogninote-mermaid-fallback'

  const title = document.createElement('div')
  title.className = 'cogninote-mermaid-fallback-title'
  title.textContent = 'Mermaid 图表语法无法渲染，已保留源码'

  const message = document.createElement('div')
  message.className = 'cogninote-mermaid-fallback-message'
  message.textContent = formatMermaidRenderError(error)

  const pre = document.createElement('pre')
  pre.className = 'cogninote-mermaid-fallback-source'
  const codeElement = document.createElement('code')
  codeElement.textContent = String(failedCode || mermaidRenderCode.value || '')
  pre.appendChild(codeElement)

  fallback.append(title, message, pre)
  try {
    container.replaceChildren(fallback)
  } catch {
    container.innerHTML = ''
    container.appendChild(fallback)
  }
  return true
}

function formatMermaidRenderError(error) {
  const rawMessage = error instanceof Error
    ? error.message
    : typeof error === 'string'
      ? error
      : '模型输出的 Mermaid 语法不完整或包含不支持的连接符。'
  const message = String(rawMessage).replace(/\s+/g, ' ').trim()
  return message.length > 280 ? `${message.slice(0, 280)}...` : message
}

/**
 * 清理 escape Mermaid Quoted Label 文本。
 * <p>渲染模型输出前先处理特殊字符，避免破坏 HTML 或 Mermaid 结构。</p>
 */
function escapeMermaidQuotedLabel(label) {
  return String(label).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

/**
 * 清理 escape Html 文本。
 * <p>渲染模型输出前先处理特殊字符，避免破坏 HTML 或 Mermaid 结构。</p>
 */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<template>
  <MermaidBlockNode
    v-if="shouldRenderAsMermaid"
    :node="mermaidNode"
    max-height="none"
    :estimated-preview-height-px="360"
    :is-dark="isDark"
    :show-header="true"
    :show-mode-toggle="true"
    :show-copy-button="true"
    :show-export-button="true"
    :show-fullscreen-button="true"
    :show-collapse-button="true"
    :show-zoom-controls="true"
    :enable-wheel-zoom="true"
    :is-strict="true"
    :enable-mermaid-interactions="false"
    :on-render-error="handleMermaidRenderError"
  />
  <div
    v-else
    class="cogninote-code-block"
    :class="{ 'is-collapsible': canCollapse, 'is-collapsed': canCollapse && isCollapsed }"
    data-markstream-code-block="1"
    :data-markstream-enhanced="isRenderReady ? 'true' : 'false'"
  >
    <div v-if="showHeader" class="cogninote-code-header">
      <span class="cogninote-code-language">{{ displayLanguage }}</span>
      <div class="cogninote-code-actions">
        <button
          v-if="showCopyButton"
          class="cogninote-code-action"
          type="button"
          :title="copied ? '已复制' : '复制代码'"
          :aria-label="copied ? '已复制' : '复制代码'"
          @click="copyCode"
        >
          <Check v-if="copied" :size="15" aria-hidden="true" />
          <Copy v-else :size="15" aria-hidden="true" />
        </button>
        <button
          v-if="showExpandButton"
          class="cogninote-code-action"
          type="button"
          title="打开代码"
          aria-label="打开代码"
          @click="isModalOpen = true"
        >
          <Maximize2 :size="15" aria-hidden="true" />
        </button>
      </div>
    </div>
    <div
      class="cogninote-code-scroll"
      :style="canCollapse && isCollapsed ? { '--collapsed-lines': visibleLineCount } : undefined"
    >
      <!-- Shiki 会转义源码内容，这里只渲染可信高亮器产出的 token HTML。 -->
      <div class="cogninote-code-render" v-html="highlightedHtml"></div>
    </div>
    <div v-if="canCollapse" class="cogninote-code-collapse-bar">
      <span v-if="isCollapsed" class="cogninote-code-collapse-summary">{{ collapseSummary }}</span>
      <button
        class="cogninote-code-collapse-button"
        type="button"
        :aria-expanded="!isCollapsed"
        :aria-label="isCollapsed ? '展开完整代码' : '收起代码块'"
        @click="toggleCollapsed"
      >
        <ChevronDown v-if="isCollapsed" :size="15" aria-hidden="true" />
        <ChevronUp v-else :size="15" aria-hidden="true" />
        <span>{{ isCollapsed ? '展开完整代码' : '收起代码块' }}</span>
      </button>
    </div>

    <Teleport to="body">
      <div
        v-if="isModalOpen"
        class="cogninote-code-modal"
        :class="{ 'is-dark': isDark }"
        @click.self="isModalOpen = false"
      >
        <section class="cogninote-code-dialog" role="dialog" aria-modal="true" :aria-label="`${displayLanguage} 代码`">
          <header class="cogninote-code-dialog-header">
            <span class="cogninote-code-language">{{ displayLanguage }}</span>
            <div class="cogninote-code-actions">
              <button class="cogninote-code-action" type="button" title="复制代码" aria-label="复制代码" @click="copyCode">
                <Check v-if="copied" :size="15" aria-hidden="true" />
                <Copy v-else :size="15" aria-hidden="true" />
              </button>
              <button
                class="cogninote-code-action"
                type="button"
                title="关闭"
                aria-label="关闭"
                @click="isModalOpen = false"
              >
                <X :size="16" aria-hidden="true" />
              </button>
            </div>
          </header>
          <div class="cogninote-code-dialog-body">
            <div class="cogninote-code-render" v-html="highlightedHtml"></div>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.cogninote-code-block {
  --code-bg: #0b1117;
  --code-fg: #d7e6ef;
  --code-border: rgba(114, 143, 161, 0.38);
  --code-header-bg: #111a22;
  --code-action-fg: #9fb8c8;
  --code-action-hover-bg: color-mix(in srgb, var(--color-action) 22%, transparent);
  --code-action-hover-fg: #eef7fb;
  width: 100%;
  max-width: 100%;
  margin: 0 0 12px;
  overflow: hidden;
  border: 1px solid var(--code-border);
  border-radius: var(--radius-sm);
  background: var(--code-bg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.64);
}

.cogninote-code-header,
.cogninote-code-dialog-header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--code-border);
  color: var(--code-action-fg);
  background: var(--code-header-bg);
}

.cogninote-code-header {
  padding: 8px 10px;
}

.cogninote-code-language {
  min-width: 0;
  overflow: hidden;
  color: var(--code-action-fg);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  text-transform: capitalize;
  white-space: nowrap;
}

.cogninote-code-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}

.cogninote-code-action {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  color: var(--code-action-fg);
  background: transparent;
  cursor: pointer;
}

.cogninote-code-action:hover,
.cogninote-code-action:focus-visible {
  color: var(--code-action-hover-fg);
  background: var(--code-action-hover-bg);
  outline: none;
}

.cogninote-code-scroll,
.cogninote-code-dialog-body {
  max-width: 100%;
  overflow: auto;
  background: var(--code-bg);
}

.cogninote-code-block.is-collapsed .cogninote-code-scroll {
  max-height: calc((var(--collapsed-lines, 12) * 1.5 * 13px) + 28px);
  overflow: auto;
}

.cogninote-code-render {
  width: max-content;
  min-width: 100%;
}

.cogninote-code-render :deep(.shiki) {
  min-width: max-content;
  margin: 0 !important;
  padding: 14px !important;
  border: 0 !important;
  border-radius: 0 !important;
  color: var(--code-fg);
  background: transparent !important;
  box-shadow: none !important;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.5;
  tab-size: 4;
}

.cogninote-code-render :deep(.shiki code) {
  display: block;
  min-width: max-content;
  padding: 0 !important;
  border: 0 !important;
  color: inherit;
  background: transparent !important;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
  white-space: pre;
}

.cogninote-code-collapse-bar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 10px;
  border-top: 1px solid var(--code-border);
  color: var(--code-action-fg);
  background: var(--code-header-bg);
}

.cogninote-code-collapse-summary {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cogninote-code-collapse-button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  color: var(--code-action-fg);
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.cogninote-code-collapse-button:hover,
.cogninote-code-collapse-button:focus-visible {
  color: var(--code-action-hover-fg);
  background: var(--code-action-hover-bg);
  outline: none;
}

.cogninote-code-modal {
  --code-bg: #0b1117;
  --code-fg: #d7e6ef;
  --code-border: rgba(114, 143, 161, 0.38);
  --code-header-bg: #111a22;
  --code-action-fg: #9fb8c8;
  --code-action-hover-bg: color-mix(in srgb, var(--color-action) 22%, transparent);
  --code-action-hover-fg: #eef7fb;
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: color-mix(in srgb, var(--color-bg) 80%, rgba(0, 0, 0, 0.62));
}

.cogninote-code-dialog {
  display: grid;
  width: min(1100px, calc(100vw - 36px));
  height: min(760px, calc(100vh - 36px));
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--code-border);
  border-radius: var(--radius-sm);
  background: var(--code-bg);
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.28);
}

.cogninote-code-dialog-header {
  padding: 9px 12px;
}

.cogninote-code-dialog-body {
  min-height: 0;
}

:deep(.cogninote-mermaid-fallback) {
  display: grid;
  width: min(100%, 860px);
  max-width: calc(100vw - 56px);
  gap: 10px;
  margin: 22px auto;
  padding: 14px;
  border: 1px solid var(--diagram-border, var(--code-border));
  border-radius: var(--radius-sm);
  color: var(--color-text);
  background: var(--color-surface);
  text-align: left;
  box-shadow: 0 8px 24px rgba(15, 42, 58, 0.08);
}

:deep(.cogninote-mermaid-fallback-title) {
  color: var(--color-text-strong);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
}

:deep(.cogninote-mermaid-fallback-message) {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

:deep(.cogninote-mermaid-fallback-source) {
  max-height: 280px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--code-border);
  border-radius: var(--radius-sm);
  color: var(--code-fg);
  background: var(--code-bg);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre;
}
</style>
