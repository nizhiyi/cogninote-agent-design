import { jsonOptions, requestJson, requestNoContent } from './http-client'

/**
 * 加载 list Knowledge Folders 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function listKnowledgeFolders() {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/knowledge-folders')
}

/**
 * 执行 知识库 中的 import Knowledge Folder 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function importKnowledgeFolder(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/knowledge-folders/import', jsonOptions('POST', payload))
}

/**
 * 执行 知识库 中的 rebuild Knowledge Folder 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function rebuildKnowledgeFolder(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/knowledge-folders/${id}/rebuild`, { method: 'POST' })
}

/**
 * 更新 set Knowledge Folder Enabled 对应的状态。
 * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
 */
export function setKnowledgeFolderEnabled(id, enabled) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestNoContent(`/api/knowledge-folders/${id}/enabled`, jsonOptions('PATCH', { enabled }))
}

/**
 * 删除或清理 delete Knowledge Folder 对应的数据。
 * <p>清理时同步处理本地缓存，避免界面保留过期状态。</p>
 */
export function deleteKnowledgeFolder(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestNoContent(`/api/knowledge-folders/${id}`, { method: 'DELETE' })
}
