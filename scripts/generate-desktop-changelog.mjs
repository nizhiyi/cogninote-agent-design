#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const args = parseArgs(process.argv.slice(2))
if (!args.output) {
  throw new Error('Missing required argument --output')
}
if (!args.version) {
  throw new Error('Missing required argument --version')
}

const maxCommits = Number.parseInt(args.maxCommits || '40', 10)
if (!Number.isFinite(maxCommits) || maxCommits <= 0) {
  throw new Error(`Invalid --maxCommits: ${args.maxCommits}`)
}

const releaseTag = args.releaseTag || `v${args.version}`
const previousTag = args.previousTag || findPreviousTag(releaseTag)
const range = previousTag ? `${previousTag}..HEAD` : 'HEAD'
const commits = readCommits(range, maxCommits)
const shortHead = runGit(['rev-parse', '--short', 'HEAD']).trim()
const rangeLabel = previousTag ? `${previousTag}...${shortHead}` : `初始提交...${shortHead}`

const changeCategories = [
  { key: 'features', title: '新增与改进' },
  { key: 'fixes', title: '问题修复' },
  { key: 'stability', title: '稳定性与性能' },
  { key: 'security', title: '安全更新' }
]

const scopeLabels = new Map([
  ['db', '数据库'],
  ['database', '数据库'],
  ['knowledge-health', '知识库健康诊断'],
  ['knowledge-maintenance', '知识库维护'],
  ['knowledge', '知识库维护'],
  ['ui', '界面'],
  ['desktop-update', '应用更新'],
  ['updater', '应用更新'],
  ['release', '发布流程']
])

mkdirSync(dirname(args.output), { recursive: true })
writeFileSync(args.output, renderUserNotes(args.version, commits), 'utf8')
if (args.technicalOutput) {
  mkdirSync(dirname(args.technicalOutput), { recursive: true })
  writeFileSync(args.technicalOutput, renderTechnicalNotes(range, rangeLabel, commits, maxCommits), 'utf8')
}

function findPreviousTag(currentTag) {
  const currentIsStableRelease = /^v\d+\.\d+\.\d+$/.test(currentTag)
  const tags = runGit(['tag', '--merged', 'HEAD', '--sort=-v:refname'])
    .split(/\r?\n/)
    .map((tag) => tag.trim())
    .filter(Boolean)
    .filter((tag) => /^v\d+\.\d+\.\d+(?:[-+].*)?$/.test(tag))
    .filter((tag) => tag !== currentTag)
    // 正式版更新说明应对比上一正式版，避免 v0.1.x-test.1 吃掉稳定版完整变更范围。
    .filter((tag) => !currentIsStableRelease || /^v\d+\.\d+\.\d+$/.test(tag))

  return tags[0] || ''
}

function readCommits(range, limit) {
  return runGit([
    'log',
    '--first-parent',
    `--max-count=${limit}`,
    '--format=%h%x09%s',
    range
  ])
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [hash, ...subjectParts] = line.split('\t')
      return {
        hash,
        subject: sanitizeSubject(subjectParts.join('\t'))
      }
    })
    .filter((commit) => commit.hash && commit.subject)
    .filter((commit) => !isNoiseCommit(commit.subject))
    .map((commit) => ({
      ...commit,
      ...parseConventionalSubject(commit.subject)
    }))
}

function hasMoreCommits(range, limit) {
  const count = Number.parseInt(runGit(['rev-list', '--count', range]).trim(), 10)
  return Number.isFinite(count) && count > limit
}

function sanitizeSubject(subject) {
  return subject.replace(/\s+/g, ' ').trim()
}

function parseConventionalSubject(subject) {
  const match = subject.match(/^([a-z]+)(?:\(([^)]+)\))?!?:\s+(.+)$/i)
  if (!match) {
    return {
      type: '',
      scope: '',
      description: subject
    }
  }
  return {
    type: match[1].toLowerCase(),
    scope: (match[2] || '').toLowerCase(),
    description: sanitizeSubject(match[3])
  }
}

