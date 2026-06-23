#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const scriptPath = join(repoRoot, 'scripts', 'render-desktop-release-notes.mjs')
const tempDir = mkdtempSync(join(tmpdir(), 'cogninote-release-notes-'))

try {
  const existingPath = join(tempDir, 'existing.md')
  const outputPath = join(tempDir, 'release-notes.md')
  const changelogPath = join(tempDir, 'user-notes.md')
  const technicalPath = join(tempDir, 'technical-notes.md')

  writeFileSync(existingPath, '', 'utf8')
  writeFileSync(changelogPath, '## 知记空间 0.1.51 更新内容\n\n### 新增与改进\n\n- 应用更新：更新说明更容易阅读。\n', 'utf8')
  writeFileSync(technicalPath, '## 技术详情\n\n- abc123 feat(updater): 改进更新说明。\n', 'utf8')

  execFileSync(process.execPath, [
    scriptPath,
    '--existing',
    existingPath,
    '--output',
    outputPath,
    '--changelogFile',
    changelogPath,
    '--technicalChangesFile',
    technicalPath,
    '--version',
    '0.1.51',
    '--releaseKind',
    '正式版',
    '--platform',
    'windows',
    '--template',
    join(repoRoot, '.github', 'release-templates', 'desktop-windows.md'),
    '--updaterChannel',
    'stable',
    '--windowsInstaller',
    'CogniNote-0.1.51-windows-x64-signed-installer.exe',
    '--windowsPortable',
    'CogniNote-0.1.51-windows-x64-signed-portable.zip',
    '--windowsSigningNote',
    '这是已签名安装包。'
  ], { cwd: repoRoot, stdio: 'inherit' })

  const rendered = readFileSync(outputPath, 'utf8')
  const changelogIndex = rendered.indexOf('<!-- COGNINOTE_RELEASE_CHANGELOG:start -->')
  const windowsIndex = rendered.indexOf('<!-- COGNINOTE_RELEASE_SECTION:windows:start -->')
  const technicalIndex = rendered.indexOf('<!-- COGNINOTE_RELEASE_TECHNICAL:start -->')

  assert(changelogIndex >= 0)
  assert(windowsIndex > changelogIndex)
  assert(technicalIndex > windowsIndex)
  assert(rendered.includes('## Windows x64'))
  assert(rendered.includes('## 技术详情'))
} finally {
  rmSync(tempDir, { recursive: true, force: true })
}
