<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Database, FileText, Folder, Network, Play, RefreshCw, Search, Square, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import GraphAdjacencyList from './graph-adjacency-list.vue'
import GraphEvidenceDrawer from './graph-evidence-drawer.vue'
import GraphViewer from './graph-viewer.vue'
import MindmapViewer from './mindmap-viewer.vue'
import SegmentedControl from './segmented-control.vue'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { GRAPH_VIEW_OPTIONS, useKnowledgeGraphStore } from '../stores/knowledge-graph'
import { ensureSystemNotificationPermission } from '../utils/desktop-notifications'
import { formatTime } from '../utils/formatters'

const knowledgeStore = useKnowledgeFoldersStore()
const graphStore = useKnowledgeGraphStore()
const isGraphDetailDialogOpen = ref(false)
const activeGraphMode = ref('existing')
const generatedGraphKeyword = ref('')
const generatedGraphScopeFilter = ref('ALL_TYPES')
const generatedGraphViewFilter = ref('ALL_VIEWS')
const lastCompletedRunId = ref('')
const deletingGraphKey = ref('')
const rebuildReminderMessage = '当前范围可能包含较多资料，生成知识图谱需要调用模型抽取并合并关系，耗时会随数据量、模型能力和网络状态变化。生成期间可以随时取消。'

/**
 * 知识图谱管理面板。
 *
 * <p>面板负责让 scope 选择始终指向一个可生成的全部、目录或文件范围；实际 run 状态、SSE 订阅和视图缓存都归
 * knowledgeGraph store 管理。</p>
 */
const scopeTypeOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'KNOWLEDGE_FOLDER', label: '目录' },
  { value: 'DOCUMENT', label: '文件' }
]
const generatedGraphScopeOptions = [
  { value: 'ALL_TYPES', label: '全部类型' },
  ...scopeTypeOptions
]
const generatedGraphViewOptions = [
  { value: 'ALL_VIEWS', label: '全部视图' },
  { value: 'MINDMAP', label: '有思维导图' },
  { value: 'GRAPH', label: '有关系图' }
]
const graphModeOptions = [
  { value: 'generate', label: '生成知识图谱', icon: Play },
  { value: 'existing', label: '已有知识图谱', icon: Database }
]
const folderOptions = computed(() => knowledgeStore.folders.filter((folder) => folder.enabled))
const parsedFolderOptions = computed(() =>
  knowledgeStore.folders
    .map((folder) => ({
      ...folder,
      documents: (folder.documents || []).filter((document) => document.status === 'PARSED')
    }))
    .filter((folder) => folder.documents.length)
)
const unassignedParsedDocuments = computed(() =>
  knowledgeStore.unassignedDocuments.filter((document) => document.status === 'PARSED')
)
const documentFolderOptions = computed(() => [
  ...parsedFolderOptions.value,
  ...(unassignedParsedDocuments.value.length
    ? [{
        id: 'unassigned',
        displayName: '未归属文件',
        folderPath: '未归属到目录的文件',
        documents: unassignedParsedDocuments.value
      }]
    : [])
])
const selectedDocumentFolderId = ref('')
const selectedDocumentFolder = computed(() =>
  documentFolderOptions.value.find((folder) => folder.id === selectedDocumentFolderId.value) || null
)
const documentOptionsForSelectedFolder = computed(() => selectedDocumentFolder.value?.documents || [])
const canGenerate = computed(() => graphStore.scopeType === 'ALL' || Boolean(graphStore.scopeId))
const progressPercent = computed(() => {
  const total = graphStore.progress?.totalChunkCount || graphStore.currentRun?.totalChunkCount || 0
  const processed = graphStore.progress?.processedChunkCount || graphStore.currentRun?.processedChunkCount || 0
  if (!total) {
    return graphStore.isRunActive ? 5 : 0
  }
  return Math.min(100, Math.round((processed / total) * 100))
})
const runStatusText = computed(() => {
  const phase = graphStore.progress?.phase || graphStore.currentRun?.status || 'IDLE'
  const labels = {
    QUEUED: '等待中',
    EXTRACTING: '抽取中',
    MERGING: '合并中',
    CANCELLING: '取消中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAILED: '失败',
    RUNNING: '运行中'
  }
  return labels[phase] || phase
})
const failedMessage = computed(() => graphStore.currentRun?.status === 'FAILED'
  ? graphStore.currentRun.errorMessage || '知识图谱生成失败'
  : '')
