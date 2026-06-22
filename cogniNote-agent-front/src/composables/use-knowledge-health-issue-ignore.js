import { ref } from 'vue'
import { issueIdentity } from '../utils/knowledge-health-issues'

const STORAGE_KEY = 'cogninote.knowledge-health.ignored-issues.v1'
const ignoredIssueKeys = ref(loadIgnoredIssueKeys())

export function useKnowledgeHealthIssueIgnore() {
  function isIssueIgnored(issue) {
    return ignoredIssueKeys.value.has(issueIdentity(issue))
  }

  function ignoreIssue(issue) {
    const next = new Set(ignoredIssueKeys.value)
    next.add(issueIdentity(issue))
    persistIgnoredIssueKeys(next)
  }

  function restoreIssue(issue) {
    const next = new Set(ignoredIssueKeys.value)
    next.delete(issueIdentity(issue))
    persistIgnoredIssueKeys(next)
  }

  function restoreAllIgnoredIssues() {
    persistIgnoredIssueKeys(new Set())
  }

  return {
    ignoredIssueKeys,
    isIssueIgnored,
    ignoreIssue,
    restoreIssue,
    restoreAllIgnoredIssues
  }
}

function loadIgnoredIssueKeys() {
  try {
    return new Set(JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]'))
  } catch {
    return new Set()
  }
}

function persistIgnoredIssueKeys(nextKeys) {
  // 忽略只是本机降噪偏好，不写入后端健康事实，避免生成另一份会腐烂的“健康状态”。
  ignoredIssueKeys.value = nextKeys
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...nextKeys]))
}
