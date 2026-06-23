#!/usr/bin/env node
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { mkdirSync } from 'node:fs'

const args = parseArgs(process.argv.slice(2))
const required = ['manifest', 'version', 'platform', 'url', 'signatureFile']
for (const key of required) {
  if (!args[key]) {
    throw new Error(`Missing required argument --${key}`)
  }
}

const signature = readFileSync(args.signatureFile, 'utf8').trim()
if (!signature) {
  throw new Error(`Signature file is empty: ${args.signatureFile}`)
}

const existing = existsSync(args.manifest)
  ? JSON.parse(readFileSync(args.manifest, 'utf8'))
  : null
const isSameVersion = existing?.version === args.version
const platforms = isSameVersion && existing?.platforms
  ? existing.platforms
  : {}

platforms[args.platform] = {
  signature,
  url: args.url
}

const manifest = {
  version: args.version,
  notes: readNotes(args),
  pub_date: args.pubDate || (isSameVersion ? existing?.pub_date : null) || new Date().toISOString(),
  platforms
}

mkdirSync(dirname(args.manifest), { recursive: true })
writeFileSync(args.manifest, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')

function readNotes(values) {
  if (values.notesFile && existsSync(values.notesFile)) {
    return normalizeReleaseNotes(readFileSync(values.notesFile, 'utf8'))
  }
  return values.notes || (isSameVersion ? existing?.notes : '') || ''
}

function normalizeReleaseNotes(content) {
  const changelog = extractMarkedChangelog(content)
  const notes = changelog || content
  return notes
    .replace(/^<!--\s*COGNINOTE_RELEASE_[^>]+-->\s*$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function extractMarkedChangelog(content) {
  const match = content.match(
    /<!--\s*COGNINOTE_RELEASE_CHANGELOG:start\s*-->\s*([\s\S]*?)\s*<!--\s*COGNINOTE_RELEASE_CHANGELOG:end\s*-->/m
  )
  return match?.[1]?.trim() || ''
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