function renderUserNotes(version, parsedCommits) {
  const grouped = Object.fromEntries(changeCategories.map((item) => [item.key, []]))

  for (const commit of parsedCommits) {
    const category = userCategory(commit)
    if (!category) {
      continue
    }
    const note = toUserNote(commit)
    if (note && !grouped[category].includes(note)) {
      grouped[category].push(note)
    }
  }

  const lines = [`## 知记空间 ${version} 更新内容`, '']
  let hasUserNotes = false
  for (const category of changeCategories) {
    const items = grouped[category.key]
    if (items.length === 0) {
      continue
    }
    hasUserNotes = true
    lines.push(`### ${category.title}`, '')
    lines.push(...items.map((item) => `- ${item}`), '')
  }

  if (!hasUserNotes) {
    lines.push('- 本次更新主要包含稳定性维护和内部优化。', '')
  }

  return `${lines.join('\n').trim()}\n`
}

function renderTechnicalNotes(range, rangeLabel, parsedCommits, limit) {
  const lines = [
    '## 技术详情',
    '',
    `- 变更范围：${rangeLabel}`,
    '',
    '### 原始提交记录',
    ''
  ]

  if (parsedCommits.length === 0) {
    lines.push('- 暂无可识别的代码变更记录。')
  } else {
    lines.push(...parsedCommits.map((commit) => `- ${commit.hash} ${commit.subject}`))
    if (parsedCommits.length === limit && hasMoreCommits(range, limit)) {
      lines.push('- 其余提交请查看 GitHub Release 对应的完整提交记录。')
    }
  }

  return `${lines.join('\n').trim()}\n`
}

function userCategory(commit) {
  if (commit.type === 'feat') {
    return 'features'
  }
  if (commit.type === 'fix') {
    return 'fixes'
  }
  if (commit.type === 'security') {
    return 'security'
  }
  if (commit.type === 'perf' || (commit.type === 'refactor' && isUserFacingRefactor(commit))) {
    return 'stability'
  }
  return ''
}

function isUserFacingRefactor(commit) {
  return /(启动|升级|性能|稳定|布局|界面|诊断|检测|队列|同步|更新|安装|数据库|初始化|冲突|重复|提示|导入|检索|渲染|弹窗)/.test(commit.description)
}

function toUserNote(commit) {
  const prefix = scopeLabel(commit.scope)
  const text = normalizeUserDescription(commit)
  if (!text) {
    return ''
  }
  return prefix ? `${prefix}：${text}` : text
}

function scopeLabel(scope) {
  if (!scope) {
    return ''
  }
  return scopeLabels.get(scope) || scope
    .split('-')
    .filter(Boolean)
    .map((part) => part[0]?.toUpperCase() + part.slice(1))
    .join(' ')
}

function normalizeUserDescription(commit) {
  let text = commit.description
    .replace(/旧版\s+schema\s+迁移逻辑/gi, '旧版数据库兼容逻辑')
    .replace(/schema/gi, '数据库结构')
    .replace(/SQLite/g, '本地数据存储')
    .replace(/mermaid/gi, '图表')
    .replace(/正则表达式/g, '匹配规则')
    .replace(/详情弹窗/g, '查看详情')
    .replace(/及/g, '和')
    .trim()

  if (commit.type === 'feat') {
    text = text
      .replace(/^实现/, '新增')
      .replace(/^添加/, '新增')
      .replace(/^为(.+)添加/, '$1新增')
  }

  if (commit.type === 'fix' && !/^修复/.test(text)) {
    text = `修复${text}`
  }

  if ((commit.type === 'perf' || commit.type === 'refactor') && /^重构/.test(text)) {
    text = text.replace(/^重构/, '优化')
  }

  if (!/[。！？.!?]$/.test(text)) {
    text = `${text}。`
  }
  return text
}

function isNoiseCommit(subject) {
  return [
    /^merge\b/i,
    /^chore\(release\):\s*(更新版本号|bump version)/i,
    /^chore:\s*(升级版本|更新版本号|bump version)/i
  ].some((pattern) => pattern.test(subject))
}

function runGit(args) {
  return execFileSync('git', args, { encoding: 'utf8' })
}

function parseArgs(argv) {
  const result = {}
  for (let index = 0; index < argv.length; index += 1) {
    const item = argv[index]
    if (!item.startsWith('--')) {
      continue
    }
    const key = item.slice(2)
    const next = argv[index + 1]
    if (!next || next.startsWith('--')) {
      result[key] = 'true'
      continue
    }
    result[key] = next
    index += 1
  }
  return result
}
