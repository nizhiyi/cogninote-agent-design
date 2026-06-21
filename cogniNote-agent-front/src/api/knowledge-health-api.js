import { requestJson } from './http-client'

export function getKnowledgeHealth() {
  return requestJson('/api/knowledge-health')
}

export function getKnowledgeFolderHealth(id) {
  return requestJson(`/api/knowledge-health/folders/${id}`)
}

export function listKnowledgeHealthRuns({ scopeType, scopeId, limit } = {}) {
  const params = new URLSearchParams()
  // 只发送有值的过滤条件，避免后端把空字符串误判为某个具体 scope。
  if (scopeType) {
    params.set('scopeType', scopeType)
  }
  if (scopeId) {
    params.set('scopeId', scopeId)
  }
  if (limit) {
    params.set('limit', String(limit))
  }

  const query = params.toString()
  return requestJson(`/api/knowledge-health/runs${query ? `?${query}` : ''}`)
}

export function listKnowledgeHealthRunsPage({ scopeType, scopeId, page, pageSize } = {}) {
  const params = new URLSearchParams()
  if (scopeType) {
    params.set('scopeType', scopeType)
  }
  if (scopeId) {
    params.set('scopeId', scopeId)
  }
  if (page) {
    params.set('page', String(page))
  }
  if (pageSize) {
    params.set('pageSize', String(pageSize))
  }

  const query = params.toString()
  return requestJson(`/api/knowledge-health/runs/page${query ? `?${query}` : ''}`)
}