const currentViewLabel = computed(() =>
  GRAPH_VIEW_OPTIONS.find((option) => option.value === graphStore.viewType)?.label || '图谱视图'
)
const visibleGeneratedGraphs = computed(() =>
  graphStore.generatedGraphs.filter((graph) => {
    const keyword = generatedGraphKeyword.value.trim().toLowerCase()
    const haystack = [
      graph.scopeName,
      graph.scopeSubtitle,
      graphTypeLabel(graph.scopeType)
    ].filter(Boolean).join(' ').toLowerCase()
    const matchesKeyword = !keyword || haystack.includes(keyword)
    const matchesScope = generatedGraphScopeFilter.value === 'ALL_TYPES'
      || graph.scopeType === generatedGraphScopeFilter.value
    const matchesView = generatedGraphViewFilter.value === 'ALL_VIEWS'
      || (generatedGraphViewFilter.value === 'MINDMAP' && graph.mindmapReady)
      || (generatedGraphViewFilter.value === 'GRAPH' && graph.graphReady)
    return matchesKeyword && matchesScope && matchesView
  })
)
const generatedGraphStatsText = computed(() =>
  `${visibleGeneratedGraphs.value.length} / ${graphStore.generatedGraphs.length} 个图谱`
)
const selectedScopeOption = computed(() =>
  graphStore.scopeType === 'KNOWLEDGE_FOLDER'
    ? folderOptions.value.find((option) => option.id === graphStore.scopeId) || null
    : documentOptionsForSelectedFolder.value.find((option) => option.id === graphStore.scopeId) || null
)
const selectedScopeName = computed(() => {
  if (graphStore.scopeType === 'ALL') {
    return '全部'
  }
  return selectedScopeOption.value?.displayName || selectedScopeOption.value?.fileName || '未选择'
})
const selectedScopeSubtitle = computed(() => {
  if (graphStore.scopeType === 'ALL') {
    return '不会限定到某个目录或文件'
  }
  return selectedScopeOption.value?.folderPath || selectedScopeOption.value?.sourcePath || ''
})

onMounted(async () => {
  await knowledgeStore.ensureFoldersLoaded()
  await ensureScopeSelection()
  // 重新进入页面时只恢复轻量清单；完整图谱必须等用户点击“查看”后再加载。
  await graphStore.loadGeneratedGraphs()
})

watch(
  () => graphStore.viewType,
  () => {
    // 切换展示类型不改变图谱 scope，只按需读取对应后端视图或复用 GRAPH payload。
    void graphStore.loadCurrentView()
  }
)

watch(
  () => [graphStore.scopeType, graphStore.scopeId],
  ([scopeType, scopeId]) => {
    if (scopeType === 'DOCUMENT' && scopeId) {
      syncDocumentFolderSelection(scopeId)
    }
  }
)

watch(
  () => graphStore.progress?.phase,
  (phase) => {
    if (phase === 'COMPLETED') {
      // 完成提示独立于进度状态更新；这里不阻塞 SSE 状态落库后的刷新流程。
      void notifyGraphCompleted()
    }
  }
)

function ensureScopeSelection() {
  if (graphStore.scopeType === 'DOCUMENT' && graphStore.scopeId) {
    syncDocumentFolderSelection(graphStore.scopeId)
  } else {
    ensureDocumentFolderSelection()
  }
  // 默认选中当前可用的第一项，避免用户切到目录/文件范围后生成按钮处于无效空 scope。
  if (graphStore.scopeType === 'KNOWLEDGE_FOLDER' && !graphStore.scopeId && folderOptions.value.length) {
    graphStore.setScopeForGeneration('KNOWLEDGE_FOLDER', folderOptions.value[0].id)
  }
  if (graphStore.scopeType === 'DOCUMENT' && !graphStore.scopeId && documentOptionsForSelectedFolder.value.length) {
    graphStore.setScopeForGeneration('DOCUMENT', documentOptionsForSelectedFolder.value[0].id)
  }
}

