<script setup>
// knowledge-folder-panel 负责知识库目录导入、状态管理和目录级操作。
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  FolderInput,
  FolderOpen,
  FolderPlus,
  FolderSync,
  RefreshCw,
  RotateCcw,
  Trash2
} from 'lucide-vue-next'
import { isTauriRuntime } from '../api/desktop-api'
import KnowledgeHealthDrawer from './knowledge-health-drawer.vue'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { useKnowledgeHealthStore } from '../stores/knowledge-health'
import { useSearchStore } from '../stores/search'
import { formatFileSize, formatTime } from '../utils/formatters'

const knowledgeStore = useKnowledgeFoldersStore()
const knowledgeHealthStore = useKnowledgeHealthStore()
const searchStore = useSearchStore()
const isImportDialogOpen = ref(false)

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

const canPickKnowledgeFolder = computed(() => isTauriRuntime())
const healthSummary = computed(() => knowledgeHealthStore.health?.summary || null)

/**
 * 全量重建是高成本操作，保留在资料管理页显式触发，避免刷新时产生副作用。
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

async function submitImportFolder() {
  await knowledgeStore.importFolder()
  if (!knowledgeStore.error) {
    isImportDialogOpen.value = false
  }
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
  const health = folderHealth(folder)
  if (!health) {
    return 0
  }
  const structuralIssue = ['DISABLED', 'EMPTY'].includes(health.status) ? 1 : 0
  return structuralIssue
    + health.failedCount
    + health.unindexedCount
    + health.missingLocalFileCount
    + health.staleLocalFileCount
}

function runOperationLabel(run) {
  return RUN_OPERATION_LABELS[run?.operation] || run?.operation || ''
}

async function confirmDeleteFolder(folder) {
  if (!folder || knowledgeStore.isFolderBusy(folder.id)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除“${folder.displayName}”这个知识库目录记录吗？本地原始文件不会被删除。`,
      '删除知识库目录',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
    await knowledgeStore.deleteFolder(folder.id)
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      throw err
    }
  }
}
</script>

<template>
  <section class="knowledge-pane knowledge-pane--folders" aria-label="知识库目录管理">
    <header class="knowledge-pane__header knowledge-pane__header--compact">
      <div>
        <p class="eyebrow">资料管理</p>
        <h3>目录</h3>
        <p class="muted-text">导入本地文件夹，查看目录状态。</p>
      </div>
      <div class="header-actions">
        <el-button @click="openImportDialog">
          <FolderPlus aria-hidden="true" />
          <span>导入目录</span>
        </el-button>
        <el-button :loading="searchStore.isRebuildingIndex" @click="rebuildAllIndexes">
          <RefreshCw aria-hidden="true" />
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
      <el-button :loading="knowledgeHealthStore.isLoading" @click="knowledgeHealthStore.fetchHealth">
        <RefreshCw aria-hidden="true" />
        <span>刷新诊断</span>
      </el-button>
    </section>

    <el-dialog
      v-model="isImportDialogOpen"
      class="knowledge-import-dialog"
      title="导入本地目录"
      width="min(560px, calc(100vw - 32px))"
      align-center
    >
      <form class="knowledge-folder-import" @submit.prevent="submitImportFolder">
        <label class="field field--full">
          <span>本地目录</span>
          <el-input
            v-model="knowledgeStore.folderPath"
            placeholder="点击选择文件夹，或手动输入 D:/notes"
            autocomplete="off"
          />
        </label>

        <div class="knowledge-folder-import__controls">
          <el-button
            :disabled="!canPickKnowledgeFolder"
            title="系统文件夹选择器仅在桌面版可用，浏览器开发模式请手动输入路径"
            @click="knowledgeStore.chooseFolder"
          >
            <FolderInput aria-hidden="true" />
            <span>选择文件夹</span>
          </el-button>

          <el-checkbox v-model="knowledgeStore.recursive">递归扫描</el-checkbox>
        </div>

        <el-alert
          v-if="knowledgeStore.error"
          class="settings-inline-alert"
          type="error"
          :title="knowledgeStore.error"
          :closable="false"
          show-icon
        />
      </form>

      <template #footer>
        <div class="knowledge-import-dialog__footer">
          <el-button @click="isImportDialogOpen = false">取消</el-button>
          <el-button type="primary" :loading="knowledgeStore.isImporting" @click="submitImportFolder">
            导入目录
          </el-button>
        </div>
      </template>
    </el-dialog>

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

    <section class="knowledge-folder-list">
      <p v-if="knowledgeStore.isLoading" class="panel-message">正在读取知识库目录...</p>
      <p v-else-if="knowledgeStore.folders.length === 0 && knowledgeStore.unassignedDocuments.length === 0" class="panel-message">
        还没有导入知识库目录。
      </p>

      <article v-for="folder in knowledgeStore.folders" :key="folder.id" class="knowledge-folder-card">
        <header class="knowledge-folder-header">
          <button class="folder-toggle-button" type="button" @click="knowledgeStore.toggleExpanded(folder.id)">
            <ChevronDown v-if="knowledgeStore.isExpanded(folder.id)" aria-hidden="true" />
            <ChevronRight v-else aria-hidden="true" />
            <FolderOpen aria-hidden="true" />
            <span>{{ folder.displayName }}</span>
          </button>

          <div class="folder-actions">
            <el-button
              v-if="folderHealth(folder) && folderHealth(folder).status !== 'HEALTHY'"
              :disabled="!folderHealth(folder)"
              @click="knowledgeHealthStore.openFolderIssues(folder.id)"
            >
              <AlertTriangle aria-hidden="true" />
              <span>查看问题</span>
            </el-button>
            <el-button
              :disabled="knowledgeStore.isFolderBusy(folder.id)"
              @click="knowledgeStore.toggleFolderEnabled(folder)"
            >
              {{ folder.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              :disabled="knowledgeStore.isFolderBusy(folder.id) || !folder.enabled"
              title="扫描新增、修改和删除的文件，只处理差异"
              aria-label="同步目录文件"
              @click="knowledgeStore.syncFolder(folder.id)"
            >
              <FolderSync aria-hidden="true" />
              <span>同步文件</span>
            </el-button>
            <el-button
              :disabled="knowledgeStore.isFolderBusy(folder.id) || !folder.enabled"
              title="重新扫描目录并重建该目录索引"
              aria-label="重建目录索引"
              @click="knowledgeStore.rebuildFolder(folder.id)"
            >
              <RotateCcw aria-hidden="true" />
              <span>重建索引</span>
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="knowledgeStore.isFolderBusy(folder.id)"
              @click="confirmDeleteFolder(folder)"
            >
              <Trash2 aria-hidden="true" />
              <span>删除</span>
            </el-button>
          </div>
        </header>

        <p class="path-text">{{ folder.folderPath }}</p>

        <div class="folder-meta">
          <span
            v-if="folderHealth(folder)"
            :class="['status-chip', healthStatusClass(folderHealth(folder).status)]"
          >
            {{ healthStatusLabel(folderHealth(folder).status) }}
          </span>
          <span :class="['status-chip', folder.enabled ? 'status-chip--parsed' : 'status-chip--skipped']">
            {{ folder.enabled ? '已启用' : '已停用' }}
          </span>
          <span>{{ folder.documentCount }} 文档</span>
          <span>{{ folder.chunkCount }} chunks</span>
          <span>{{ folder.unindexedCount }} 未索引</span>
          <span v-if="folderHealth(folder)?.missingLocalFileCount">缺失 {{ folderHealth(folder).missingLocalFileCount }}</span>
          <span v-if="folderHealth(folder)?.staleLocalFileCount">变化 {{ folderHealth(folder).staleLocalFileCount }}</span>
          <span v-if="folderIssueCount(folder)">问题 {{ folderIssueCount(folder) }}</span>
          <span v-if="folderHealth(folder)?.lastRun">
            最近{{ runOperationLabel(folderHealth(folder).lastRun) }} {{ formatTime(folderHealth(folder).lastRun.completedAt) }}
          </span>
          <span>导入 {{ formatTime(folder.lastIngestedAt) }}</span>
          <span>索引 {{ formatTime(folder.lastIndexedAt) }}</span>
        </div>

        <div v-if="knowledgeStore.isExpanded(folder.id)" class="folder-documents">
          <p v-if="folder.documents.length === 0" class="panel-message">该目录下还没有文档记录。</p>
          <article v-for="document in folder.documents" :key="document.id" class="document-row document-row--nested">
            <div class="document-main">
              <div class="document-title-line">
                <h4>{{ document.fileName }}</h4>
                <span :class="['status-chip', `status-chip--${document.status.toLowerCase()}`]">
                  {{ document.status }}
                </span>
              </div>
              <p class="path-text">{{ document.sourcePath }}</p>
              <div class="document-meta">
                <span>{{ document.fileType }}</span>
                <span>{{ formatFileSize(document.fileSize) }}</span>
                <span>{{ document.chunkCount }} chunks</span>
                <span>索引 {{ formatTime(document.indexedAt) }}</span>
                <span>{{ formatTime(document.updatedAt) }}</span>
              </div>
            </div>
          </article>
        </div>
      </article>

      <article v-if="knowledgeStore.unassignedDocuments.length" class="knowledge-folder-card">
        <header class="knowledge-folder-header">
          <button class="folder-toggle-button" type="button" @click="knowledgeStore.toggleExpanded('unassigned')">
            <ChevronDown v-if="knowledgeStore.isExpanded('unassigned')" aria-hidden="true" />
            <ChevronRight v-else aria-hidden="true" />
            <FolderOpen aria-hidden="true" />
            <span>未归属文档</span>
          </button>
        </header>
        <p class="hint-message">这些是旧版本导入的散落文档。重新导入对应本地目录后，会自动归属到知识库文件夹。</p>
        <div v-if="knowledgeStore.isExpanded('unassigned')" class="folder-documents">
          <article v-for="document in knowledgeStore.unassignedDocuments" :key="document.id" class="document-row document-row--nested">
            <div class="document-main">
              <div class="document-title-line">
                <h4>{{ document.fileName }}</h4>
                <span :class="['status-chip', `status-chip--${document.status.toLowerCase()}`]">
                  {{ document.status }}
                </span>
              </div>
              <p class="path-text">{{ document.sourcePath }}</p>
              <div class="document-meta">
                <span>{{ document.fileType }}</span>
                <span>{{ formatFileSize(document.fileSize) }}</span>
                <span>{{ document.chunkCount }} chunks</span>
                <span>索引 {{ formatTime(document.indexedAt) }}</span>
              </div>
            </div>
          </article>
        </div>
      </article>
    </section>

    <KnowledgeHealthDrawer />
  </section>
</template>
