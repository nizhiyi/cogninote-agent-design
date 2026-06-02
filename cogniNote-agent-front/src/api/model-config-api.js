import { jsonOptions, requestJson } from './http-client'

export function getModelConfig() {
  return requestJson('/api/model-config')
}

export function saveModelConfig(payload) {
  return requestJson('/api/model-config', jsonOptions('PUT', payload))
}

export function testModelConfig(payload) {
  return requestJson('/api/model-config/test', jsonOptions('POST', payload))
}

export function fetchModelOptions(payload) {
  return requestJson('/api/model-config/models', jsonOptions('POST', payload))
}
