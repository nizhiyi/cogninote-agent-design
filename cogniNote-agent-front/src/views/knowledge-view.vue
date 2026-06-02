<script setup>
import DocumentList from '../components/document-list.vue'
import SearchResults from '../components/search-results.vue'
import SegmentedControl from '../components/segmented-control.vue'
import StatGrid from '../components/stat-grid.vue'
import { useDocumentsStore } from '../stores/documents'
import { SEARCH_MODES, useSearchStore } from '../stores/search'
import { formatTime } from '../utils/formatters'

const documentsStore = useDocumentsStore()
const searchStore = useSearchStore()
</script>

<template>
  <StatGrid
    :items="[
      { label: '已索引文档', value: searchStore.indexStatus?.indexedDocumentCount ?? '-' },
      { label: '未索引文档', value: searchStore.indexStatus?.unindexedDocumentCount ?? '-' },
      { label: '索引 chunks', value: searchStore.indexStatus?.indexedChunkCount ?? '-' },
      { label: 'Embedding', value: searchStore.indexStatus?.embeddingConfigured ? '已启用' : '未启用' }
    ]"
  />

  <section class="index-toolbar">
    <div>
      <p class="path-text">{{ searchStore.indexStatus?.indexPath || '索引目录读取中...' }}</p>
      <p class="muted-text">最后索引：{{ formatTime(searchStore.indexStatus?.lastIndexedAt) }}</p>
    </div>
    <div class="header-actions">
      <button class="secondary-button" type="button" :disabled="searchStore.isLoadingIndexStatus" @click="searchStore.fetchIndexStatus">
        刷新索引
      </button>
      <button
        class="primary-button"
        type="button"
        :disabled="searchStore.isRebuildingIndex"
        @click="async () => { await searchStore.rebuildIndex(); await documentsStore.fetchDocuments() }"
      >
        {{ searchStore.isRebuildingIndex ? '重建中...' : '重建索引' }}
      </button>
    </div>
  </section>

  <p v-if="searchStore.indexError" class="error-message">{{ searchStore.indexError }}</p>

  <div v-if="searchStore.rebuildResult" class="result-strip result-strip--three">
    <span>索引文档 {{ searchStore.rebuildResult.indexedDocumentCount }}</span>
    <span>索引 chunks {{ searchStore.rebuildResult.indexedChunkCount }}</span>
    <span>耗时 {{ searchStore.rebuildResult.durationMs }} ms</span>
  </div>

  <form class="ingest-form" @submit.prevent="documentsStore.ingestDocuments">
    <label class="field">
      <span>本地目录路径</span>
      <input
        v-model="documentsStore.folderPath"
        type="text"
        placeholder="例如 D:/notes 或 C:/Users/you/Documents/Notes"
        autocomplete="off"
      />
    </label>

    <label class="checkbox-field">
      <input v-model="documentsStore.recursive" type="checkbox" />
      <span>递归扫描子目录</span>
    </label>

    <button class="primary-button" type="submit" :disabled="documentsStore.isIngesting">
      {{ documentsStore.isIngesting ? '导入中...' : '导入目录' }}
    </button>
  </form>

  <form class="search-form" @submit.prevent="searchStore.searchKnowledge">
    <label class="field">
      <span>检索内容</span>
      <input v-model="searchStore.query" type="text" placeholder="输入关键词或问题片段" autocomplete="off" />
    </label>

    <SegmentedControl v-model="searchStore.mode" :options="SEARCH_MODES" label="检索模式" />

    <label class="field field--small">
      <span>Top K</span>
      <input v-model="searchStore.topK" type="number" min="1" max="50" />
    </label>

    <button class="primary-button" type="submit" :disabled="searchStore.isSearching">
      {{ searchStore.isSearching ? '检索中...' : '搜索' }}
    </button>
  </form>

  <p v-if="documentsStore.documentError" class="error-message">{{ documentsStore.documentError }}</p>
  <p v-if="searchStore.searchError" class="error-message">{{ searchStore.searchError }}</p>

  <div v-if="documentsStore.ingestResult" class="result-strip">
    <span>扫描 {{ documentsStore.ingestResult.scannedCount }}</span>
    <span>解析 {{ documentsStore.ingestResult.parsedCount }}</span>
    <span>跳过 {{ documentsStore.ingestResult.skippedCount }}</span>
    <span>失败 {{ documentsStore.ingestResult.failedCount }}</span>
  </div>

  <StatGrid
    class="stats-row"
    :items="[
      { label: '文档记录', value: documentsStore.documents.length },
      { label: '解析成功', value: documentsStore.stats.parsed },
      { label: '文本块', value: documentsStore.stats.chunks },
      { label: '失败记录', value: documentsStore.stats.failed }
    ]"
  />

  <SearchResults :result="searchStore.searchResult" />

  <DocumentList
    :documents="documentsStore.documents"
    :is-loading="documentsStore.isLoadingDocuments"
    @refresh="documentsStore.fetchDocuments"
    @delete="documentsStore.deleteDocument"
  />
</template>
