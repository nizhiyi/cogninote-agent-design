import { jsonOptions, requestJson } from './http-client'

/**
 * 加载 get Index Status 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function getIndexStatus() {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/index/status')
}

/**
 * 执行 业务 中的 rebuild Search Index 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function rebuildSearchIndex() {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/index/rebuild', { method: 'POST' })
}

/**
 * 执行 业务 中的 search Knowledge 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function searchKnowledge(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/search', jsonOptions('POST', payload))
}
