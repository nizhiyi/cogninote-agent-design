import { jsonOptions, requestJson, requestNoContent } from './http-client'

export function listDocuments() {
  return requestJson('/api/documents')
}

export function getDocumentChunk(chunkId) {
  // chunkId 来自后端检索结果，仍通过 encodeURIComponent 保护 URL 边界。
  return requestJson(`/api/documents/chunks/${encodeURIComponent(chunkId)}`)
}

export function ingestDocuments(payload) {
  return requestJson('/api/documents/ingest', jsonOptions('POST', payload))
}

export function deleteDocumentRecord(id) {
  return requestNoContent(`/api/documents/${id}`, { method: 'DELETE' })
}
