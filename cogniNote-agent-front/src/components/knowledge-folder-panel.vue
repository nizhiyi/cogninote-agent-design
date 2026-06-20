<script setup>
import { computed, ref } from 'vue'
import {
  AlertTriangle,
  ChevronRight,
  FolderPlus,
  ListTree,
  RefreshCw,
  RotateCcw
} from 'lucide-vue-next'
import KnowledgeFolderImportDialog from './knowledge-folder-import-dialog.vue'
import KnowledgeHealthDrawer from './knowledge-health-drawer.vue'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { useKnowledgeHealthStore } from '../stores/knowledge-health'
import { useSearchStore } from '../stores/search'
import { formatTime } from '../utils/formatters'

const knowledgeStore = useKnowledgeFoldersStore()
const knowledgeHealthStore = useKnowledgeHealthStore()
const searchStore = useSearchStore()
const isImportDialogOpen = ref(false)
const isHealthIssuesDialogOpen = ref(false)

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

const healthSummary = computed(() => knowledgeHealthStore.health?.summary || null)
const issueFolders = computed(() =>
  (knowledgeHealthStore.health?.folders || []).filter((folderHealthSummary) => folderHealthIssueCount(folderHealthSummary))
)
const recentFolders = computed(() => knowledgeStore.folders.slice(0, 4))

/**
 * 全量重建会触发高成本索引操作，只在用户明确点击时执行。
 */
async function rebuildAllIndexes() {
  await searchStore.rebuildIndex()
  if (!searchStore.indexError) {
    await Promise.all([
      knowledgeStore.fetchFolders(),
      knowledgeHealthStore.fetchHealth()
    ])
  }
}

function openImportDialog() {
  knowledgeStore.error = ''
  isImportDialogOpen.value = true
}

function openHealthIssuesDialog() {
  isHealthIssuesDialogOpen.value = true
}

async function openFolderIssueDetail(folderId) {
  isHealthIssuesDialogOpen.value = false
  await knowledgeHealthStore.openFolderIssues(folderId)
}

function folderHealth(folder) {
  return knowledgeHealthStore.folderHealthById.get(folder.id) || null
}

function healthStatusLabel(status) {
  return HEALTH_STATUS_LABELS[status] || status || '未知'
}

function healthStatusClass(status) {
  return `status-chip--health-${String(status || 'unknown').toLowerCase()}`
}

function folderIssueCount(folder) {
  return folderHealthIssueCount(folderHealth(folder))
}

function folderHealthIssueCount(health) {
  if (!health || !health.enabled || health.status === 'DISABLED') {
    return 0
  }
  const fileIssueCount = health.failedCount
    + health.unindexedCount
    + health.missingLocalFileCount
    + health.staleLocalFileCount
  return health.status === 'HEALTHY' ? 0 : Math.max(1, fileIssueCount)
}

function runOperationLabel(run) {
  return RUN_OPERATION_LABELS[run?.operation] || run?.operation || ''
}
</script>

<template>
  <section class="knowledge-pane knowledge-pane--folders" aria-label="知识库资料管理">
    <header class="knowledge-pane__header knowledge-pane__header--compact">
      <div>
        <p class="eyebrow">资料管理</p>
        <h3>知识库总览</h3>
        <p class="muted-text">导入本地资料，查看全局健康状态；目录维护在独立列表中处理。</p>
      </div>
      <div class="header-actions">
        <el-button @click="openImportDialog">
          <FolderPlus aria-hidden="true" />
          <span>导入目录</span>
        </el-button>
