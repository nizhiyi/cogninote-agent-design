export function jsonOptions(method, body) {
  return {
    method,
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  }
}

/**
 * 封装基础 HTTP 请求。
 * <p>统一处理 JSON 解析、空响应和后端错误消息。</p>
 */
export async function requestJson(url, options = {}) {
  // 这里进入浏览器网络请求边界，后续统一解析响应和错误。
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(payload?.message || payload?.code || `HTTP ${response.status}`)
  }

  // 后端普通 JSON API 统一返回 ApiResponse<T>。
  // 在这里集中解包，避免每个 store 都重复处理 success/code/message。
  if (payload && typeof payload === 'object' && 'success' in payload) {
    if (!payload.success) {
      throw new Error(payload.message || payload.code || '请求失败')
    }
    return payload.data
  }

  return payload
}

/**
 * 封装基础 HTTP 请求。
 * <p>统一处理 JSON 解析、空响应和后端错误消息。</p>
 */
export async function requestNoContent(url, options = {}) {
  // 这里进入浏览器网络请求边界，后续统一解析响应和错误。
  const response = await fetch(url, options)
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new Error(payload?.message || payload?.code || `HTTP ${response.status}`)
  }
}
