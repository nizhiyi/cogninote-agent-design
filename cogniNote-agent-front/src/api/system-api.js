import { requestJson } from './http-client'

/**
 * 加载 get System Status 对应的数据。
 * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
 */
export function getSystemStatus() {
  // 后端请求统一走 API helper，避免各组件重复处理错误。
  return requestJson('/api/system/status')
}
