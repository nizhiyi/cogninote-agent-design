import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSystemStatus } from '../api/system-api'

/**
 * 定义 业务 的 Pinia Store。
 * <p>集中维护响应式状态、派生值和异步动作，组件只消费 Store 暴露的接口。</p>
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

  /**
   * 加载 fetch Status 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
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

  /**
   * 执行 业务 中的 ensure Status Loaded 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
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
