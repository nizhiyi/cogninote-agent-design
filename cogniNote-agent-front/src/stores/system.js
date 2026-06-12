import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSystemStatus } from '../api/system-api'

/**
 * 管理后端连接状态。
 *
 * <p>该 store 只表示 Spring Boot API 是否可用，不代表模型服务、Embedding 或本地知识库索引可用。</p>
 */
export const useSystemStore = defineStore('system', () => {
  const status = ref(null)
  const isLoading = ref(false)
  const error = ref('')

  const connectionLabel = computed(() => {
    if (isLoading.value) {
      return '连接中'
    }
    return error.value ? '未连接' : '已连接'
  })

  async function fetchStatus() {
    isLoading.value = true
    error.value = ''

    try {
      status.value = await getSystemStatus()
    } catch (err) {
      error.value = `后端服务暂不可用：${err.message}`
    } finally {
      isLoading.value = false
    }
  }

  function ensureStatusLoaded() {
    if (status.value || isLoading.value) {
      return Promise.resolve()
    }
    return fetchStatus()
  }

  return {
    status,
    isLoading,
    error,
    connectionLabel,
    fetchStatus,
    ensureStatusLoaded
  }
})
