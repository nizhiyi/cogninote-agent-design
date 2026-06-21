import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  batchDeleteKnowledgeHealthRuns,
  deleteKnowledgeHealthRun,
  getKnowledgeHealthRunDetail,
  getKnowledgeFolderHealth,
  getKnowledgeHealth,
  listKnowledgeHealthRuns,
  listKnowledgeHealthRunsPage
} from '../api/knowledge-health-api'

/**
 * 管理知识库健康快照和问题抽屉状态。
 *
 * <p>health 是全库轻量快照；folderHealth 只在用户打开问题抽屉时加载，避免首屏拉取大量文件列表。</p>
 */
export const useKnowledgeHealthStore = defineStore('knowledgeHealth', () => {
  const health = ref(null)
  const folderHealth = ref(null)
  const runs = ref([])
  const runsPage = ref({ items: [], total: 0, page: 1, pageSize: 10 })
  const selectedRunDetail = ref(null)
  const selectedFolderId = ref('')
  const isDrawerOpen = ref(false)
  const isLoading = ref(false)
  const isLoadingFolder = ref(false)
  const isLoadingRuns = ref(false)
  const error = ref('')

  // 将全库快照中的目录摘要建索引，问题抽屉可直接复用，避免为标题再发一次目录请求。
  const folderHealthById = computed(() => new Map(
    (health.value?.folders || []).map((folder) => [folder.id, folder])
  ))

  async function fetchHealth() {
    isLoading.value = true
    error.value = ''

    try {
      health.value = await getKnowledgeHealth()
    } catch (err) {
      health.value = null
      error.value = `知识库健康诊断失败：${err.message}`
    } finally {
      isLoading.value = false
    }
  }

  function ensureHealthLoaded() {
    if (health.value || isLoading.value) {
      return Promise.resolve()
    }
    return fetchHealth()
  }

  async function fetchFolderHealth(folderId) {
    isLoadingFolder.value = true
    error.value = ''

    try {
      folderHealth.value = await getKnowledgeFolderHealth(folderId)
    } catch (err) {
      folderHealth.value = null
      error.value = `目录健康详情读取失败：${err.message}`
    } finally {
      isLoadingFolder.value = false
    }
  }

  async function openFolderIssues(folderId) {
    selectedFolderId.value = folderId
    isDrawerOpen.value = true
    // 抽屉先打开再加载详情，用户能立即看到上下文和 loading，慢文件系统探针不会卡住点击反馈。
    await fetchFolderHealth(folderId)
  }

  function closeDrawer() {
    isDrawerOpen.value = false
  }

  async function fetchRuns(params = {}) {
    isLoadingRuns.value = true
    error.value = ''

    try {
      runs.value = await listKnowledgeHealthRuns(params)
    } catch (err) {
      runs.value = []
      error.value = `维护记录读取失败：${err.message}`
    } finally {
      isLoadingRuns.value = false
    }
  }

  async function fetchRunsPage(params = {}) {
    isLoadingRuns.value = true
    error.value = ''

    try {
      runsPage.value = await listKnowledgeHealthRunsPage(params)
    } catch (err) {
      runsPage.value = {
        items: [],
        total: 0,
        page: params.page || 1,
        pageSize: params.pageSize || 10
      }
      error.value = `维护记录读取失败：${err.message}`
    } finally {
      isLoadingRuns.value = false
    }
  }

  async function fetchRunDetail(runId) {
    isLoadingRuns.value = true
    error.value = ''
    selectedRunDetail.value = null

    try {
      selectedRunDetail.value = await getKnowledgeHealthRunDetail(runId)
    } catch (err) {
      selectedRunDetail.value = null
      error.value = `维护记录详情读取失败：${err.message}`
    } finally {
      isLoadingRuns.value = false
    }
  }

  async function deleteRun(runId) {
    return deleteKnowledgeHealthRun(runId)
  }

  async function batchDeleteRuns(ids) {
    return batchDeleteKnowledgeHealthRuns(ids)
  }

  return {
    health,
    folderHealth,
    runs,
    runsPage,
    selectedRunDetail,
    selectedFolderId,
    isDrawerOpen,
    isLoading,
    isLoadingFolder,
    isLoadingRuns,
    error,
    folderHealthById,
    fetchHealth,
    ensureHealthLoaded,
    fetchFolderHealth,
    openFolderIssues,
    closeDrawer,
    fetchRuns,
    fetchRunsPage,
    fetchRunDetail,
    deleteRun,
    batchDeleteRuns
  }
})
