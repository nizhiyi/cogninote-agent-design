import { jsonOptions, requestJson } from './http-client'

/**
 * 模型配置 API。
 *
 * <p>/settings 路径服务新的设置页编辑体验，旧的顶层 CRUD 保留给兼容入口；两者最终写入同一后端配置表。</p>
 */
export function getActiveModelConfigs() {
  return requestJson('/api/model-configs/active')
}

export function listModelConfigs(role) {
  return requestJson(`/api/model-configs?role=${encodeURIComponent(role)}`)
}

export function createModelConfig(payload) {
  return requestJson('/api/model-configs', jsonOptions('POST', payload))
}

export function updateModelConfig(id, payload) {
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}`, jsonOptions('PUT', payload))
}

export function deleteModelConfig(id) {
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function activateModelConfig(id) {
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}/activate`, jsonOptions('POST', {}))
}

export function getModelConfigSettings(role) {
  return requestJson(`/api/model-configs/settings?role=${encodeURIComponent(role)}`)
}

export function createSettingsModelConfig(payload) {
  return requestJson('/api/model-configs/settings/configs', jsonOptions('POST', payload))
}

export function updateSettingsModelConfig(id, payload) {
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}`, jsonOptions('PUT', payload))
}

export function deleteSettingsModelConfig(id) {
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function activateSettingsModelConfig(id) {
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}/activate`, jsonOptions('POST', {}))
}

export function testModelConfig(payload) {
  return requestJson('/api/model-configs/test', jsonOptions('POST', payload))
}

export function fetchModelOptions(payload) {
  return requestJson('/api/model-configs/models', jsonOptions('POST', payload))
}
