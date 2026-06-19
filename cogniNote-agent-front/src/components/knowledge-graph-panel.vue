<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Database, FileText, Folder, Maximize2, Network, Play, RefreshCw, Square } from 'lucide-vue-next'
import { ElMessageBox } from 'element-plus'
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
const isExplorerDialogOpen = ref(false)
const rebuildReminderMessage = '当前范围可能包含较多资料，生成知识图谱需要调用模型抽取并合并关系，耗时会随数据量、模型能力和网络状态变化。生成期间可以随时取消。'

/**
 * 知识图谱管理面板。
 *
 * <p>面板负责让 scope 选择始终指向一个可生成的目录或文档；实际 run 状态、SSE 订阅和视图缓存都归
 * knowledgeGraph store 管理。</p>
 */
const scopeTypeOptions = [
  { value: 'ALL', label: '全库' },
  { value: 'KNOWLEDGE_FOLDER', label: '目录' },
  { value: 'DOCUMENT', label: '文档' }
]
const folderOptions = computed(() => knowledgeStore.folders.filter((folder) => folder.enabled))
const documentOptions = computed(() => [
  ...knowledgeStore.folders.flatMap((folder) => folder.documents || []),
  ...knowledgeStore.unassignedDocuments
].filter((document) => document.status === 'PARSED'))
const scopedOptions = computed(() =>
  graphStore.scopeType === 'KNOWLEDGE_FOLDER' ? folderOptions.value : documentOptions.value
)
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
// 用于区分“选中了历史图谱但视图不可用”和“还没有从清单选择任何图谱”两种空态。
const hasSelectedGeneratedGraph = computed(() =>
  graphStore.generatedGraphs.some((graph) => graphKey(graph) === graphStore.selectedGraphKey)
)

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

async function ensureScopeSelection() {
  // 默认选中当前可用的第一项，避免用户切到目录/文档范围后生成按钮处于无效空 scope。
  if (graphStore.scopeType === 'KNOWLEDGE_FOLDER' && !graphStore.scopeId && folderOptions.value.length) {
    graphStore.setScopeForGeneration('KNOWLEDGE_FOLDER', folderOptions.value[0].id)
  }
  if (graphStore.scopeType === 'DOCUMENT' && !graphStore.scopeId && documentOptions.value.length) {
    graphStore.setScopeForGeneration('DOCUMENT', documentOptions.value[0].id)
  }
}

async function handleScopeTypeChange(value) {
  if (value === 'KNOWLEDGE_FOLDER') {
    await graphStore.selectScope(value, folderOptions.value[0]?.id || '')
    return
  }
  if (value === 'DOCUMENT') {
    await graphStore.selectScope(value, documentOptions.value[0]?.id || '')
    return
  }
  await graphStore.selectScope('ALL', '')
}

async function handleScopeIdChange(value) {
  await graphStore.selectScope(graphStore.scopeType, value)
}

async function refreshGraphSummaries() {
  await graphStore.loadGeneratedGraphs()
}

async function handleRegenerateGraph(graph) {
  if (!graph) {
    return
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
    void ensureSystemNotificationPermission()
    await graphStore.rebuild()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      throw err
    }
  }
}

function graphKey(graph) {
  const type = graph?.scopeType || 'ALL'
  return `${type}:${type === 'ALL' ? '' : graph?.scopeId || ''}`
}

