import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  cancelKnowledgeGraphRun,
  deleteKnowledgeGraph,
  getKnowledgeGraphStatus,
  getKnowledgeGraphView,
  listKnowledgeGraphs,
  listEdgeEvidence,
  listNodeEvidence,
  rebuildKnowledgeGraph,
  streamKnowledgeGraphRun
} from '../api/knowledge-graph-api'
import { notifyTaskCompleted } from '../utils/desktop-notifications'

/**
 * 与后端 KnowledgeGraphViewType 保持一致的视图枚举。
 *
 * <p>LIST 只是前端展示形态，实际复用 GRAPH payload，不能向后端请求 LIST 视图。</p>
 */
export const GRAPH_VIEW_OPTIONS = [
  { value: 'MINDMAP', label: '思维导图' },
  { value: 'GRAPH', label: '关系图' },
  { value: 'LIST', label: '列表' }
]

/**
 * 管理知识图谱范围、视图缓存和生成进度流。
 *
 * <p>scopeType/scopeId 决定后端图谱缓存键；同一时间只允许一个 run SSE 连接写入当前 store，
 * 避免切换范围或重新生成后旧事件覆盖新视图。</p>
 */
export const useKnowledgeGraphStore = defineStore('knowledgeGraph', () => {
  const scopeType = ref('ALL')
  const scopeId = ref('')
  const viewType = ref('MINDMAP')
  const statusSnapshot = ref(null)
  const currentRun = ref(null)
  const progress = ref(null)
  const generatedGraphs = ref([])
  // 当前真正打开的清单项；范围选择器只准备生成范围，不代表已经加载完整 payload。
  const activeGraphKey = ref('')
  // 视图按后端 viewType 缓存；LIST 复用 GRAPH payload，只改变前端渲染方式。
  const views = ref({})
  const error = ref('')
  const generatedGraphsError = ref('')
  const viewError = ref('')
  const evidenceError = ref('')
  const isLoadingGeneratedGraphs = ref(false)
  const isLoadingStatus = ref(false)
  const isLoadingView = ref(false)
  const isRebuilding = ref(false)
  const isCancelling = ref(false)
  const isEvidenceOpen = ref(false)
  const evidenceTarget = ref(null)
  const evidenceItems = ref([])
  const isLoadingEvidence = ref(false)
  // 切换 scope 或重新生成时先中断旧连接，防止旧 run 的终止事件倒灌到当前页面。
  let runAbortController = null

  const selectedScope = computed(() => ({
    scopeType: scopeType.value,
    scopeId: scopeType.value === 'ALL' ? '' : scopeId.value
  }))
  const isRunActive = computed(() => {
    const status = currentRun.value?.status
    return status === 'QUEUED' || status === 'RUNNING'
  })
  const activeViewPayload = computed(() => {
    const key = viewType.value === 'LIST' ? 'GRAPH' : viewType.value
    return views.value[key]?.payload || null
  })
  const currentScopeKey = computed(() => scopeKey(selectedScope.value))
  // statusSnapshot 只能说明后端已有视图，不能说明前端已经读取了大 payload。
  const hasOpenedCurrentScope = computed(() => activeGraphKey.value === currentScopeKey.value)
  const selectedGraphKey = computed(() => activeGraphKey.value)

  /**
   * 读取已生成图谱摘要清单。
   *
   * <p>首屏只调用该接口，避免重新进入知识图谱页时直接拉取 MINDMAP/GRAPH 大 JSON。</p>
   */
  async function loadGeneratedGraphs() {
    isLoadingGeneratedGraphs.value = true
    generatedGraphsError.value = ''
    try {
      const graphs = await listKnowledgeGraphs()
      generatedGraphs.value = Array.isArray(graphs) ? graphs : []
    } catch (err) {
      generatedGraphsError.value = `已生成图谱读取失败：${err?.message || '请稍后重试'}`
    } finally {
      isLoadingGeneratedGraphs.value = false
    }
  }

  async function loadStatus({ subscribeActive = true, loadView = true } = {}) {
    isLoadingStatus.value = true
    error.value = ''
    try {
      statusSnapshot.value = await getKnowledgeGraphStatus(selectedScope.value)
      currentRun.value = statusSnapshot.value?.latestRun || null
      if (subscribeActive && isRunActive.value) {
        subscribeToRun(currentRun.value.runId)
      }
      if (loadView && hasCurrentViewReady()) {
        await loadCurrentView()
      }
    } catch (err) {
      error.value = `知识图谱状态读取失败：${err.message}`
    } finally {
      isLoadingStatus.value = false
    }
  }

  async function loadCurrentView() {
    // 后端没有 LIST 视图，列表模式读取 GRAPH payload 后由前端转为邻接表。
    const key = viewType.value === 'LIST' ? 'GRAPH' : viewType.value
    viewError.value = ''
    // 用户只切换范围时不读取完整视图；必须由清单“查看”或生成完成显式打开当前 scope。
    if (!hasOpenedCurrentScope.value) {
      return
    }
    if (!hasViewReady(key)) {
      return
    }
    isLoadingView.value = true
    try {
      const response = await getKnowledgeGraphView(selectedScope.value, key)
      views.value = {
        ...views.value,
        [key]: response
      }
    } catch (err) {
      viewError.value = `知识图谱视图读取失败：${err.message}`
    } finally {
      isLoadingView.value = false
    }
  }

  async function rebuild() {
    isRebuilding.value = true
    error.value = ''
    viewError.value = ''
    try {
      const run = await rebuildKnowledgeGraph(selectedScope.value)
      activeGraphKey.value = currentScopeKey.value
      currentRun.value = run
      progress.value = {
        runId: run.runId,
        status: run.status,
        phase: run.status === 'QUEUED' ? 'QUEUED' : 'RUNNING',
        totalChunkCount: run.totalChunkCount || 0,
        processedChunkCount: run.processedChunkCount || 0,
        skippedChunkCount: run.skippedChunkCount || 0,
        failedChunkCount: run.failedChunkCount || 0
      }
      subscribeToRun(run.runId)
      await loadStatus({ subscribeActive: false, loadView: false })
      await loadGeneratedGraphs()
    } catch (err) {
      error.value = `知识图谱生成失败：${err.message}`
    } finally {
      isRebuilding.value = false
    }
  }

  async function cancelRun() {
    if (!currentRun.value?.runId) {
      return
    }
    isCancelling.value = true
    error.value = ''
    try {
      await cancelKnowledgeGraphRun(currentRun.value.runId)
      progress.value = {
        ...(progress.value || {}),
        runId: currentRun.value.runId,
        status: currentRun.value.status,
        phase: 'CANCELLING'
      }
    } catch (err) {
      error.value = `取消知识图谱生成失败：${err.message}`
    } finally {
      isCancelling.value = false
    }
  }

  async function deleteGeneratedGraph(graph) {
    if (!graph) {
      return
    }
    const targetScope = {
      scopeType: graph.scopeType || 'ALL',
      scopeId: graph.scopeId || ''
    }
    const targetKey = scopeKey(targetScope)
    generatedGraphsError.value = ''
    try {
      await deleteKnowledgeGraph(targetScope)
      generatedGraphs.value = generatedGraphs.value.filter((item) => scopeKey(item) !== targetKey)
      if (activeGraphKey.value === targetKey || currentScopeKey.value === targetKey) {
        resetCurrentGraphState()
      }
    } catch (err) {
      throw err
    }
  }

  function stopRunStream() {
    if (!runAbortController) {
      return
    }
    runAbortController.abort()
    runAbortController = null
  }

  function subscribeToRun(runId) {
    if (!runId) {
      return
    }
    stopRunStream()
    runAbortController = new AbortController()
    void streamKnowledgeGraphRun(runId, {
      signal: runAbortController.signal,
      onEvent: handleRunEvent
    }).catch((err) => {
      if (err.name === 'AbortError') {
        return
      }
      error.value = `知识图谱进度连接已断开：${err.message}`
      void loadStatus({ subscribeActive: false, loadView: true })
    })
  }

  /**
   * 将后端 SSE 事件折叠成本地 run/progress/view 状态。
   *
   * <p>终止事件只更新即时进度，随后重新拉取 status，确保前端拿到后端最终落库后的节点、边和视图状态。</p>
   */
  function handleRunEvent(eventName, payload) {
    if (eventName === 'graph-run-snapshot') {
      currentRun.value = payload
      return
    }
    if (eventName === 'graph-run-started' || eventName === 'graph-run-progress') {
      progress.value = payload
      return
    }
    if (eventName === 'graph-run-view-ready') {
      if (payload?.viewType === 'MINDMAP' || payload?.viewType === 'GRAPH') {
        void loadCurrentView()
      }
      return
    }
    if (eventName === 'graph-run-completed') {
      currentRun.value = {
        ...(currentRun.value || {}),
        runId: payload.runId,
        status: payload.status,
        extractedNodeCount: payload.nodeCount,
        extractedEdgeCount: payload.edgeCount
      }
      progress.value = {
        ...(progress.value || {}),
        runId: payload.runId,
        status: payload.status,
        phase: 'COMPLETED'
      }
      void notifyTaskCompleted({
        title: '知识图谱生成完成',
        body: buildGraphCompletedNotification(payload)
      })
      activeGraphKey.value = currentScopeKey.value
      void loadStatus({ subscribeActive: false, loadView: true })
      void loadGeneratedGraphs()
      return
    }
    if (eventName === 'graph-run-failed' || eventName === 'graph-run-cancelled') {
      currentRun.value = {
        ...(currentRun.value || {}),
        runId: payload.runId,
        status: payload.status,
        errorMessage: payload.message || currentRun.value?.errorMessage
      }
      progress.value = {
        ...(progress.value || {}),
        runId: payload.runId,
        status: payload.status,
        phase: payload.status
      }
      void loadStatus({ subscribeActive: false, loadView: true })
    }
  }

  /**
   * 切换待生成范围。
   *
   * 该方法只准备生成上下文并清空旧视图；历史图谱查看必须走 openGeneratedGraph，避免范围选择器
   * 无意触发完整 payload 读取。
   */
  function setScopeForGeneration(nextScopeType, nextScopeId = '') {
    stopRunStream()
    scopeType.value = nextScopeType || 'ALL'
    scopeId.value = scopeType.value === 'ALL' ? '' : nextScopeId
    resetCurrentGraphState()
  }

  function resetCurrentGraphState() {
    // scope 是图谱缓存键的一部分；只要切换范围或删除当前图谱，就不能继续展示旧范围的状态和视图。
    activeGraphKey.value = ''
    statusSnapshot.value = null
    currentRun.value = null
    views.value = {}
    progress.value = null
  }

  async function selectScope(nextScopeType, nextScopeId = '') {
    setScopeForGeneration(nextScopeType, nextScopeId)
    // 范围选择器服务于新建/重新生成；查看历史图谱必须从清单入口触发，保持两段式加载。
    await loadStatus({ loadView: false })
  }

  /**
   * 打开清单中的已生成图谱。
   *
   * <p>只有该入口会把 scope 标记为 activeGraphKey，并触发完整视图读取。</p>
   */
  async function openGeneratedGraph(graph) {
    if (!graph) {
      return
    }
    // 清单点击才进入完整图谱读取；首屏只拿摘要，避免应用启动后拉取大 payload。
    setScopeForGeneration(graph.scopeType || 'ALL', graph.scopeId || '')
    activeGraphKey.value = currentScopeKey.value
    await loadStatus({ loadView: true })
  }

  /**
   * 打开节点或边的证据抽屉。
   *
   * <p>图谱视图只保存证据 id 和摘要，完整 chunk 内容由抽屉按需回查，避免初次加载大图时拉取过多文本。</p>
   */
  async function openEvidence(target) {
    evidenceTarget.value = target
    evidenceItems.value = []
    evidenceError.value = ''
    isEvidenceOpen.value = true
    isLoadingEvidence.value = true
    try {
      evidenceItems.value = target.type === 'edge'
        ? await listEdgeEvidence(target.id)
        : await listNodeEvidence(target.id)
    } catch (err) {
      evidenceError.value = `证据读取失败：${err.message}`
    } finally {
      isLoadingEvidence.value = false
    }
  }

  function closeEvidence() {
    isEvidenceOpen.value = false
  }

  function hasCurrentViewReady() {
    return hasViewReady(viewType.value === 'LIST' ? 'GRAPH' : viewType.value)
  }

  function hasCurrentViewLoaded() {
    return Boolean(activeViewPayload.value)
  }

  function hasViewReady(key) {
    if (!statusSnapshot.value) {
      return false
    }
    if (key === 'MINDMAP') {
      return statusSnapshot.value.mindmapReady
    }
    if (key === 'GRAPH') {
      return statusSnapshot.value.graphReady
    }
    return false
  }

  function buildGraphCompletedNotification(payload) {
    const nodeCount = payload?.nodeCount || 0
    const edgeCount = payload?.edgeCount || 0
    return `已生成 ${nodeCount} 个节点、${edgeCount} 条关系，可以回到知识图谱查看。`
  }

  function scopeKey(scope) {
    const type = scope?.scopeType || 'ALL'
    return `${type}:${type === 'ALL' ? '' : scope?.scopeId || ''}`
  }

  return {
    scopeType,
    scopeId,
    viewType,
    statusSnapshot,
    currentRun,
    progress,
    generatedGraphs,
    views,
    error,
    generatedGraphsError,
    viewError,
    evidenceError,
    isLoadingGeneratedGraphs,
    isLoadingStatus,
    isLoadingView,
    isRebuilding,
    isCancelling,
    isEvidenceOpen,
    evidenceTarget,
    evidenceItems,
    isLoadingEvidence,
    selectedScope,
    isRunActive,
    activeViewPayload,
    selectedGraphKey,
    loadGeneratedGraphs,
    loadStatus,
    loadCurrentView,
    rebuild,
    cancelRun,
    deleteGeneratedGraph,
    setScopeForGeneration,
    selectScope,
    openGeneratedGraph,
    openEvidence,
    closeEvidence,
    hasCurrentViewReady,
    hasCurrentViewLoaded
  }
})
