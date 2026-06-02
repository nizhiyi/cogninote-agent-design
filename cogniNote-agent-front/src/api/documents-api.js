import { jsonOptions, requestJson, requestNoContent } from './http-client'

export function listDocuments() {
  return requestJson('/api/documents')
}

export function ingestDocuments(payload) {
  return requestJson('/api/documents/ingest', jsonOptions('POST', payload))
}

export function deleteDocumentRecord(id) {
  return requestNoContent(`/api/documents/${id}`, { method: 'DELETE' })
}
