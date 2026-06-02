import { requestJson } from './http-client'

export function getSystemStatus() {
  return requestJson('/api/system/status')
}
