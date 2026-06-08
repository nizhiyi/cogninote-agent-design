import { jsonOptions, requestJson } from './http-client'

/**
 * 加载 get Active Model Configs 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function getActiveModelConfigs() {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/model-configs/active')
}

/**
 * 加载 list Model Configs 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function listModelConfigs(role) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs?role=${encodeURIComponent(role)}`)
}

/**
 * 创建或启动 create Model 配置 对应的前端流程。
 * <p>该方法通常会同步本地响应式状态和后端快照。</p>
 */
export function createModelConfig(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/model-configs', jsonOptions('POST', payload))
}

/**
 * 更新 update Model 配置 对应的状态。
 * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
 */
export function updateModelConfig(id, payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}`, jsonOptions('PUT', payload))
}

/**
 * 删除或清理 delete Model 配置 对应的数据。
 * <p>清理时同步处理本地缓存，避免界面保留过期状态。</p>
 */
export function deleteModelConfig(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * 执行 模型配置 中的 activate Model 配置 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function activateModelConfig(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/${encodeURIComponent(id)}/activate`, jsonOptions('POST', {}))
}

/**
 * 加载 get Model 配置 Settings 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function getModelConfigSettings(role) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/settings?role=${encodeURIComponent(role)}`)
}

/**
 * 创建或启动 create Settings Model 配置 对应的前端流程。
 * <p>该方法通常会同步本地响应式状态和后端快照。</p>
 */
export function createSettingsModelConfig(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/model-configs/settings/configs', jsonOptions('POST', payload))
}

/**
 * 更新 update Settings Model 配置 对应的状态。
 * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
 */
export function updateSettingsModelConfig(id, payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}`, jsonOptions('PUT', payload))
}

/**
 * 删除或清理 delete Settings Model 配置 对应的数据。
 * <p>清理时同步处理本地缓存，避免界面保留过期状态。</p>
 */
export function deleteSettingsModelConfig(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * 执行 模型配置 中的 activate Settings Model 配置 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function activateSettingsModelConfig(id) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson(`/api/model-configs/settings/configs/${encodeURIComponent(id)}/activate`, jsonOptions('POST', {}))
}

/**
 * 执行 模型配置 中的 test Model 配置 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export function testModelConfig(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/model-configs/test', jsonOptions('POST', payload))
}

/**
 * 加载 fetch Model Options 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function fetchModelOptions(payload) {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/model-configs/models', jsonOptions('POST', payload))
}
