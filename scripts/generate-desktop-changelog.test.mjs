#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const scriptPath = join(repoRoot, 'scripts', 'generate-desktop-changelog.mjs')
const tempDir = mkdtempSync(join(tmpdir(), 'cogninote-changelog-'))

try {
  initRepo()
  commit('feat(ui): 初始化应用界面')
  git(['tag', 'v0.1.50'])
  commit('feat(knowledge-health): 将健康诊断问题按类别分组，支持忽略及详情弹窗')
  commit('fix(knowledge-maintenance): 修复队列状态同步与去重逻辑')
  commit('refactor(db): 移除旧版 schema 迁移逻辑，简化启动初始化')
  commit('security(updater): 强化更新包校验提示')
  commit('docs(knowledge): 更新知识库维护文档')
  commit('test(ui): 添加更新说明渲染测试')
  commit('chore(release): 更新版本号至 0.1.51，发布正式版本')

  const userNotesPath = join(tempDir, 'user-notes.md')
  const rawCommitNotesPath = join(tempDir, 'raw-commits.md')
  execFileSync(process.execPath, [
    scriptPath,
    '--output',
    userNotesPath,
    '--rawCommitsOutput',
    rawCommitNotesPath,
    '--version',
    '0.1.51',
    '--releaseTag',
    'v0.1.51'
  ], { cwd: tempDir, stdio: 'inherit' })

  const userNotes = readFileSync(userNotesPath, 'utf8')
  assert(userNotes.includes('## 知记空间 0.1.51 更新内容'))
  assert(userNotes.includes('### 新增与改进'))
  assert(userNotes.includes('### 问题修复'))
  assert(userNotes.includes('### 稳定性与性能'))
  assert(userNotes.includes('### 安全更新'))
  assert(userNotes.includes('知识库健康诊断：将健康诊断问题按类别分组，支持忽略和查看详情。'))
  assert(userNotes.includes('知识库维护：修复队列状态同步与去重逻辑。'))
  assert(userNotes.includes('数据库：移除旧版数据库兼容逻辑，简化启动初始化。'))
  assert(userNotes.includes('应用更新：强化更新包校验提示。'))
  assert(!userNotes.includes('feat('))
  assert(!userNotes.includes('docs('))
  assert(!/[0-9a-f]{7}\s/.test(userNotes))

  const rawCommitNotes = readFileSync(rawCommitNotesPath, 'utf8')
  assert(rawCommitNotes.includes('## 原始提交记录'))
  assert(rawCommitNotes.includes('docs(knowledge): 更新知识库维护文档'))
  assert(rawCommitNotes.includes('test(ui): 添加更新说明渲染测试'))
  assert(!rawCommitNotes.includes('## 技术详情'))
  assert(!rawCommitNotes.includes('变更范围'))
  assert(!rawCommitNotes.includes('chore(release): 更新版本号至 0.1.51'))
} finally {
  rmSync(tempDir, { recursive: true, force: true })
}

function initRepo() {
  git(['init'])
  git(['config', 'user.email', 'codex@example.invalid'])
  git(['config', 'user.name', 'Codex Test'])
}

function commit(message) {
  writeFileSync(join(tempDir, 'changes.txt'), `${message}\n`, { flag: 'a' })
  git(['add', 'changes.txt'])
  git(['commit', '-m', message])
}

function git(args) {
  return execFileSync('git', args, { cwd: tempDir, encoding: 'utf8' })
}
