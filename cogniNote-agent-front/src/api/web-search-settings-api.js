import { jsonOptions, requestJson } from './http-client'

/**
 * 读取联网搜索设置。
 * <p>响应只包含 API Key 是否已配置，不包含明文密钥。</p>
 */
export function getWebSearchSettings() {
  return requestJson('/api/web-search/settings')
}

/**
 * 更新联网搜索设置。
 * <p>apiKey 留空时后端沿用已保存密钥，避免前端回显或持有明文 Key。</p>
 */
export function updateWebSearchSettings(payload) {
  return requestJson('/api/web-search/settings', jsonOptions('PUT', payload))
}

/**
 * 测试当前联网搜索配置。
 * <p>测试只验证 provider 可用性，不写入聊天记录。</p>
 */
export function testWebSearchSettings() {
  return requestJson('/api/web-search/test', jsonOptions('POST', {}))
}
