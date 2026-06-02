import { jsonOptions, requestJson } from './http-client'

export function getIndexStatus() {
  return requestJson('/api/index/status')
}

export function rebuildSearchIndex() {
  return requestJson('/api/index/rebuild', { method: 'POST' })
}

export function searchKnowledge(payload) {
  return requestJson('/api/search', jsonOptions('POST', payload))
}
