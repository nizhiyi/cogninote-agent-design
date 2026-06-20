<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Copy, FolderSync, RotateCcw } from 'lucide-vue-next'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { useKnowledgeHealthStore } from '../stores/knowledge-health'
import { formatTime } from '../utils/formatters'

const knowledgeStore = useKnowledgeFoldersStore()
const healthStore = useKnowledgeHealthStore()

const currentFolder = computed(() =>
  (healthStore.health?.folders || []).find((folder) => folder.id === healthStore.selectedFolderId) || null
)
const drawerTitle = computed(() => currentFolder.value?.displayName || '知识库问题')
const folderHealth = computed(() => healthStore.folderHealth)
// 后端按问题来源拆分文档列表，前端只负责分区展示，不在这里重新推导健康状态。
const problemSections = computed(() => [
  {
    key: 'failed',
    title: '解析失败',
    items: folderHealth.value?.failedDocuments || []
  },
  {
    key: 'unindexed',
    title: '未进入索引',
    items: folderHealth.value?.unindexedDocuments || []
  },
  {
    key: 'missing',
    title: '本地文件缺失',
    items: folderHealth.value?.missingLocalFiles || []
  },
  {
    key: 'stale',
    title: '疑似已变化',
    items: folderHealth.value?.staleLocalFiles || []
  }
].filter((section) => section.items.length))
const issueCount = computed(() => folderHealth.value?.issues?.reduce((total, issue) => total + issue.count, 0) || 0)
const canRepairFolder = computed(() => Boolean(currentFolder.value?.enabled && healthStore.selectedFolderId))

// issue.action 是后端建议，不在抽屉中自动执行；删除、启停等破坏性动作仍交给目录面板显式确认。
async function syncSelectedFolder() {
  if (!healthStore.selectedFolderId) {
    return
  }
  await knowledgeStore.syncFolder(healthStore.selectedFolderId)
  await healthStore.fetchFolderHealth(healthStore.selectedFolderId)
}

async function rebuildSelectedFolder() {
  if (!healthStore.selectedFolderId) {
    return
  }
  await knowledgeStore.rebuildFolder(healthStore.selectedFolderId)
  await healthStore.fetchFolderHealth(healthStore.selectedFolderId)
}

async function copyPath(path) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(path)
    } else {
      // Tauri WebView 或旧浏览器可能没有 Clipboard API，保留 DOM fallback 保证本地路径可复制。
      fallbackCopy(path)
    }
    ElMessage.success('路径已复制')
  } catch {
    ElMessage.error('路径复制失败')
  }
}

function fallbackCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}
</script>

<template>
  <el-drawer
    v-model="healthStore.isDrawerOpen"
    class="knowledge-health-drawer"
    size="520px"
    :title="drawerTitle"
  >
    <div v-if="currentFolder" class="knowledge-health-drawer__folder">
      <strong>{{ currentFolder.status }}</strong>
      <span>{{ issueCount }} 个问题</span>
      <p class="path-text">{{ currentFolder.folderPath }}</p>
    </div>

    <div class="knowledge-health-drawer__actions">
      <el-button
        :disabled="!canRepairFolder || knowledgeStore.isFolderBusy(healthStore.selectedFolderId)"
        @click="syncSelectedFolder"
      >
        <FolderSync aria-hidden="true" />
        <span>同步目录</span>
      </el-button>
      <el-button
        :disabled="!canRepairFolder || knowledgeStore.isFolderBusy(healthStore.selectedFolderId)"
        @click="rebuildSelectedFolder"
      >
        <RotateCcw aria-hidden="true" />
        <span>重建索引</span>
      </el-button>
    </div>

    <el-alert
      v-if="healthStore.error"
      class="settings-inline-alert"
      type="error"
      :title="healthStore.error"
      :closable="false"
      show-icon
    />

    <p v-if="healthStore.isLoadingFolder" class="panel-message">正在读取目录诊断...</p>
    <section v-else-if="folderHealth?.issues?.length" class="knowledge-issue-list">
      <article v-for="issue in folderHealth.issues" :key="issue.code" class="knowledge-issue-item">
        <strong>{{ issue.message }}</strong>
        <span>{{ issue.severity }} · {{ issue.action }}</span>
      </article>
    </section>
    <p v-if="!healthStore.isLoadingFolder && !problemSections.length" class="panel-message">
      当前目录没有需要处理的文件问题。
    </p>

    <section
      v-for="section in problemSections"
      :key="section.key"
      class="knowledge-problem-section"
    >
      <h4>{{ section.title }}</h4>
      <article
        v-for="document in section.items"
        :key="document.documentId"
        class="knowledge-problem-item"
      >
        <div>
          <strong>{{ document.fileName }}</strong>
          <p class="path-text">{{ document.sourcePath }}</p>
          <span>{{ document.message }}</span>
          <em>更新 {{ formatTime(document.updatedAt) }}</em>
        </div>
        <el-button text aria-label="复制文件路径" @click="copyPath(document.sourcePath)">
          <Copy aria-hidden="true" />
        </el-button>
      </article>
    </section>

    <section v-if="folderHealth?.runs?.length" class="knowledge-run-list">
      <h4>最近维护</h4>
      <article v-for="run in folderHealth.runs" :key="run.id" class="knowledge-run-item">
        <strong>{{ run.operation }}</strong>
        <span>{{ run.status }}</span>
        <em>{{ formatTime(run.completedAt) }} · {{ run.durationMs }} ms</em>
      </article>
    </section>
  </el-drawer>
</template>
