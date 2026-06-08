import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getIndexStatus, rebuildSearchIndex, searchKnowledge as requestSearch } from '../api/search-api'

export const SEARCH_MODES = [
  { label: '关键词', value: 'KEYWORD' },
  { label: '向量', value: 'VECTOR' },
  { label: '混合', value: 'HYBRID' }
]

/**
 * 定义 业务 的 Pinia Store。
 * <p>集中维护响应式状态、派生值和异步动作，组件只消费 Store 暴露的接口。</p>
 */
export const useSearchStore = defineStore('search', () => {
  const indexStatus = ref(null)
  const rebuildResult = ref(null)
  const searchResult = ref(null)
  const isLoadingIndexStatus = ref(false)
  const isRebuildingIndex = ref(false)
  const isSearching = ref(false)
  const indexError = ref('')
  const searchError = ref('')
  const query = ref('')
  const mode = ref('KEYWORD')
  const topK = ref(8)

  /**
   * 加载 fetch Index Status 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
  async function fetchIndexStatus() {
    isLoadingIndexStatus.value = true
    indexError.value = ''

    try {
      indexStatus.value = await getIndexStatus()
    } catch (err) {
      indexError.value = `索引状态读取失败：${err.message}`
    } finally {
      isLoadingIndexStatus.value = false
    }
  }

  /**
   * 执行 业务 中的 ensure Index Status Loaded 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function ensureIndexStatusLoaded() {
    if (indexStatus.value || isLoadingIndexStatus.value) {
      return Promise.resolve()
    }
    return fetchIndexStatus()
  }

  /**
   * 执行 业务 中的 rebuild Index 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function rebuildIndex() {
    isRebuildingIndex.value = true
    rebuildResult.value = null
    indexError.value = ''

    try {
      rebuildResult.value = await rebuildSearchIndex()
      await fetchIndexStatus()
    } catch (err) {
      indexError.value = `重建索引失败：${err.message}`
    } finally {
      isRebuildingIndex.value = false
    }
  }

  /**
   * 执行 业务 中的 search Knowledge 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function searchKnowledge() {
    const trimmedQuery = query.value.trim()
    if (!trimmedQuery) {
      searchError.value = '请输入检索关键词'
      return
    }

    isSearching.value = true
    searchResult.value = null
    searchError.value = ''

    try {
      searchResult.value = await requestSearch({
        query: trimmedQuery,
        mode: mode.value,
        topK: Number(topK.value)
      })
    } catch (err) {
      searchError.value = `检索失败：${err.message}`
    } finally {
      isSearching.value = false
    }
  }

  return {
    indexStatus,
    rebuildResult,
    searchResult,
    isLoadingIndexStatus,
    isRebuildingIndex,
    isSearching,
    indexError,
    searchError,
    query,
    mode,
    topK,
    fetchIndexStatus,
    ensureIndexStatusLoaded,
    rebuildIndex,
    searchKnowledge
  }
})
