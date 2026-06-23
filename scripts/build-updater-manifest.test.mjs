#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import assert from 'node:assert/strict'

const tempDir = mkdtempSync(join(tmpdir(), 'cogninote-updater-manifest-'))

try {
  const manifestPath = join(tempDir, 'latest.json')
  const windowsSigPath = join(tempDir, 'windows.sig')
  const macosSigPath = join(tempDir, 'macos.sig')
  const nextSigPath = join(tempDir, 'next.sig')
  const notesPath = join(tempDir, 'notes.md')

  writeFileSync(windowsSigPath, 'windows-signature\n', 'utf8')
  writeFileSync(macosSigPath, 'macos-signature\n', 'utf8')
  writeFileSync(nextSigPath, 'next-signature\n', 'utf8')
  writeFileSync(
    notesPath,
    [
      '<!-- COGNINOTE_RELEASE_CHANGELOG:start -->',
      '## 更新内容',
      '',
      '- 修复自动更新说明。',
      '<!-- COGNINOTE_RELEASE_CHANGELOG:end -->',
      '<!-- COGNINOTE_RELEASE_SECTION:windows:start -->',
      '## Windows x64',
      '',
      '下载安装包。',
      '<!-- COGNINOTE_RELEASE_SECTION:windows:end -->',
      '<!-- COGNINOTE_RELEASE_TECHNICAL:start -->',
      '## 技术详情',
      '',
      '- abc123 feat(updater): 这条不应进入应用内更新说明。',
      '<!-- COGNINOTE_RELEASE_TECHNICAL:end -->'
    ].join('\n'),
    'utf8'
  )

  runBuilder(
    manifestPath,
    '0.1.34',
    'windows-x86_64',
    'https://example.com/windows.exe',
    windowsSigPath,
    '2026-06-15T13:42:18.307Z'
  )
  runBuilder(manifestPath, '0.1.34', 'darwin-aarch64', 'https://example.com/macos.app.tar.gz', macosSigPath)

  const merged = readManifest(manifestPath)
  assert.equal(merged.version, '0.1.34')
  assert.equal(merged.pub_date, '2026-06-15T13:42:18.307Z')
  assert.equal(merged.platforms['windows-x86_64'].signature, 'windows-signature')
  assert.equal(merged.platforms['darwin-aarch64'].signature, 'macos-signature')
  assert.equal(merged.platforms['darwin-aarch64'].url, 'https://example.com/macos.app.tar.gz')

  runBuilder(manifestPath, '0.1.35', 'windows-x86_64', 'https://example.com/next.exe', nextSigPath)

  const nextVersion = readManifest(manifestPath)
  assert.equal(nextVersion.version, '0.1.35')
  assert.notEqual(nextVersion.pub_date, merged.pub_date)
  assert.deepEqual(Object.keys(nextVersion.platforms), ['windows-x86_64'])
  assert.equal(nextVersion.platforms['windows-x86_64'].signature, 'next-signature')

  runBuilder(
    manifestPath,
    '0.1.35',
    'darwin-aarch64',
    'https://example.com/next-macos.app.tar.gz',
    macosSigPath,
    null,
    notesPath
  )

  const notesFileVersion = readManifest(manifestPath)
  assert.equal(notesFileVersion.notes, '## 更新内容\n\n- 修复自动更新说明。')
  assert(!notesFileVersion.notes.includes('技术详情'))
  assert(!notesFileVersion.notes.includes('Windows x64'))
} finally {
  rmSync(tempDir, { recursive: true, force: true })
}

function runBuilder(manifestPath, version, platform, url, signatureFile, pubDate, notesFile) {
  const args = [
    'scripts/build-updater-manifest.mjs',
    '--manifest',
    manifestPath,
    '--version',
    version,
    '--platform',
    platform,
    '--url',
    url,
    '--signatureFile',
    signatureFile
  ]
  if (notesFile) {
    args.push('--notesFile', notesFile)
  } else {
    args.push('--notes', `CogniNote ${version}`)
  }
  if (pubDate) {
    args.push('--pubDate', pubDate)
  }
  execFileSync(process.execPath, args, { stdio: 'inherit' })
}

function readManifest(manifestPath) {
  return JSON.parse(readFileSync(manifestPath, 'utf8'))
}