<!--        <RouterLink-->
<!--          class="knowledge-header-link"-->
<!--          :to="{ name: 'knowledge', query: { panel: 'directories' } }"-->
<!--        >-->
<!--          <ListTree aria-hidden="true" />-->
<!--          <span>目录管理</span>-->
<!--        </RouterLink>-->
        <el-button :loading="searchStore.isRebuildingIndex" @click="rebuildAllIndexes">
          <RotateCcw aria-hidden="true" />
          <span>重建索引</span>
        </el-button>
      </div>
    </header>

    <section v-if="knowledgeHealthStore.health" class="knowledge-health-overview">
      <div class="knowledge-health-overview__main">
        <span :class="['status-chip', healthStatusClass(knowledgeHealthStore.health.status)]">
          {{ healthStatusLabel(knowledgeHealthStore.health.status) }}
        </span>
        <div>
          <strong>知识库可信状态</strong>
          <p class="muted-text">
            {{ healthSummary?.enabledFolderCount || 0 }} 个启用目录 · {{ healthSummary?.documentCount || 0 }} 个文档
          </p>
        </div>
      </div>
      <div class="knowledge-health-overview__stats">
        <span>失败 {{ healthSummary?.failedCount || 0 }}</span>
        <span>未索引 {{ healthSummary?.unindexedCount || 0 }}</span>
        <span>缺失 {{ healthSummary?.missingLocalFileCount || 0 }}</span>
        <span>变化 {{ healthSummary?.staleLocalFileCount || 0 }}</span>
      </div>
      <div class="knowledge-health-overview__actions">
        <el-button
          :disabled="issueFolders.length === 0"
          @click="openHealthIssuesDialog"
        >
          <AlertTriangle aria-hidden="true" />
          <span>查看问题</span>
        </el-button>
        <el-button :loading="knowledgeHealthStore.isLoading" @click="knowledgeHealthStore.fetchHealth">
          <RefreshCw aria-hidden="true" />
          <span>刷新诊断</span>
        </el-button>
      </div>
    </section>

    <section class="knowledge-overview-grid" aria-label="知识库统计">
      <article>
        <span>目录</span>
        <strong>{{ knowledgeStore.stats.folderCount }}</strong>
        <p>已导入本地目录</p>
      </article>
      <article>
        <span>文档</span>
        <strong>{{ knowledgeStore.stats.documentCount }}</strong>
        <p>纳入知识库资料</p>
      </article>
      <article>
        <span>Chunks</span>
        <strong>{{ knowledgeStore.stats.chunks }}</strong>
        <p>可检索内容片段</p>
      </article>
      <article>
        <span>解析失败</span>
        <strong>{{ knowledgeStore.stats.failed }}</strong>
        <p>需要重新导入或检查文件</p>
      </article>
    </section>

    <section class="knowledge-directory-entry" aria-label="目录管理入口">
      <div>
        <p class="eyebrow">目录维护</p>
        <h4>用列表管理目录</h4>
        <p class="muted-text">搜索、分页、同步、启停和删除目录都在这里处理，避免总览页被操作细节挤满。</p>
      </div>
      <RouterLink class="knowledge-directory-entry__link" :to="{ name: 'knowledge', query: { panel: 'directories' } }">
        <span>打开目录管理</span>
        <ChevronRight aria-hidden="true" />
      </RouterLink>
    </section>

    <section class="knowledge-folder-snapshot" aria-label="最近目录">
      <header>
        <h4>最近目录</h4>
        <RouterLink :to="{ name: 'knowledge', query: { panel: 'directories' } }">查看全部</RouterLink>
      </header>
      <p v-if="knowledgeStore.isLoading" class="panel-message">正在读取知识库目录...</p>
      <p v-else-if="!knowledgeStore.folders.length" class="panel-message">还没有导入知识库目录。</p>
      <template v-else>
        <article v-for="folder in recentFolders" :key="folder.id" class="knowledge-folder-snapshot__item">
          <div>
            <strong>{{ folder.displayName }}</strong>
            <p class="path-text">{{ folder.folderPath }}</p>
          </div>
          <div class="folder-meta">
            <span :class="['status-chip', folder.enabled ? 'status-chip--parsed' : 'status-chip--skipped']">
              {{ folder.enabled ? '已启用' : '已停用' }}
            </span>
            <span
              v-if="folderHealth(folder)"
              :class="['status-chip', healthStatusClass(folderHealth(folder).status)]"
            >
              {{ healthStatusLabel(folderHealth(folder).status) }}
            </span>
            <span v-if="folderIssueCount(folder)" class="folder-issue-count">问题 {{ folderIssueCount(folder) }}</span>
            <span>{{ folder.documentCount }} 文档</span>
            <span>{{ folder.chunkCount }} chunks</span>
            <span v-if="folderHealth(folder)?.lastRun">
              最近{{ runOperationLabel(folderHealth(folder).lastRun) }} {{ formatTime(folderHealth(folder).lastRun.completedAt) }}
            </span>
          </div>
        </article>
      </template>
    </section>

    <el-alert
      v-if="knowledgeStore.error && !isImportDialogOpen"
      class="settings-inline-alert"
      type="error"
      :title="knowledgeStore.error"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="knowledgeHealthStore.error"
      class="settings-inline-alert"
      type="error"
      :title="knowledgeHealthStore.error"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="searchStore.indexError"
      class="settings-inline-alert"
      type="error"
      :title="searchStore.indexError"
      :closable="false"
      show-icon
    />

    <div v-if="knowledgeStore.ingestResult" class="result-strip result-strip--compact">
      <span>扫描 {{ knowledgeStore.ingestResult.scannedCount }}</span>
      <span>解析 {{ knowledgeStore.ingestResult.parsedCount }}</span>
      <span>跳过 {{ knowledgeStore.ingestResult.skippedCount }}</span>
      <span>失败 {{ knowledgeStore.ingestResult.failedCount }}</span>
    </div>

    <div v-if="knowledgeStore.rebuildResult" class="result-strip result-strip--compact result-strip--three">
      <span>扫描 {{ knowledgeStore.rebuildResult.scannedCount }}</span>
      <span>索引文档 {{ knowledgeStore.rebuildResult.indexedDocumentCount }}</span>
      <span>失败 {{ knowledgeStore.rebuildResult.failedCount + knowledgeStore.rebuildResult.failedDocumentCount }}</span>
    </div>

    <div v-if="searchStore.rebuildResult" class="result-strip result-strip--compact result-strip--three">
      <span>索引文档 {{ searchStore.rebuildResult.indexedDocumentCount }}</span>
      <span>索引 chunks {{ searchStore.rebuildResult.indexedChunkCount }}</span>
      <span>耗时 {{ searchStore.rebuildResult.durationMs }} ms</span>
    </div>

    <ul v-if="knowledgeStore.ingestResult?.failures?.length || knowledgeStore.rebuildResult?.failures?.length" class="failure-list">
      <li
        v-for="failure in (knowledgeStore.ingestResult?.failures || knowledgeStore.rebuildResult?.failures)"
        :key="failure.sourcePath"
      >
        <strong>{{ failure.sourcePath }}</strong>
        <span>{{ failure.message }}</span>
      </li>
    </ul>

    <el-dialog
      v-model="isHealthIssuesDialogOpen"
      class="knowledge-health-issues-dialog"
      body-class="knowledge-health-issues-dialog__body"
      title="知识库问题目录"
      width="min(720px, calc(100vw - 32px))"
      align-center
    >
      <p v-if="issueFolders.length === 0" class="panel-message">当前没有需要处理的问题目录。</p>
      <section v-else class="knowledge-health-issue-folders">
        <article
          v-for="folder in issueFolders"
          :key="folder.id"
          class="knowledge-health-issue-folder"
        >
          <div>
            <strong>{{ folder.displayName }}</strong>
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
            </div>
          </div>
          <el-button @click="openFolderIssueDetail(folder.id)">
            <AlertTriangle aria-hidden="true" />
            <span>问题详情</span>
          </el-button>
        </article>
      </section>
    </el-dialog>

    <KnowledgeFolderImportDialog v-model="isImportDialogOpen" />
    <KnowledgeHealthDrawer />
  </section>
</template>