function handleScopeTypeChange(value) {
  if (value === 'KNOWLEDGE_FOLDER') {
    graphStore.setScopeForGeneration(value, folderOptions.value[0]?.id || '')
    return
  }
  if (value === 'DOCUMENT') {
    ensureDocumentFolderSelection()
    graphStore.setScopeForGeneration(value, documentOptionsForSelectedFolder.value[0]?.id || '')
    return
  }
  graphStore.setScopeForGeneration('ALL', '')
}

function handleFolderScopeChange(value) {
  graphStore.setScopeForGeneration('KNOWLEDGE_FOLDER', value)
}

function handleDocumentFolderChange(value) {
  selectedDocumentFolderId.value = value
  graphStore.setScopeForGeneration('DOCUMENT', documentOptionsForSelectedFolder.value[0]?.id || '')
}

function handleDocumentScopeChange(value) {
  graphStore.setScopeForGeneration('DOCUMENT', value)
}

function ensureDocumentFolderSelection() {
  if (selectedDocumentFolder.value) {
    return
  }
  // 文件生成必须先落到某个目录分组；旧版散落文件保留在“未归属文件”分组里兼容历史数据。
  selectedDocumentFolderId.value = documentFolderOptions.value[0]?.id || ''
}

function syncDocumentFolderSelection(documentId) {
  const folder = documentFolderOptions.value.find((option) =>
    (option.documents || []).some((document) => document.id === documentId)
  )
  selectedDocumentFolderId.value = folder?.id || documentFolderOptions.value[0]?.id || ''
}

async function refreshGraphSummaries() {
  await graphStore.loadGeneratedGraphs()
}

function switchGraphMode(mode) {
  activeGraphMode.value = mode
  if (mode === 'existing' && !graphStore.generatedGraphs.length && !graphStore.isLoadingGeneratedGraphs) {
    void graphStore.loadGeneratedGraphs()
  }
}

async function handleRegenerateGraph(graph) {
  if (!graph) {
    return
  }
  if (graph.scopeType === 'DOCUMENT') {
    syncDocumentFolderSelection(graph.scopeId)
  }
  // 清单里的“重新生成”必须先同步 scope，避免沿用工具栏上另一个待生成范围。
  graphStore.setScopeForGeneration(graph.scopeType || 'ALL', graph.scopeId || '')
  await handleRebuildWithReminder()
}

