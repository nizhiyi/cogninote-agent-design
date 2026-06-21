<script setup>
import { computed, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  BrainCircuit,
  CheckCircle2,
  ChevronRight,
  Database,
  FolderOpen,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  Wrench
} from 'lucide-vue-next'
import KnowledgeHealthDrawer from './knowledge-health-drawer.vue'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { useKnowledgeHealthStore } from '../stores/knowledge-health'
import { useKnowledgeMaintenanceStore } from '../stores/knowledge-maintenance'
import { useSearchStore } from '../stores/search'
import { formatTime } from '../utils/formatters'

const knowledgeStore = useKnowledgeFoldersStore()
const healthStore = useKnowledgeHealthStore()
const maintenanceStore = useKnowledgeMaintenanceStore()
const searchStore = useSearchStore()
const isRunsDialogOpen = ref(false)
const runPage = ref(1)
const runPageSize = ref(10)

const HEALTH_STATUS_LABELS = {
  HEALTHY: '可信',
  WARNING: '需关注',
  ERROR: '需修复',
  DISABLED: '已停用',
  EMPTY: '空目录'
}

const RUN_OPERATION_LABELS = {
  IMPORT: '导入',
  SYNC: '同步',
  REBUILD_INDEX: '重建',
  ENABLE: '启用',
  DISABLE: '停用',
  DELETE: '删除'
}

const RUN_STATUS_LABELS = {
  QUEUED: '等待中',
  RUNNING: '运行中',
  CANCELLING: '取消中',
  CANCELLED: '已取消',
  COMPLETED: '完成',
  COMPLETED_WITH_WARNINGS: '有失败项',
  FAILED: '失败'
}

const healthSummary = computed(() => healthStore.health?.summary || null)
const healthIssues = computed(() => healthStore.health?.issues || [])
const globalIssues = computed(() => healthIssues.value.filter((issue) => issue.scopeType === 'ALL' && !issue.scopeId))
const folderIssues = computed(() =>
  (healthStore.health?.folders || []).filter((folder) => folderHealthIssueCount(folder))
)
const currentRuns = computed(() => maintenanceStore.currentRuns.length
  ? maintenanceStore.currentRuns
  : healthStore.health?.currentRuns || []
)
const queuedRuns = computed(() => maintenanceStore.queuedRuns.length
  ? maintenanceStore.queuedRuns
  : healthStore.health?.queuedRuns || []
)
const hasIndexIssue = computed(() => globalIssues.value.some((issue) => issue.code === 'INDEX_INCONSISTENT'))
const hasEmbeddingIssue = computed(() => globalIssues.value.some((issue) => issue.code === 'EMBEDDING_UNCONFIGURED'))
const totalIssueEntries = computed(() => globalIssues.value.length + folderIssues.value.length)
const primaryActionLabel = computed(() => {
  if (hasIndexIssue.value) {
    return '重建索引'
  }
  if (totalIssueEntries.value > 0) {
    return '查看问题'
  }
  return '刷新诊断'
})
const statusIcon = computed(() => (healthStore.health?.status === 'HEALTHY' ? ShieldCheck : ShieldAlert))
const summaryText = computed(() => {
  const summary = healthSummary.value
  if (!summary) {
    return '正在读取知识库可信状态'
  }
  return `${summary.enabledFolderCount || 0} 个启用目录 · ${summary.documentCount || 0} 个文档 · ${summary.chunkCount || 0} chunks`
})
const issueMetrics = computed(() => [
  {
    key: 'failed',
    label: '解析失败',
    value: healthSummary.value?.failedCount || 0,
    tone: (healthSummary.value?.failedCount || 0) > 0 ? 'warning' : 'muted'
  },
  {
    key: 'unindexed',
    label: '未索引',
    value: healthSummary.value?.unindexedCount || 0,
    tone: (healthSummary.value?.unindexedCount || 0) > 0 ? 'error' : 'muted'
  },
  {
    key: 'missing',
    label: '本地缺失',
    value: healthSummary.value?.missingLocalFileCount || 0,
    tone: (healthSummary.value?.missingLocalFileCount || 0) > 0 ? 'warning' : 'muted'
  },
  {
    key: 'stale',
    label: '疑似变化',
    value: healthSummary.value?.staleLocalFileCount || 0,
    tone: (healthSummary.value?.staleLocalFileCount || 0) > 0 ? 'warning' : 'muted'
  },
  {
    key: 'new-local',
    label: '本地新增',
    value: healthSummary.value?.newLocalFileCount || 0,
    tone: (healthSummary.value?.newLocalFileCount || 0) > 0 ? 'warning' : 'muted'
  }
])
const systemSignals = computed(() => [
  {
    key: 'lucene',
    icon: Database,
    label: 'Lucene 一致性',
    value: healthSummary.value?.indexConsistent ? '一致' : '不一致',
    state: healthSummary.value?.indexConsistent ? 'ok' : 'error',
    detail: `${healthSummary.value?.luceneDocumentCount || 0} 文档 / ${healthSummary.value?.luceneChunkCount || 0} chunks`
  },
  {
    key: 'embedding',
    icon: BrainCircuit,
    label: 'Embedding 状态',
    value: healthSummary.value?.embeddingConfigured ? '可用' : '未配置',
    state: healthSummary.value?.embeddingConfigured ? 'ok' : 'warning',
    detail: healthSummary.value?.embeddingConfigured ? '向量和混合检索可用' : '向量/混合检索会降级提示'
  },
  {
    key: 'running',
    icon: Activity,
    label: '当前维护任务',
    value: currentRuns.value.length ? `${currentRuns.value.length} 个运行中` : '空闲',
    state: currentRuns.value.length ? 'warning' : 'ok',
    detail: currentRuns.value.length
      ? currentRuns.value.map((run) => `${runScopeLabel(run)} ${runOperationLabel(run)}`).join('、')
      : '没有正在执行的维护任务'
  }
])
const latestRun = computed(() => maintenanceStore.latestRun || healthStore.health?.latestRun || null)

