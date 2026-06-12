import { jsonOptions, requestJson } from './http-client'

/**
 * 搜索索引 API。
 *
 * <p>重建索引会从后端 SQLite 解析结果重新写 Lucene，前端调用后需要刷新索引状态。</p>
 */
export function getIndexStatus() {
  return requestJson('/api/index/status')
}

export function rebuildSearchIndex() {
  return requestJson('/api/index/rebuild', { method: 'POST' })
}

export function searchKnowledge(payload) {
  return requestJson('/api/search', jsonOptions('POST', payload))
}
