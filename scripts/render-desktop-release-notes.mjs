#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

const args = parseArgs(process.argv.slice(2))
const required = ['existing', 'output', 'version', 'releaseKind', 'platform', 'template']
for (const key of required) {
  if (!args[key]) {
    throw new Error(`Missing required argument --${key}`)
  }
}

const platformOrder = ['windows', 'macos']
const platformLabels = {
  windows: 'Windows x64',
  macos: 'macOS Apple Silicon'
}
if (!platformOrder.includes(args.platform)) {
  throw new Error(`Unsupported platform: ${args.platform}`)
}

const existing = existsSync(args.existing) ? readFileSync(args.existing, 'utf8') : ''
const sections = {
  ...migrateLegacySections(existing),
  ...extractMarkedSections(existing)
}
const changelog = readChangelog(args.changelogFile) || extractMarkedChangelog(existing)
const rawCommits = readOptionalFile(args.rawCommitsFile) || extractMarkedRawCommits(existing)
sections[args.platform] = renderTemplate(args.template, args)

const parts = []
if (changelog) {
  parts.push(wrapChangelog(changelog))
}
for (const platform of platformOrder) {
  if (sections[platform]?.trim()) {
    parts.push(wrapSection(platform, sections[platform].trim()))
  }
}
if (rawCommits) {
  parts.push(wrapRawCommits(rawCommits))
}

mkdirSync(dirname(args.output), { recursive: true })
writeFileSync(args.output, `${parts.join('\n\n')}\n`, 'utf8')

function renderTemplate(templatePath, values) {
  if (!existsSync(templatePath)) {
    throw new Error(`Release note template not found: ${templatePath}`)
  }
  const replacements = {
    '{{VERSION}}': values.version,
    '{{RELEASE_KIND}}': values.releaseKind,
    '{{UPDATER_CHANNEL}}': values.updaterChannel,
    '{{WINDOWS_INSTALLER}}': values.windowsInstaller,
    '{{WINDOWS_PORTABLE}}': values.windowsPortable,
    '{{WINDOWS_SIGNING_NOTE}}': values.windowsSigningNote,
    '{{MACOS_DMG}}': values.macosDmg,
    '{{MACOS_APP_ZIP}}': values.macosAppZip,
    '{{MACOS_SIGNING_NOTE}}': values.macosSigningNote
  }

  let content = readFileSync(templatePath, 'utf8')
  for (const [placeholder, value] of Object.entries(replacements)) {
    content = content.split(placeholder).join(value ?? '')
  }

  const unresolved = content.match(/{{[A-Z_]+}}/g)
  if (unresolved) {
    throw new Error(`Unresolved release note placeholders: ${[...new Set(unresolved)].join(', ')}`)
  }
  return content.trim()
}

function extractMarkedSections(body) {
  const sections = {}
  for (const platform of platformOrder) {
    const pattern = new RegExp(
      `${escapeRegExp(sectionMarker(platform, 'start'))}\\s*([\\s\\S]*?)\\s*${escapeRegExp(sectionMarker(platform, 'end'))}`,
      'm'
    )
    const match = body.match(pattern)
    if (match?.[1]?.trim()) {
      sections[platform] = match[1].trim()
    }
  }
  return sections
}

function extractMarkedChangelog(body) {
  const hiddenPattern = new RegExp(
    `<!--\\s*COGNINOTE_RELEASE_CHANGELOG:start\\s*\\r?\\n([\\s\\S]*?)\\r?\\n\\s*COGNINOTE_RELEASE_CHANGELOG:end\\s*-->`,
    'm'
  )
  const hiddenMatch = body.match(hiddenPattern)
  if (hiddenMatch?.[1]?.trim()) {
    return hiddenMatch[1].trim()
  }

  const visiblePattern = new RegExp(
    `${escapeRegExp(changelogMarker('start'))}\\s*([\\s\\S]*?)\\s*${escapeRegExp(changelogMarker('end'))}`,
    'm'
  )
  return body.match(visiblePattern)?.[1]?.trim() || ''
}

function readChangelog(changelogFile) {
  return readOptionalFile(changelogFile)
}

function extractMarkedRawCommits(body) {
  const pattern = new RegExp(
    `${escapeRegExp(rawCommitsMarker('start'))}\\s*([\\s\\S]*?)\\s*${escapeRegExp(rawCommitsMarker('end'))}`,
    'm'
  )
  return body.match(pattern)?.[1]?.trim() || ''
}

function readOptionalFile(changelogFile) {
  if (!changelogFile || !existsSync(changelogFile)) {
    return ''
  }
  return readFileSync(changelogFile, 'utf8').trim()
}

function migrateLegacySections(body) {
  const trimmed = body.trim()
  if (!trimmed || extractMarkedSections(trimmed).windows || extractMarkedSections(trimmed).macos) {
    return {}
  }

  const section = trimmed.replace(/^#\s+.+(?:\r?\n)+/, '').trim()
  if (!section) {
    return {}
  }
  const heading = trimmed.match(/^#\s+(.+)$/m)?.[1] ?? ''
  if (/windows/i.test(heading)) {
    return { windows: ensureSectionHeading('windows', section) }
  }
  if (/macos|macOS|Apple Silicon/i.test(heading)) {
    return { macos: ensureSectionHeading('macos', section) }
  }
  return {}
}

function ensureSectionHeading(platform, content) {
  if (/^##\s+/m.test(content)) {
    return content
  }
  return `## ${platformLabels[platform]}\n\n${content}`
}

function wrapSection(platform, content) {
  return `${sectionMarker(platform, 'start')}\n${content}\n${sectionMarker(platform, 'end')}`
}

function wrapChangelog(content) {
  return `<!-- COGNINOTE_RELEASE_CHANGELOG:start\n${content}\nCOGNINOTE_RELEASE_CHANGELOG:end -->`
}

function wrapRawCommits(content) {
  return `${rawCommitsMarker('start')}\n${content}\n${rawCommitsMarker('end')}`
}

function sectionMarker(platform, boundary) {
  return `<!-- COGNINOTE_RELEASE_SECTION:${platform}:${boundary} -->`
}

function changelogMarker(boundary) {
  return `<!-- COGNINOTE_RELEASE_CHANGELOG:${boundary} -->`
}

function rawCommitsMarker(boundary) {
  return `<!-- COGNINOTE_RELEASE_RAW_COMMITS:${boundary} -->`
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
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