async function handleRebuildWithReminder() {
  if (!canGenerate.value || graphStore.isRunActive || graphStore.isRebuilding) {
    return
  }

  try {
    await ElMessageBox.confirm(
      rebuildReminderMessage,
      '温馨提示',
      {
        confirmButtonText: '继续生成',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    activeGraphMode.value = 'generate'
    void ensureSystemNotificationPermission()
    await graphStore.rebuild()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      throw err
    }
  }
}

async function handleOpenGeneratedGraph(graph) {
  if (!graph) {
    return
  }
  await graphStore.openGeneratedGraph(graph)
  isGraphDetailDialogOpen.value = true
}

async function handleDeleteGeneratedGraph(graph) {
  if (!graph || deletingGraphKey.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定彻底删除“${graph.scopeName || graphTypeLabel(graph.scopeType)}”的知识图谱吗？该操作会删除图谱节点、关系、证据、视图和运行记录，不会删除原始目录或文件。`,
      '删除知识图谱',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
    deletingGraphKey.value = graphKey(graph)
    await graphStore.deleteGeneratedGraph(graph)
    if (graphStore.selectedGraphKey === '') {
      isGraphDetailDialogOpen.value = false
    }
    ElMessage.success('知识图谱已删除')
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.message || '知识图谱删除失败')
    }
  } finally {
    deletingGraphKey.value = ''
  }
}

async function notifyGraphCompleted() {
  const runId = graphStore.progress?.runId || graphStore.currentRun?.runId || ''
  if (runId && runId === lastCompletedRunId.value) {
    return
  }
  lastCompletedRunId.value = runId

  try {
    await ElMessageBox.confirm(
      buildGraphCompletedMessage(),
      '知识图谱生成完成',
      {
        confirmButtonText: '立即打开',
        cancelButtonText: '稍后查看',
        type: 'success'
      }
    )
    await openCurrentGeneratedGraph()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      throw err
    }
  }
}

async function openCurrentGeneratedGraph() {
  activeGraphMode.value = 'existing'
  await graphStore.loadGeneratedGraphs()
  const currentGraph = graphStore.generatedGraphs.find((graph) => graphKey(graph) === graphStore.selectedGraphKey)
  if (currentGraph) {
    await handleOpenGeneratedGraph(currentGraph)
    return
  }
  await graphStore.loadStatus({ subscribeActive: false, loadView: true })
  isGraphDetailDialogOpen.value = true
}

function buildGraphCompletedMessage() {
  const nodeCount = graphStore.currentRun?.extractedNodeCount || graphStore.statusSnapshot?.nodeCount || 0
  const edgeCount = graphStore.currentRun?.extractedEdgeCount || graphStore.statusSnapshot?.edgeCount || 0
  return `已生成 ${nodeCount} 个节点、${edgeCount} 条关系。`
}

function graphKey(graph) {
  const type = graph?.scopeType || 'ALL'
  return `${type}:${type === 'ALL' ? '' : graph?.scopeId || ''}`
}

function graphTypeLabel(scopeType) {
  const labels = {
    ALL: '全部',
    KNOWLEDGE_FOLDER: '目录',
    DOCUMENT: '文件'
  }
  return labels[scopeType] || '图谱'
}

function graphIcon(scopeType) {
  if (scopeType === 'KNOWLEDGE_FOLDER') {
    return Folder
  }
  if (scopeType === 'DOCUMENT') {
    return FileText
  }
  return Database
}
</script>

<template>
  <section class="knowledge-pane knowledge-pane--graph" aria-label="知识图谱">
    <header class="knowledge-pane__header knowledge-pane__header--compact">
      <div>
        <p class="eyebrow">知识图谱</p>
        <h3>图谱</h3>
        <p class="muted-text">
          {{ graphStore.statusSnapshot?.nodeCount || 0 }} 节点 ·
          {{ graphStore.statusSnapshot?.edgeCount || 0 }} 关系 ·
          更新 {{ formatTime(graphStore.statusSnapshot?.generatedAt) }}
        </p>
      </div>
      <div class="header-actions graph-header-actions">
        <section class="graph-mode-tabs" aria-label="知识图谱页面模式">
          <button
            v-for="option in graphModeOptions"
            :key="option.value"
            type="button"
            :class="{ active: activeGraphMode === option.value }"
            @click="switchGraphMode(option.value)"
          >
            <component :is="option.icon" aria-hidden="true" />
            <span>{{ option.label }}</span>
          </button>
        </section>
        <el-button
          :loading="graphStore.isLoadingStatus"
          aria-label="刷新当前图谱"
          title="刷新当前图谱"
          @click="graphStore.loadStatus"
        >
          <RefreshCw aria-hidden="true" />
        </el-button>
      </div>
    </header>

    <section v-if="activeGraphMode === 'generate'" class="graph-mode-panel graph-mode-panel--generate" aria-label="生成知识图谱">
      <section class="graph-generate-panel" aria-label="生成配置">
        <header class="graph-mode-panel__header">
          <div>
            <p class="eyebrow">生成知识图谱</p>
            <h4>选择要抽取的范围</h4>
          </div>
          <div class="graph-mode-panel__actions">
            <el-button
              type="primary"
              :disabled="!canGenerate || graphStore.isRunActive"
              :loading="graphStore.isRebuilding"
              @click="handleRebuildWithReminder"
            >
              <Play aria-hidden="true" />
              <span>生成</span>
            </el-button>
            <el-button
              v-if="graphStore.isRunActive"
              :loading="graphStore.isCancelling"
              @click="graphStore.cancelRun"
            >
              <Square aria-hidden="true" />
              <span>取消</span>
            </el-button>
          </div>
        </header>

        <div class="graph-generate-panel__body">
          <section class="graph-toolbar graph-toolbar--generate" aria-label="生成范围">
            <div class="field graph-scope-type-field">
              <span>范围</span>
              <div class="graph-scope-segmented" role="group" aria-label="生成范围类型">
                <button
                  v-for="option in scopeTypeOptions"
                  :key="option.value"
                  type="button"
                  :class="{ active: graphStore.scopeType === option.value }"
                  @click="handleScopeTypeChange(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <label v-if="graphStore.scopeType === 'KNOWLEDGE_FOLDER'" class="field graph-toolbar__field graph-toolbar__field--wide">
              <span>目录</span>
              <el-select
                v-model="graphStore.scopeId"
                filterable
                placeholder="选择目录"
                @change="handleFolderScopeChange"
              >
                <el-option
                  v-for="option in folderOptions"
                  :key="option.id"
                  :label="option.displayName"
                  :value="option.id"
                />
              </el-select>
            </label>

            <template v-else-if="graphStore.scopeType === 'DOCUMENT'">
              <label class="field graph-toolbar__field graph-toolbar__field--wide">
                <span>所在目录</span>
                <el-select
                  v-model="selectedDocumentFolderId"
                  filterable
                  placeholder="先选择目录"
                  @change="handleDocumentFolderChange"
                >
                  <el-option
                    v-for="folder in documentFolderOptions"
                    :key="folder.id"
                    :label="folder.displayName"
                    :value="folder.id"
                  />
                </el-select>
              </label>

              <label class="field graph-toolbar__field graph-toolbar__field--wide">
                <span>文件</span>
                <el-select
                  v-model="graphStore.scopeId"
                  filterable
                  placeholder="再选择文件"
                  :disabled="!documentOptionsForSelectedFolder.length"
                  @change="handleDocumentScopeChange"
                >
                  <el-option
                    v-for="option in documentOptionsForSelectedFolder"
                    :key="option.id"
                    :label="option.fileName"
                    :value="option.id"
                  />
                </el-select>
              </label>
            </template>
          </section>

          <section class="graph-generate-summary">
            <Network aria-hidden="true" />
            <div>
              <strong>{{ selectedScopeName }}</strong>
              <p>{{ selectedScopeSubtitle || '生成完成后会出现在“已有知识图谱”中。' }}</p>
            </div>
          </section>
        </div>
      </section>

      <el-alert
        v-if="graphStore.error"
        class="settings-inline-alert"
        type="error"
        :title="graphStore.error"
        :closable="false"
        show-icon
      />

      <section v-if="graphStore.isRunActive" class="graph-run-panel" aria-label="图谱生成进度">
        <div>
          <Network aria-hidden="true" />
          <strong>{{ runStatusText }}</strong>
        </div>
        <el-progress :percentage="progressPercent" :stroke-width="10" />
        <p>
          已处理 {{ graphStore.progress?.processedChunkCount || graphStore.currentRun?.processedChunkCount || 0 }} /
          {{ graphStore.progress?.totalChunkCount || graphStore.currentRun?.totalChunkCount || 0 }} chunks ·
          跳过 {{ graphStore.progress?.skippedChunkCount || graphStore.currentRun?.skippedChunkCount || 0 }} ·
          失败 {{ graphStore.progress?.failedChunkCount || graphStore.currentRun?.failedChunkCount || 0 }}
        </p>
      </section>

      <el-alert
        v-else-if="failedMessage"
        class="settings-inline-alert"
        type="error"
        :title="failedMessage"
        :closable="false"
        show-icon
      />
    </section>

    <section v-else class="graph-mode-panel" aria-label="已有知识图谱">
      <section class="generated-graphs" aria-label="已生成图谱">
        <header class="generated-graphs__header">
          <div>
            <p class="eyebrow">已有知识图谱</p>
            <h4>图谱列表</h4>
            <p>{{ generatedGraphStatsText }}</p>
          </div>
          <el-button
            :loading="graphStore.isLoadingGeneratedGraphs"
            aria-label="刷新已生成图谱清单"
            title="刷新已生成图谱清单"
            @click="refreshGraphSummaries"
          >
            <RefreshCw aria-hidden="true" />
          </el-button>
        </header>

        <el-alert
          v-if="graphStore.generatedGraphsError"
          class="settings-inline-alert"
          type="error"
          :title="graphStore.generatedGraphsError"
          :closable="false"
          show-icon
        />

        <section class="generated-graphs__filters" aria-label="已生成图谱筛选">
          <label class="field generated-graphs__search">
            <span>搜索</span>
            <el-input
              v-model="generatedGraphKeyword"
              clearable
              placeholder="搜索名称或路径"
            >
              <template #prefix>
                <Search aria-hidden="true" />
              </template>
            </el-input>
          </label>

          <label class="field generated-graphs__filter">
            <span>范围</span>
            <el-select v-model="generatedGraphScopeFilter">
              <el-option
                v-for="option in generatedGraphScopeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>

          <label class="field generated-graphs__filter">
            <span>视图</span>
            <el-select v-model="generatedGraphViewFilter">
              <el-option
                v-for="option in generatedGraphViewOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>
        </section>

        <p v-if="graphStore.isLoadingGeneratedGraphs" class="panel-message">正在读取已生成图谱...</p>
        <section v-else-if="visibleGeneratedGraphs.length" class="generated-graphs__list">
          <article
            v-for="graph in visibleGeneratedGraphs"
            :key="graphKey(graph)"
            class="generated-graph-card"
            :class="{ 'generated-graph-card--active': graphKey(graph) === graphStore.selectedGraphKey }"
          >
            <component :is="graphIcon(graph.scopeType)" class="generated-graph-card__icon" aria-hidden="true" />
            <div class="generated-graph-card__main">
              <div class="generated-graph-card__title-line">
                <span class="status-chip status-chip--graph">{{ graphTypeLabel(graph.scopeType) }}</span>
                <strong>{{ graph.scopeName }}</strong>
              </div>
              <p>{{ graph.scopeSubtitle || '无路径信息' }}</p>
              <div class="generated-graph-card__meta">
                <span>{{ graph.nodeCount || 0 }} 节点</span>
                <span>{{ graph.edgeCount || 0 }} 关系</span>
                <span>更新 {{ formatTime(graph.generatedAt) }}</span>
              </div>
            </div>
            <div class="generated-graph-card__actions">
              <el-button type="primary" @click="handleOpenGeneratedGraph(graph)">查看</el-button>
              <el-button @click="handleRegenerateGraph(graph)">重新生成</el-button>
              <el-button
                type="danger"
                plain
                :loading="deletingGraphKey === graphKey(graph)"
                @click="handleDeleteGeneratedGraph(graph)"
              >
                <Trash2 aria-hidden="true" />
                <span>删除</span>
              </el-button>
            </div>
          </article>
        </section>
        <section v-else class="generated-graphs__empty">
          <Network aria-hidden="true" />
          <span>{{ graphStore.generatedGraphs.length ? '没有匹配的图谱' : '还没有已生成图谱' }}</span>
        </section>
      </section>
    </section>

    <el-dialog
      v-model="isGraphDetailDialogOpen"
      class="graph-fullscreen-dialog"
      fullscreen
      destroy-on-close
      :title="`知识图谱 · ${currentViewLabel}`"
    >
      <section class="graph-fullscreen-dialog__body" aria-label="全屏图谱探索">
        <header class="graph-detail-dialog__toolbar">
          <SegmentedControl v-model="graphStore.viewType" :options="GRAPH_VIEW_OPTIONS" label="图谱视图" />
          <p v-if="graphStore.isLoadingView" class="panel-message">正在读取图谱视图...</p>
          <el-alert
            v-else-if="graphStore.viewError"
            class="settings-inline-alert graph-detail-dialog__alert"
            type="error"
            :title="graphStore.viewError"
            :closable="false"
            show-icon
          />
        </header>
        <section v-if="!graphStore.isLoadingView && !graphStore.hasCurrentViewLoaded()" class="graph-empty-state graph-empty-state--dialog">
          <Network aria-hidden="true" />
          <p>当前图谱还没有可用视图。</p>
        </section>
        <MindmapViewer
          v-else-if="graphStore.viewType === 'MINDMAP'"
          :payload="graphStore.activeViewPayload"
          fullscreen
          @open-evidence="graphStore.openEvidence"
        />
        <GraphViewer
          v-else-if="graphStore.viewType === 'GRAPH'"
          :payload="graphStore.activeViewPayload"
          fullscreen
          @open-evidence="graphStore.openEvidence"
        />
        <GraphAdjacencyList
          v-else
          :payload="graphStore.activeViewPayload"
          fullscreen
          @open-evidence="graphStore.openEvidence"
        />
      </section>
    </el-dialog>

    <GraphEvidenceDrawer
      v-model="graphStore.isEvidenceOpen"
      :target="graphStore.evidenceTarget"
      :evidence="graphStore.evidenceItems"
      :loading="graphStore.isLoadingEvidence"
      :error="graphStore.evidenceError"
    />
  </section>
</template>
