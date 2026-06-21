import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  cancelMaintenanceRun,
  enqueueDeleteFolder,
  enqueueFolderEnabled,
  enqueueImportFolder,
  enqueueRebuildFolder,
  enqueueRebuildIndex,
  enqueueSyncFolder,
  getMaintenanceQueue,
  streamMaintenanceRun
} from '../api/knowledge-maintenance-api'

const ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING', 'CANCELLING'])
const TERMINAL_STATUSES = new Set(['CANCELLED', 'COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED'])

/**
 * 知识库维护任务统一状态源。
 *
 * 后端的 knowledge_folder_runs 是事实源；本 store 只缓存队列快照和 SSE 事件。
 * 目录、可信状态和检索页面不能再用局部 busy 标记推断真实任务状态。
 */
export const useKnowledgeMaintenanceStore = defineStore('knowledgeMaintenance', () => {
  const currentRuns = ref([])
  const queuedRuns = ref([])
  const latestRun = ref(null)
  const error = ref('')
  const isLoadingQueue = ref(false)
  const isEnqueueing = ref(false)
  const cancellingRunIds = ref(new Set())
  let runAbortController = null

  const currentRun = computed(() => currentRuns.value[0] || null)
  const hasActiveRun = computed(() => Boolean(currentRun.value || queuedRuns.value.length))

  async function fetchQueue() {
    isLoadingQueue.value = true
    error.value = ''
    try {
      applyQueue(await getMaintenanceQueue())
      subscribeToMostRelevantRun()
    } catch (err) {
      error.value = `维护队列读取失败：${err.message}`
    } finally {
      isLoadingQueue.value = false
    }
  }

  function ensureQueueLoaded() {
    if (currentRuns.value.length || queuedRuns.value.length || latestRun.value || isLoadingQueue.value) {
      return Promise.resolve()
    }
    return fetchQueue()
  }

  async function importFolder(payload) {
    return enqueue(() => enqueueImportFolder(payload))
  }

  async function rebuildAllIndex() {
    return enqueue(enqueueRebuildIndex)
  }

  async function syncFolder(id) {
    return enqueue(() => enqueueSyncFolder(id))
  }

  async function rebuildFolder(id) {
    return enqueue(() => enqueueRebuildFolder(id))
  }

  async function setFolderEnabled(id, enabled) {
    return enqueue(() => enqueueFolderEnabled(id, enabled))
  }

  async function deleteFolder(id) {
    return enqueue(() => enqueueDeleteFolder(id))
  }

  async function enqueue(action) {
    isEnqueueing.value = true
    error.value = ''
    try {
      const run = await action()
      upsertRun(run)
      subscribeToRun(run.id)
      await fetchQueue()
      return run
    } catch (err) {
      error.value = `维护任务入队失败：${err.message}`
      throw err
    } finally {
      isEnqueueing.value = false
    }
  }

  async function cancelRun(runId) {
    if (!runId) {
      return
    }
    setCancelling(runId, true)
    error.value = ''
    try {
      await cancelMaintenanceRun(runId)
      await fetchQueue()
    } catch (err) {
      error.value = `取消维护任务失败：${err.message}`
    } finally {
      setCancelling(runId, false)
    }
  }

  function activeRunForFolder(folderId) {
    return allActiveRuns().find((run) => run.scopeType === 'KNOWLEDGE_FOLDER' && run.scopeId === folderId) || null
  }

  function activeRunForOperation(operation, scopeType = 'ALL', scopeId = null) {
    return allActiveRuns().find((run) => {
      if (run.operation !== operation || run.scopeType !== scopeType) {
        return false
      }
      return scopeType === 'ALL' || run.scopeId === scopeId
    }) || null
  }

  function isFolderBusy(folderId) {
    return Boolean(activeRunForFolder(folderId))
  }

  function isRunCancelling(runId) {
    return cancellingRunIds.value.has(runId)
  }

  function applyQueue(queue) {
    currentRuns.value = queue?.currentRuns || []
    queuedRuns.value = queue?.queuedRuns || []
    latestRun.value = queue?.latestRun || null
  }

  function upsertRun(run) {
    if (!run?.id) {
      return
    }
    currentRuns.value = currentRuns.value.filter((item) => item.id !== run.id)
    queuedRuns.value = queuedRuns.value.filter((item) => item.id !== run.id)
    if (run.status === 'RUNNING' || run.status === 'CANCELLING') {
      currentRuns.value = [run, ...currentRuns.value]
    } else if (run.status === 'QUEUED') {
      queuedRuns.value = [...queuedRuns.value, run].sort(queueSort)
    } else if (TERMINAL_STATUSES.has(run.status)) {
      latestRun.value = run
    }
  }

  function handleRunEvent(eventName, payload) {
    if (eventName === 'maintenance-run-snapshot'
        || eventName === 'maintenance-run-queued'
        || eventName === 'maintenance-run-started'
        || eventName === 'maintenance-run-progress'
        || eventName === 'maintenance-run-cancelling') {
      upsertRun(payload)
      return
    }
    if (eventName === 'maintenance-queue-updated') {
      void fetchQueue()
      return
    }
    if (eventName === 'maintenance-run-completed'
        || eventName === 'maintenance-run-failed'
        || eventName === 'maintenance-run-cancelled') {
      upsertRun(payload)
      void refreshKnowledgeSnapshots()
    }
  }

  function subscribeToMostRelevantRun() {
    const target = currentRun.value || queuedRuns.value[0]
    if (target?.id) {
      subscribeToRun(target.id)
    }
  }

  function subscribeToRun(runId) {
    if (!runId) {
      return
    }
    stopRunStream()
    runAbortController = new AbortController()
    void streamMaintenanceRun(runId, {
      signal: runAbortController.signal,
      onEvent: handleRunEvent
    }).catch((err) => {
      if (err.name === 'AbortError') {
        return
      }
      error.value = `维护任务进度连接已断开：${err.message}`
      void fetchQueue()
    })
  }

  function stopRunStream() {
    if (!runAbortController) {
      return
    }
    runAbortController.abort()
    runAbortController = null
  }

  async function refreshKnowledgeSnapshots() {
    /*
     * 维护任务终态会同时影响目录列表、健康诊断和 Lucene 统计。
     * 这里集中刷新，避免各页面各自猜测哪些快照已经过期。
     */
    const { useKnowledgeFoldersStore } = await import('./knowledge-folders')
    const { useKnowledgeHealthStore } = await import('./knowledge-health')
    const { useSearchStore } = await import('./search')
    const knowledgeStore = useKnowledgeFoldersStore()
    const healthStore = useKnowledgeHealthStore()
    const searchStore = useSearchStore()
    await Promise.all([
      fetchQueue(),
      knowledgeStore.fetchFolders(),
      healthStore.fetchHealth(),
      searchStore.fetchIndexStatus()
    ])
  }

  function allActiveRuns() {
    return [...currentRuns.value, ...queuedRuns.value].filter((run) => ACTIVE_STATUSES.has(run.status))
  }

  function setCancelling(runId, cancelling) {
    const next = new Set(cancellingRunIds.value)
    if (cancelling) {
      next.add(runId)
    } else {
      next.delete(runId)
    }
    cancellingRunIds.value = next
  }

  function queueSort(left, right) {
    return (left.queuePosition || 0) - (right.queuePosition || 0)
      || (left.queuedAt || left.createdAt || 0) - (right.queuedAt || right.createdAt || 0)
  }

  return {
    currentRuns,
    queuedRuns,
    latestRun,
    error,
    isLoadingQueue,
    isEnqueueing,
    currentRun,
    hasActiveRun,
    fetchQueue,
    ensureQueueLoaded,
    importFolder,
    rebuildAllIndex,
    syncFolder,
    rebuildFolder,
    setFolderEnabled,
    deleteFolder,
    cancelRun,
    activeRunForFolder,
    activeRunForOperation,
    isFolderBusy,
    isRunCancelling
  }
})
