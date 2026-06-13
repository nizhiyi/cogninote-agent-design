import { jsonOptions, requestJson, requestNoContent } from './http-client'

/**
 * 知识库目录 API。
 *
 * <p>同步、删除、停用和重建都只影响应用元数据与索引，不会删除用户本地目录中的原始文件。</p>
 */
export function listKnowledgeFolders() {
  return requestJson('/api/knowledge-folders')
}

export function importKnowledgeFolder(payload) {
  return requestJson('/api/knowledge-folders/import', jsonOptions('POST', payload))
}

export function syncKnowledgeFolder(id) {
  return requestJson(`/api/knowledge-folders/${id}/sync`, { method: 'POST' })
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