function graphTypeLabel(scopeType) {
  const labels = {
    ALL: '全库',
    KNOWLEDGE_FOLDER: '目录',
    DOCUMENT: '文档'
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
      <div class="header-actions">
        <el-button
          :loading="graphStore.isLoadingStatus"
          aria-label="刷新当前图谱"
          title="刷新当前图谱"
          @click="graphStore.loadStatus"
        >
          <RefreshCw aria-hidden="true" />
        </el-button>
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

    <section class="generated-graphs" aria-label="已生成图谱">
      <header class="generated-graphs__header">
        <div>
          <p class="eyebrow">已生成图谱</p>
          <h4>目录与文档</h4>
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

      <p v-if="graphStore.isLoadingGeneratedGraphs" class="panel-message">正在读取已生成图谱...</p>
      <section v-else-if="graphStore.generatedGraphs.length" class="generated-graphs__list">
        <article
          v-for="graph in graphStore.generatedGraphs"
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
            <el-button type="primary" @click="graphStore.openGeneratedGraph(graph)">查看</el-button>
            <el-button @click="handleRegenerateGraph(graph)">重新生成</el-button>
          </div>
        </article>
      </section>
      <section v-else class="generated-graphs__empty">
        <Network aria-hidden="true" />
        <span>还没有已生成图谱</span>
      </section>
    </section>

    <section class="graph-toolbar" aria-label="图谱工具栏">
      <label class="field graph-toolbar__field">
        <span>范围</span>
        <el-select v-model="graphStore.scopeType" @change="handleScopeTypeChange">
          <el-option
            v-for="option in scopeTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </label>

      <label v-if="graphStore.scopeType !== 'ALL'" class="field graph-toolbar__field graph-toolbar__field--wide">
        <span>{{ graphStore.scopeType === 'KNOWLEDGE_FOLDER' ? '目录' : '文档' }}</span>
        <el-select v-model="graphStore.scopeId" filterable @change="handleScopeIdChange">
          <el-option
            v-for="option in scopedOptions"
            :key="option.id"
            :label="option.displayName || option.fileName"
            :value="option.id"
          />
        </el-select>
      </label>

      <SegmentedControl v-model="graphStore.viewType" :options="GRAPH_VIEW_OPTIONS" label="图谱视图" />

      <el-button
        class="graph-toolbar__fullscreen"
        :disabled="!graphStore.hasCurrentViewLoaded() || graphStore.isRunActive"
        aria-label="全屏探索图谱"
        title="全屏探索图谱"
        @click="isExplorerDialogOpen = true"
      >
        <Maximize2 aria-hidden="true" />
      </el-button>
    </section>

    <el-alert
      v-if="graphStore.error"
      class="settings-inline-alert"
      type="error"
      :title="graphStore.error"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="graphStore.viewError"
      class="settings-inline-alert"
      type="error"
      :title="graphStore.viewError"
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

    <section
      v-if="!graphStore.isRunActive && !graphStore.isLoadingView && !graphStore.hasCurrentViewLoaded()"
      class="graph-empty-state"
    >
      <Network aria-hidden="true" />
      <p>{{ hasSelectedGeneratedGraph ? '当前范围还没有可用视图。' : '请选择一个已生成图谱查看，或选择范围后生成新的图谱。' }}</p>
      <el-button
        type="primary"
        :disabled="!canGenerate"
        :loading="graphStore.isRebuilding"
        @click="handleRebuildWithReminder"
      >
        生成知识图谱
      </el-button>
    </section>

    <section v-else-if="!graphStore.isRunActive" class="graph-view-shell">
      <p v-if="graphStore.isLoadingView" class="panel-message">正在读取图谱视图...</p>
      <MindmapViewer
        v-else-if="graphStore.viewType === 'MINDMAP'"
        :payload="graphStore.activeViewPayload"
        @open-evidence="graphStore.openEvidence"
      />
      <GraphViewer
        v-else-if="graphStore.viewType === 'GRAPH'"
        :payload="graphStore.activeViewPayload"
        @open-evidence="graphStore.openEvidence"
      />
      <GraphAdjacencyList
        v-else
        :payload="graphStore.activeViewPayload"
        @open-evidence="graphStore.openEvidence"
      />
    </section>

    <el-dialog
      v-model="isExplorerDialogOpen"
      class="graph-fullscreen-dialog"
      fullscreen
      destroy-on-close
      :title="`知识图谱 · ${currentViewLabel}`"
    >
      <section class="graph-fullscreen-dialog__body" aria-label="全屏图谱探索">
        <MindmapViewer
          v-if="graphStore.viewType === 'MINDMAP'"
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
