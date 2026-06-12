import { requestJson } from './http-client'

/**
 * 后端健康检查 API。
 *
 * <p>该接口用于工作区导航的连接状态提示，应保持轻量，不承载模型或知识库连通性测试。</p>
 */
export function getSystemStatus() {
  return requestJson('/api/system/status')
}
