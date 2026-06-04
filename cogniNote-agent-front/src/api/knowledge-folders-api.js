import { jsonOptions, requestJson, requestNoContent } from './http-client'

export function listKnowledgeFolders() {
  return requestJson('/api/knowledge-folders')
}

export function importKnowledgeFolder(payload) {
  return requestJson('/api/knowledge-folders/import', jsonOptions('POST', payload))
}

export function rebuildKnowledgeFolder(id) {
  return requestJson(`/api/knowledge-folders/${id}/rebuild`, { method: 'POST' })
}

export function setKnowledgeFolderEnabled(id, enabled) {
  return requestNoContent(`/api/knowledge-folders/${id}/enabled`, jsonOptions('PATCH', { enabled }))
}

export function deleteKnowledgeFolder(id) {
  return requestNoContent(`/api/knowledge-folders/${id}`, { method: 'DELETE' })
}