function healthStatusLabel(status) {
  return HEALTH_STATUS_LABELS[status] || status || '未知'
}

function healthStatusClass(status) {
  return `status-chip--health-${String(status || 'unknown').toLowerCase()}`
}

function folderHealthIssueCount(health) {
  if (!health || !health.enabled || health.status === 'DISABLED') {
    return 0
  }
  const fileIssueCount = health.failedCount
    + health.unindexedCount
    + health.missingLocalFileCount
    + health.staleLocalFileCount
    + (health.newLocalFileCount || 0)
  return health.status === 'HEALTHY' ? 0 : Math.max(1, fileIssueCount)
}

function runOperationLabel(run) {
  return RUN_OPERATION_LABELS[run?.operation] || run?.operation || ''
}

function runStatusLabel(run) {
  return RUN_STATUS_LABELS[run?.status] || run?.status || '无记录'
}

function runStatusClass(run) {
  return `knowledge-run-status--${String(run?.status || 'unknown').toLowerCase()}`
}

async function refreshHealthPanel() {
  await healthStore.fetchHealth()
}

async function rebuildAllIndexes() {
  await searchStore.rebuildIndex()
}

async function handlePrimaryAction() {
  if (hasIndexIssue.value) {
    await rebuildAllIndexes()
    return
  }
  if (totalIssueEntries.value > 0) {
    openGlobalIssues()
    return
  }
  await refreshHealthPanel()
}

function openGlobalIssues() {
  healthStore.selectedFolderId = ''
  healthStore.folderHealth = null
  healthStore.isDrawerOpen = true
}

async function openRunsDialog() {
  isRunsDialogOpen.value = true
  runPage.value = 1
  await fetchRunsPage()
}

async function fetchRunsPage() {
  await healthStore.fetchRunsPage({
    page: runPage.value,
    pageSize: runPageSize.value
  })
}

async function handleRunPageChange(page) {
  runPage.value = page
  await fetchRunsPage()
}

async function handleRunPageSizeChange(pageSize) {
  runPageSize.value = pageSize
  runPage.value = 1
  await fetchRunsPage()
}

function runScopeLabel(run) {
  if (!run?.scopeType) {
    return '未知范围'
  }
  if (run.scopeType === 'ALL') {
    return '全库'
  }
  return run.scopeId ? `目录 · ${run.scopeId}` : '目录'
}

function runProgressPercentage(run) {
  if (!run?.progressTotal) {
    return 0
  }
  return Math.min(100, Math.round((run.progressCurrent / run.progressTotal) * 100))
}

