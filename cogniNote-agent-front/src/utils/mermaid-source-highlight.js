export function renderMermaidSourceHtml(source) {
  const parts = String(source).split(/(\r?\n)/)
  return parts.map((part) => {
    if (part === '\n' || part === '\r\n') {
      return part
    }
    return highlightMermaidLine(part)
  }).join('')
}

export function trimFenceBoundaryBlankLines(value) {
  return String(value).replace(/^(?:\r?\n)+/, '').replace(/(?:\r?\n)+$/, '')
}

function highlightMermaidLine(line) {
  if (!line) {
    return ''
  }

  const leadingWhitespace = line.match(/^\s*/)?.[0] ?? ''
  const body = line.slice(leadingWhitespace.length)
  if (!body) {
    return escapeHtml(line)
  }
  if (body.trimStart().startsWith('%%')) {
    return `${escapeHtml(leadingWhitespace)}<span class="mermaid-token-comment">${escapeHtml(body)}</span>`
  }

  const tokens = tokenizeMermaidLine(body)
  return `${escapeHtml(leadingWhitespace)}${tokens.map(renderMermaidToken).join('')}`
}

function tokenizeMermaidLine(line) {
  const tokenPattern = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\[[^\]]*]|\([^)]*\)|\{[^}]*}|-->|---|==>|-.->|~~>|-\.-|--|==|:::+|[A-Za-z_][\w.-]*|\d+(?:\.\d+)?|[^\sA-Za-z_\d]+)/g
  const tokens = []
  let lastIndex = 0
  let match = tokenPattern.exec(line)

  while (match) {
    if (match.index > lastIndex) {
      tokens.push({ value: line.slice(lastIndex, match.index), type: 'text' })
    }
    tokens.push({ value: match[0], type: classifyMermaidToken(match[0], tokens) })
    lastIndex = tokenPattern.lastIndex
    match = tokenPattern.exec(line)
  }

  if (lastIndex < line.length) {
    tokens.push({ value: line.slice(lastIndex), type: 'text' })
  }
  return tokens
}

function classifyMermaidToken(token, previousTokens) {
  const lower = token.toLowerCase()
  const previousMeaningful = [...previousTokens].reverse().find((item) => item.type !== 'text' && item.value.trim())

  if (token.startsWith('[') || token.startsWith('(') || token.startsWith('{')) {
    return 'label'
  }
  if (/^["'`]/.test(token)) {
    return 'string'
  }
  if (/^(-->|---|==>|-.->|~~>|-\.-|--|==)$/.test(token)) {
    return 'edge'
  }
  if (/^:::+$/.test(token)) {
    return 'punctuation'
  }
  if (/^\d/.test(token)) {
    return 'number'
  }
  if (MERMAID_DECLARATION_KEYWORDS.has(lower)) {
    return 'keyword'
  }
  if (MERMAID_SECTION_KEYWORDS.has(lower)) {
    return 'section'
  }
  if (previousMeaningful?.type === 'keyword' || previousMeaningful?.type === 'section') {
    return 'property'
  }
  if (/^[A-Za-z_][\w.-]*$/.test(token)) {
    return 'identifier'
  }
  return 'punctuation'
}

function renderMermaidToken(token) {
  if (token.type === 'text') {
    return escapeHtml(token.value)
  }
  return `<span class="mermaid-token-${token.type}">${escapeHtml(token.value)}</span>`
}

const MERMAID_DECLARATION_KEYWORDS = new Set([
  'accdescr',
  'acctitle',
  'architecture',
  'block',
  'block-beta',
  'c4component',
  'c4container',
  'c4context',
  'c4deployment',
  'c4dynamic',
  'classdiagram',
  'classdiagram-v2',
  'erdiagram',
  'flowchart',
  'gantt',
  'gitgraph',
  'graph',
  'journey',
  'mindmap',
  'pie',
  'quadrantchart',
  'requirementdiagram',
  'sankey-beta',
  'sequencediagram',
  'statediagram',
  'statediagram-v2',
  'timeline',
  'xychart-beta'
])

const MERMAID_SECTION_KEYWORDS = new Set([
  'activate',
  'alt',
  'and',
  'as',
  'autonumber',
  'axisformat',
  'bar',
  'class',
  'classdef',
  'click',
  'critical',
  'dateformat',
  'deactivate',
  'direction',
  'else',
  'end',
  'excludes',
  'from',
  'link',
  'linkstyle',
  'loop',
  'note',
  'opt',
  'par',
  'participant',
  'rect',
  'section',
  'state',
  'style',
  'subgraph',
  'task',
  'tb',
  'td',
  'title',
  'todaymarker'
])

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