function runCountSummary(run) {
  const pieces = []
  if (run?.scannedCount != null) {
    pieces.push(`扫描 ${run.scannedCount}`)
  }
  if (run?.parsedCount != null) {
    pieces.push(`解析 ${run.parsedCount}`)
  }
  if (run?.indexedDocumentCount != null) {
    pieces.push(`索引 ${run.indexedDocumentCount}`)
  }
  const failedCount = (run?.failedCount || 0) + (run?.failedDocumentCount || 0)
  if (failedCount) {
    pieces.push(`失败 ${failedCount}`)
  }
  return pieces.length ? pieces.join(' · ') : '无计数'
}
</script>

<template>
  <section class="knowledge-pane knowledge-pane--health" aria-label="知识库可信状态">
    <header class="knowledge-health-page-header">
      <div>
        <p class="eyebrow">可信状态</p>
        <h3>诊断与维护控制台</h3>
        <p class="muted-text">集中查看索引一致性、Embedding 可用性、目录问题和最近维护记录。</p>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :disabled="healthStore.isLoading || searchStore.isRebuildingIndex || maintenanceStore.isEnqueueing"
          :loading="searchStore.isRebuildingIndex"
          @click="handlePrimaryAction"
        >
          <RotateCcw v-if="hasIndexIssue" aria-hidden="true" />
          <AlertTriangle v-else-if="totalIssueEntries > 0" aria-hidden="true" />
          <RefreshCw v-else aria-hidden="true" />
          <span>{{ primaryActionLabel }}</span>
        </el-button>
        <el-button :loading="healthStore.isLoading || healthStore.isLoadingRuns" @click="refreshHealthPanel">
          <RefreshCw aria-hidden="true" />
          <span>刷新</span>
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="healthStore.error"
      class="settings-inline-alert"
      type="error"
      :title="healthStore.error"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="maintenanceStore.error"
      class="settings-inline-alert"
      type="error"
      :title="maintenanceStore.error"
      :closable="false"
      show-icon
    />

    <section class="knowledge-health-command-center" aria-label="可信状态摘要">
      <div class="knowledge-health-command-card">
        <span :class="['knowledge-health-command-card__icon', healthStatusClass(healthStore.health?.status)]">
          <component :is="statusIcon" aria-hidden="true" />
        </span>
        <div class="knowledge-health-command-card__content">
          <span :class="['status-chip', healthStatusClass(healthStore.health?.status)]">
            {{ healthStatusLabel(healthStore.health?.status) }}
          </span>
          <strong>全库可信状态</strong>
          <p>{{ summaryText }}</p>
          <div class="knowledge-health-command-card__signals">
            <span>Lucene {{ healthSummary?.indexConsistent ? '一致' : '不一致' }}</span>
            <span>Embedding {{ healthSummary?.embeddingConfigured ? '可用' : '未配置' }}</span>
            <span>{{ currentRuns.length ? `${currentRuns.length} 个任务运行中` : '维护空闲' }}</span>
          </div>
        </div>
      </div>

      <dl class="knowledge-health-command-metrics" aria-label="问题计数">
        <div
          v-for="metric in issueMetrics"
          :key="metric.key"
          :class="['knowledge-health-command-metric', `knowledge-health-command-metric--${metric.tone}`]"
        >
          <dt>{{ metric.label }}</dt>
          <dd>{{ metric.value }}</dd>
        </div>
      </dl>
    </section>

    <section class="knowledge-health-signal-grid" aria-label="系统诊断信号">
      <article
        v-for="signal in systemSignals"
        :key="signal.key"
        :class="['knowledge-health-signal-card', `knowledge-health-signal-card--${signal.state}`]"
      >
        <component :is="signal.icon" aria-hidden="true" />
        <div>
          <span>{{ signal.label }}</span>
          <strong>{{ signal.value }}</strong>
          <p>{{ signal.detail }}</p>
        </div>
      </article>
    </section>

    <section class="knowledge-health-main-grid">
      <section class="knowledge-health-section" aria-label="全局诊断问题">
        <header>
          <div>
            <p class="eyebrow">全局诊断</p>
            <h4>索引与模型</h4>
          </div>
          <span>{{ globalIssues.length }} 项</span>
        </header>

        <p v-if="!globalIssues.length" class="knowledge-health-empty">
          <CheckCircle2 aria-hidden="true" />
          <span>没有全局诊断问题。</span>
        </p>

        <article
          v-for="issue in globalIssues"
          :key="issue.code"
          class="knowledge-health-issue-row"
        >
          <div>
            <strong>{{ issue.message }}</strong>
            <p>{{ issue.severity }} · {{ issue.action }}</p>
          </div>
          <el-button
            v-if="issue.code === 'INDEX_INCONSISTENT'"
            :loading="searchStore.isRebuildingIndex"
            :disabled="maintenanceStore.hasActiveRun"
            @click="rebuildAllIndexes"
          >
            <Wrench aria-hidden="true" />
            <span>重建索引</span>
          </el-button>
          <RouterLink
            v-else-if="issue.code === 'EMBEDDING_UNCONFIGURED'"
            class="knowledge-directory-entry__link"
            :to="{ name: 'settings', query: { item: 'model-embedding' } }"
          >
            <span>配置向量模型</span>
            <ChevronRight aria-hidden="true" />
          </RouterLink>
        </article>
      </section>

      <section class="knowledge-health-section" aria-label="目录诊断问题">
        <header>
          <div>
            <p class="eyebrow">目录诊断</p>
            <h4>需要处理的目录</h4>
          </div>
          <span>{{ folderIssues.length }} 个目录</span>
        </header>

        <p v-if="!folderIssues.length" class="knowledge-health-empty">
          <CheckCircle2 aria-hidden="true" />
          <span>所有启用目录当前可信。</span>
        </p>

        <article
          v-for="folder in folderIssues"
          :key="folder.id"
          class="knowledge-health-folder-row"
        >
          <div>
            <strong>
              <FolderOpen aria-hidden="true" />
              <span>{{ folder.displayName }}</span>
            </strong>
            <p class="path-text">{{ folder.folderPath }}</p>
            <div class="folder-meta">
              <span :class="['status-chip', healthStatusClass(folder.status)]">
                {{ healthStatusLabel(folder.status) }}
              </span>
              <span>{{ folderHealthIssueCount(folder) }} 个问题</span>
              <span v-if="folder.failedCount">失败 {{ folder.failedCount }}</span>
              <span v-if="folder.unindexedCount">未索引 {{ folder.unindexedCount }}</span>
              <span v-if="folder.missingLocalFileCount">缺失 {{ folder.missingLocalFileCount }}</span>
              <span v-if="folder.staleLocalFileCount">变化 {{ folder.staleLocalFileCount }}</span>
              <span v-if="folder.newLocalFileCount">新增 {{ folder.newLocalFileCount }}</span>
            </div>
          </div>
          <el-button @click="healthStore.openFolderIssues(folder.id)">
            <AlertTriangle aria-hidden="true" />
            <span>诊断详情</span>
          </el-button>
        </article>
      </section>
    </section>

    <section class="knowledge-health-section knowledge-health-section--queue" aria-label="维护队列">
      <header>
        <div>
          <p class="eyebrow">维护队列</p>
          <h4>当前任务与等待队列</h4>
        </div>
        <el-button :loading="maintenanceStore.isLoadingQueue" @click="maintenanceStore.fetchQueue">
          <RefreshCw aria-hidden="true" />
          <span>刷新队列</span>
        </el-button>
      </header>

      <article v-if="currentRuns.length" class="knowledge-maintenance-task knowledge-maintenance-task--running">
        <div>
          <strong>{{ runScopeLabel(currentRuns[0]) }} · {{ runOperationLabel(currentRuns[0]) }}</strong>
          <span :class="['knowledge-run-status', runStatusClass(currentRuns[0])]">
            <Activity aria-hidden="true" />
            <span>{{ runStatusLabel(currentRuns[0]) }}</span>
          </span>
        </div>
        <p>{{ currentRuns[0].phase || 'RUNNING' }} · {{ currentRuns[0].currentItem || '处理中' }}</p>
        <el-progress
          :percentage="runProgressPercentage(currentRuns[0])"
          :indeterminate="!currentRuns[0].progressTotal"
          :show-text="Boolean(currentRuns[0].progressTotal)"
        />
        <el-button
          :disabled="currentRuns[0].status === 'CANCELLING'"
          :loading="maintenanceStore.isRunCancelling(currentRuns[0].id)"
          @click="maintenanceStore.cancelRun(currentRuns[0].id)"
        >
          取消任务
        </el-button>
      </article>

      <p v-else class="knowledge-health-empty">
        <CheckCircle2 aria-hidden="true" />
        <span>当前没有运行中的维护任务。</span>
      </p>

      <section v-if="queuedRuns.length" class="knowledge-maintenance-queue-list" aria-label="等待队列">
        <article v-for="run in queuedRuns" :key="run.id" class="knowledge-maintenance-task">
          <div>
            <strong>#{{ run.queuePosition || '-' }} {{ runScopeLabel(run) }} · {{ runOperationLabel(run) }}</strong>
            <span :class="['knowledge-run-status', runStatusClass(run)]">
              <Activity aria-hidden="true" />
              <span>{{ runStatusLabel(run) }}</span>
            </span>
          </div>
          <p>{{ run.currentItem || '等待执行' }} · 排队 {{ formatTime(run.queuedAt || run.createdAt) }}</p>
          <el-button
            :loading="maintenanceStore.isRunCancelling(run.id)"
            @click="maintenanceStore.cancelRun(run.id)"
          >
            取消排队
          </el-button>
        </article>
      </section>
    </section>

    <section class="knowledge-health-section knowledge-health-section--runs-entry" aria-label="维护记录入口">
      <header>
        <div>
          <p class="eyebrow">维护记录</p>
          <h4>运行历史</h4>
        </div>
        <el-button @click="openRunsDialog">
          <Activity aria-hidden="true" />
          <span>查看全部</span>
        </el-button>
      </header>

      <article v-if="latestRun" class="knowledge-health-run-preview">
        <div>
          <strong>{{ runOperationLabel(latestRun) }}</strong>
          <span :class="['knowledge-run-status', runStatusClass(latestRun)]">
            <Activity aria-hidden="true" />
            <span>{{ runStatusLabel(latestRun) }}</span>
          </span>
        </div>
        <p>{{ runScopeLabel(latestRun) }} · {{ runCountSummary(latestRun) }}</p>
        <em>{{ formatTime(latestRun.completedAt || latestRun.startedAt) }} · {{ latestRun.durationMs != null ? `${latestRun.durationMs} ms` : '运行中' }}</em>
      </article>
      <p v-else class="knowledge-health-empty">
        <Activity aria-hidden="true" />
        <span>暂无最近维护记录；点击查看全部可按页读取历史数据。</span>
      </p>
    </section>

    <el-dialog
      v-model="isRunsDialogOpen"
      class="knowledge-health-runs-dialog"
      title="维护记录"
      width="min(920px, calc(100vw - 32px))"
      align-center
    >
      <p v-if="healthStore.isLoadingRuns" class="panel-message">正在读取维护记录...</p>
      <p v-else-if="!healthStore.runsPage.items.length" class="knowledge-health-empty">
        <Activity aria-hidden="true" />
        <span>暂无维护记录。</span>
      </p>

      <section v-else class="knowledge-health-run-dialog-list" aria-label="维护记录列表">
        <header aria-hidden="true">
          <span>操作</span>
          <span>状态</span>
          <span>范围</span>
          <span>计数</span>
          <span>时间</span>
          <span>耗时</span>
        </header>
        <article v-for="run in healthStore.runsPage.items" :key="run.id">
          <strong>{{ runOperationLabel(run) }}</strong>
          <span :class="['knowledge-run-status', runStatusClass(run)]">
            <Activity aria-hidden="true" />
            <span>{{ runStatusLabel(run) }}</span>
          </span>
          <span>{{ runScopeLabel(run) }}</span>
          <span>{{ runCountSummary(run) }}</span>
          <span>{{ formatTime(run.completedAt || run.startedAt) }}</span>
          <span>{{ run.durationMs != null ? `${run.durationMs} ms` : '运行中' }}</span>
        </article>
      </section>

      <template #footer>
        <el-pagination
          v-model:current-page="runPage"
          v-model:page-size="runPageSize"
          :page-sizes="[10, 20, 50]"
          :total="healthStore.runsPage.total"
          background
          layout="total, sizes, prev, pager, next"
          @current-change="handleRunPageChange"
          @size-change="handleRunPageSizeChange"
        />
      </template>
    </el-dialog>

    <KnowledgeHealthDrawer />
  </section>
</template>
