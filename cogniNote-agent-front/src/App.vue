<script setup>
import { computed, onMounted, ref } from 'vue'

const navItems = [
  {
    id: 'chat',
    name: '对话',
    state: '可用',
    description: '基于知识库检索片段，流式生成带引用的回答。'
  },
  {
    id: 'knowledge',
    name: '知识库',
    state: '可检索',
    description: '导入本地文档，管理 SQLite 记录和 Lucene 索引。'
  },
  {
    id: 'model',
    name: '模型配置',
    state: 'DashScope',
    description: '配置 Spring AI Alibaba DashScope 的 Chat 与 Embedding。'
  },
  {
    id: 'settings',
    name: '系统设置',
    state: '基础',
    description: '查看系统状态、数据目录和当前阶段能力边界。'
  }
]

const searchModes = [
  { label: '关键词', value: 'KEYWORD' },
  { label: '向量', value: 'VECTOR' },
  { label: '混合', value: 'HYBRID' }
]

const activeView = ref('chat')
const systemStatus = ref(null)
const indexStatus = ref(null)
const documents = ref([])
const ingestResult = ref(null)
const rebuildResult = ref(null)
const searchResult = ref(null)
const modelConfig = ref(null)
const isLoadingStatus = ref(true)
const isLoadingIndexStatus = ref(false)
const isLoadingDocuments = ref(false)
const isLoadingModelConfig = ref(false)
const isIngesting = ref(false)
const isRebuildingIndex = ref(false)
const isSearching = ref(false)
const isSavingModelConfig = ref(false)
const isTestingModelConfig = ref(false)
const isChatStreaming = ref(false)
const statusError = ref('')
const indexError = ref('')
const documentError = ref('')
const searchError = ref('')
const modelConfigError = ref('')
const modelConfigMessage = ref('')
const chatError = ref('')
const folderPath = ref('')
const recursive = ref(true)
const searchQuery = ref('')
const searchMode = ref('KEYWORD')
const searchTopK = ref(8)
const chatQuestion = ref('')
const chatMode = ref('HYBRID')
const chatTopK = ref(8)
const chatAnswer = ref('')
const chatSources = ref([])
const chatRetrievalMode = ref('')
const chatConversationId = ref('')
const chatAbortController = ref(null)
const modelForm = ref({
  apiKey: '',
  chatModel: 'qwen-plus',
  embeddingModel: 'text-embedding-v4',
  embeddingDimensions: 1024,
  temperature: 0.7,
  topK: 8
})

const activeNavItem = computed(() => navItems.find((item) => item.id === activeView.value) || navItems[0])

const connectionLabel = computed(() => {
  if (isLoadingStatus.value) {
    return '连接中'
  }

  return statusError.value ? '未连接' : '已连接'
})

const connectionClass = computed(() => ({
  'status-pill': true,
  'status-pill--loading': isLoadingStatus.value,
  'status-pill--error': Boolean(statusError.value),
  'status-pill--ok': !isLoadingStatus.value && !statusError.value
}))

const documentStats = computed(() => {
  const parsed = documents.value.filter((document) => document.status === 'PARSED').length
  const failed = documents.value.filter((document) => document.status === 'FAILED').length
  const chunks = documents.value.reduce((total, document) => total + document.chunkCount, 0)

  return { parsed, failed, chunks }
})

const modelApiKeyPlaceholder = computed(() => {
  if (modelConfig.value?.apiKeyConfigured) {
    return '已保存，留空表示继续使用当前 Key'
  }

  return '请输入 DashScope API Key'
})

const chatCanSend = computed(() => chatQuestion.value.trim().length > 0 && !isChatStreaming.value)

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`)
  }

  return payload
}

function jsonOptions(method, body) {
  return {
    method,
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  }
}

async function fetchSystemStatus() {
  isLoadingStatus.value = true
  statusError.value = ''

  try {
    systemStatus.value = await fetchJson('/api/system/status')
  } catch (error) {
    systemStatus.value = null
    statusError.value = `后端服务暂不可用：${error.message}`
  } finally {
    isLoadingStatus.value = false
  }
}

async function fetchDocuments() {
  isLoadingDocuments.value = true
  documentError.value = ''

  try {
    documents.value = await fetchJson('/api/documents')
  } catch (error) {
    documents.value = []
    documentError.value = `文档列表读取失败：${error.message}`
  } finally {
    isLoadingDocuments.value = false
  }
}

async function fetchIndexStatus() {
  isLoadingIndexStatus.value = true
  indexError.value = ''

  try {
    indexStatus.value = await fetchJson('/api/index/status')
  } catch (error) {
    indexStatus.value = null
    indexError.value = `索引状态读取失败：${error.message}`
  } finally {
    isLoadingIndexStatus.value = false
  }
}

async function fetchModelConfig() {
  isLoadingModelConfig.value = true
  modelConfigError.value = ''
  modelConfigMessage.value = ''

  try {
    const config = await fetchJson('/api/model-config')
    modelConfig.value = config
    modelForm.value = {
      apiKey: '',
      chatModel: config.chatModel,
      embeddingModel: config.embeddingModel,
      embeddingDimensions: config.embeddingDimensions,
      temperature: config.temperature,
      topK: config.topK
    }
    chatTopK.value = config.topK
  } catch (error) {
    modelConfig.value = null
    modelConfigError.value = `模型配置读取失败：${error.message}`
  } finally {
    isLoadingModelConfig.value = false
  }
}

function modelRequestPayload() {
  return {
    apiKey: modelForm.value.apiKey,
    chatModel: modelForm.value.chatModel.trim(),
    embeddingModel: modelForm.value.embeddingModel.trim(),
    embeddingDimensions: Number(modelForm.value.embeddingDimensions),
    temperature: Number(modelForm.value.temperature),
    topK: Number(modelForm.value.topK)
  }
}

async function saveModelConfig() {
  isSavingModelConfig.value = true
  modelConfigError.value = ''
  modelConfigMessage.value = ''

  try {
    const saved = await fetchJson('/api/model-config', jsonOptions('PUT', modelRequestPayload()))
    modelConfig.value = saved
    modelForm.value.apiKey = ''
    chatTopK.value = saved.topK
    modelConfigMessage.value = '模型配置已保存'
    await fetchIndexStatus()
  } catch (error) {
    modelConfigError.value = `保存失败：${error.message}`
  } finally {
    isSavingModelConfig.value = false
  }
}

async function testModelConfig() {
  isTestingModelConfig.value = true
  modelConfigError.value = ''
  modelConfigMessage.value = ''

  try {
    const result = await fetchJson('/api/model-config/test', jsonOptions('POST', modelRequestPayload()))
    modelConfigMessage.value = result.message || 'DashScope 连接测试成功'
  } catch (error) {
    modelConfigError.value = `连接测试失败：${error.message}`
  } finally {
    isTestingModelConfig.value = false
  }
}

async function ingestDocuments() {
  const trimmedFolderPath = folderPath.value.trim()
  if (!trimmedFolderPath) {
    documentError.value = '请输入要导入的本地目录路径'
    return
  }

  isIngesting.value = true
  ingestResult.value = null
  documentError.value = ''

  try {
    ingestResult.value = await fetchJson('/api/documents/ingest', jsonOptions('POST', {
      folderPath: trimmedFolderPath,
      recursive: recursive.value
    }))
    await fetchDocuments()
    await fetchIndexStatus()
  } catch (error) {
    documentError.value = `导入失败：${error.message}`
  } finally {
    isIngesting.value = false
  }
}

async function deleteDocument(id) {
  documentError.value = ''

  try {
    await fetchJson(`/api/documents/${id}`, { method: 'DELETE' })
    await fetchDocuments()
    await fetchIndexStatus()
    if (searchResult.value?.hits?.length) {
      await searchKnowledge()
    }
  } catch (error) {
    documentError.value = `删除索引记录失败：${error.message}`
  }
}

async function rebuildIndex() {
  isRebuildingIndex.value = true
  rebuildResult.value = null
  indexError.value = ''

  try {
    rebuildResult.value = await fetchJson('/api/index/rebuild', { method: 'POST' })
    await fetchDocuments()
    await fetchIndexStatus()
  } catch (error) {
    indexError.value = `重建索引失败：${error.message}`
  } finally {
    isRebuildingIndex.value = false
  }
}

async function searchKnowledge() {
  const query = searchQuery.value.trim()
  if (!query) {
    searchError.value = '请输入检索关键词'
    return
  }

  isSearching.value = true
  searchResult.value = null
  searchError.value = ''

  try {
    searchResult.value = await fetchJson('/api/search', jsonOptions('POST', {
      query,
      mode: searchMode.value,
      topK: Number(searchTopK.value)
    }))
  } catch (error) {
    searchError.value = `检索失败：${error.message}`
  } finally {
    isSearching.value = false
  }
}

async function streamChat() {
  const question = chatQuestion.value.trim()
  if (!question) {
    chatError.value = '请输入问题'
    return
  }

  resetChatResponse()
  isChatStreaming.value = true
  chatAbortController.value = new AbortController()

  try {
    const response = await fetch('/api/chat/stream', {
      ...jsonOptions('POST', {
        question,
        mode: chatMode.value,
        topK: Number(chatTopK.value)
      }),
      signal: chatAbortController.value.signal
    })

    if (!response.ok) {
      const payload = await response.json().catch(() => null)
      throw new Error(payload?.message || `HTTP ${response.status}`)
    }

    if (!response.body) {
      throw new Error('当前浏览器不支持流式响应')
    }

    // EventSource does not support POST with JSON body, so the stream is parsed from fetch manually.
    await readSseStream(response.body)
  } catch (error) {
    if (error.name !== 'AbortError') {
      chatError.value = `对话失败：${error.message}`
    }
  } finally {
    isChatStreaming.value = false
    chatAbortController.value = null
  }
}

function resetChatResponse() {
  chatAnswer.value = ''
  chatSources.value = []
  chatRetrievalMode.value = ''
  chatConversationId.value = ''
  chatError.value = ''
}

function stopChat() {
  chatAbortController.value?.abort()
  isChatStreaming.value = false
}

async function readSseStream(body) {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const dispatchEvent = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }

    const rawData = dataLines.join('\n')
    const payload = parseSsePayload(rawData)
    handleChatEvent(eventName, payload)
    eventName = 'message'
    dataLines = []
  }

  const handleLine = (line) => {
    if (line === '') {
      dispatchEvent()
      return
    }

    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      return
    }

    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    let newlineIndex = buffer.indexOf('\n')
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).replace(/\r$/, '')
      buffer = buffer.slice(newlineIndex + 1)
      handleLine(line)
      newlineIndex = buffer.indexOf('\n')
    }
  }

  buffer += decoder.decode()
  if (buffer.length > 0) {
    handleLine(buffer.replace(/\r$/, ''))
  }
  dispatchEvent()
}

function parseSsePayload(rawData) {
  try {
    return JSON.parse(rawData)
  } catch {
    return { text: rawData }
  }
}

function handleChatEvent(eventName, payload) {
  if (eventName === 'meta') {
    chatConversationId.value = payload.conversationId || ''
    chatRetrievalMode.value = payload.retrievalMode || ''
    chatSources.value = payload.sources || []
    return
  }

  if (eventName === 'delta') {
    chatAnswer.value += payload.text || ''
    return
  }

  if (eventName === 'error') {
    chatError.value = payload.message || '模型返回错误'
  }
}

function useSourceQuestion(source) {
  chatQuestion.value = `请解释 ${source.fileName} 中和这段内容相关的要点。`
  activeView.value = 'chat'
}

function formatFileSize(size) {
  if (size < 1024) {
    return `${size} B`
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(timestamp) {
  if (!timestamp) {
    return '-'
  }

  return new Date(timestamp).toLocaleString()
}

function formatScore(score) {
  return typeof score === 'number' ? score.toFixed(3) : '-'
}

onMounted(() => {
  fetchSystemStatus()
  fetchDocuments()
  fetchIndexStatus()
  fetchModelConfig()
})
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">本地个人知识库智能体</p>
        <h1>CogniNote Agent</h1>
        <p class="subtitle">
          第四阶段进入 RAG 对话闭环：配置 DashScope，检索知识片段，流式回答并展示引用来源。
        </p>
      </div>

      <aside class="connection-panel" aria-label="后端连接状态">
        <div class="panel-header">
          <span>后端连接</span>
          <span :class="connectionClass">{{ connectionLabel }}</span>
        </div>
        <p v-if="systemStatus" class="connection-summary">
          {{ systemStatus.appName }} / {{ systemStatus.version }}
        </p>
        <p v-else class="panel-message">
          {{ isLoadingStatus ? '正在读取系统状态...' : statusError }}
        </p>
        <button class="secondary-button" type="button" :disabled="isLoadingStatus" @click="fetchSystemStatus">
          刷新
        </button>
      </aside>
    </header>

    <nav class="module-nav" aria-label="功能入口">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        :class="['module-tab', { active: activeView === item.id }]"
        @click="activeView = item.id"
      >
        <span class="module-tab__top">
          <strong>{{ item.name }}</strong>
          <em>{{ item.state }}</em>
        </span>
        <span>{{ item.description }}</span>
      </button>
    </nav>

    <section class="workspace" :aria-label="activeNavItem.name">
      <div class="workspace-header">
        <div>
          <p class="eyebrow">{{ activeNavItem.state }}</p>
          <h2>{{ activeNavItem.name }}</h2>
        </div>
        <p>{{ activeNavItem.description }}</p>
      </div>

      <template v-if="activeView === 'chat'">
        <section class="chat-layout">
          <form class="chat-composer" @submit.prevent="streamChat">
            <label class="field">
              <span>问题</span>
              <textarea
                v-model="chatQuestion"
                rows="6"
                placeholder="例如：这个项目如何打包？"
              ></textarea>
            </label>

            <div class="inline-controls">
              <div class="segmented-control" role="group" aria-label="RAG 检索模式">
                <button
                  v-for="mode in searchModes"
                  :key="mode.value"
                  type="button"
                  :class="{ active: chatMode === mode.value }"
                  @click="chatMode = mode.value"
                >
                  {{ mode.label }}
                </button>
              </div>

              <label class="field field--small">
                <span>Top K</span>
                <input v-model="chatTopK" type="number" min="1" max="50" />
              </label>
            </div>

            <div class="button-row">
              <button class="primary-button" type="submit" :disabled="!chatCanSend">
                {{ isChatStreaming ? '回答中...' : '发送问题' }}
              </button>
              <button
                class="secondary-button"
                type="button"
                :disabled="!isChatStreaming"
                @click="stopChat"
              >
                停止
              </button>
            </div>

            <p v-if="chatError" class="error-message">{{ chatError }}</p>
            <p v-if="!modelConfig?.apiKeyConfigured" class="hint-message">
              尚未保存 DashScope API Key。请先到“模型配置”页保存后再对话。
            </p>
          </form>

          <article class="answer-panel" aria-live="polite">
            <div class="section-title-line">
              <h3>回答</h3>
              <span>{{ chatRetrievalMode || '等待提问' }}</span>
            </div>
            <p v-if="!chatAnswer && !isChatStreaming" class="panel-message">
              这里会显示模型的流式回答。回答中的 [1]、[2] 对应下方引用来源。
            </p>
            <p v-else-if="!chatAnswer && isChatStreaming" class="panel-message">正在等待模型返回...</p>
            <div v-else class="answer-text">{{ chatAnswer }}</div>
            <p v-if="chatConversationId" class="path-text">conversationId: {{ chatConversationId }}</p>
          </article>
        </section>

        <section class="sources-panel" aria-label="引用来源">
          <div class="section-title-line">
            <h3>引用来源</h3>
            <span>{{ chatSources.length }} sources</span>
          </div>
          <p v-if="chatSources.length === 0" class="panel-message">发送问题后会列出本次检索命中的文档片段。</p>
          <article v-for="source in chatSources" :key="source.chunkId" class="source-row">
            <div class="source-index">[{{ source.index }}]</div>
            <div class="source-main">
              <div class="document-title-line">
                <h4>{{ source.fileName }}</h4>
                <span class="score-chip">{{ formatScore(source.score) }}</span>
              </div>
              <p class="path-text">{{ source.sourcePath }}</p>
              <p class="hit-preview">{{ source.preview }}</p>
              <div class="document-meta">
                <span v-if="source.heading">标题：{{ source.heading }}</span>
                <span v-if="source.pageNumber">页码：{{ source.pageNumber }}</span>
                <span>chunk：{{ source.chunkId }}</span>
              </div>
            </div>
            <button class="text-button" type="button" @click="useSourceQuestion(source)">追问</button>
          </article>
        </section>
      </template>

      <template v-else-if="activeView === 'knowledge'">
        <section class="index-status-grid" aria-label="索引状态">
          <div>
            <span>已索引文档</span>
            <strong>{{ indexStatus?.indexedDocumentCount ?? '-' }}</strong>
          </div>
          <div>
            <span>未索引文档</span>
            <strong>{{ indexStatus?.unindexedDocumentCount ?? '-' }}</strong>
          </div>
          <div>
            <span>索引 chunks</span>
            <strong>{{ indexStatus?.indexedChunkCount ?? '-' }}</strong>
          </div>
          <div>
            <span>Embedding</span>
            <strong>{{ indexStatus?.embeddingConfigured ? '已启用' : '未启用' }}</strong>
          </div>
        </section>

        <section class="index-toolbar">
          <div>
            <p class="path-text">{{ indexStatus?.indexPath || '索引目录读取中...' }}</p>
            <p class="muted-text">最后索引：{{ formatTime(indexStatus?.lastIndexedAt) }}</p>
          </div>
          <div class="header-actions">
            <button class="secondary-button" type="button" :disabled="isLoadingIndexStatus" @click="fetchIndexStatus">
              刷新索引
            </button>
            <button class="primary-button" type="button" :disabled="isRebuildingIndex" @click="rebuildIndex">
              {{ isRebuildingIndex ? '重建中...' : '重建索引' }}
            </button>
          </div>
        </section>

        <p v-if="indexError" class="error-message">{{ indexError }}</p>

        <div v-if="rebuildResult" class="result-strip result-strip--three">
          <span>索引文档 {{ rebuildResult.indexedDocumentCount }}</span>
          <span>索引 chunks {{ rebuildResult.indexedChunkCount }}</span>
          <span>耗时 {{ rebuildResult.durationMs }} ms</span>
        </div>

        <form class="ingest-form" @submit.prevent="ingestDocuments">
          <label class="field">
            <span>本地目录路径</span>
            <input
              v-model="folderPath"
              type="text"
              placeholder="例如 D:/notes 或 C:/Users/you/Documents/Notes"
              autocomplete="off"
            />
          </label>

          <label class="checkbox-field">
            <input v-model="recursive" type="checkbox" />
            <span>递归扫描子目录</span>
          </label>

          <button class="primary-button" type="submit" :disabled="isIngesting">
            {{ isIngesting ? '导入中...' : '导入目录' }}
          </button>
        </form>

        <form class="search-form" @submit.prevent="searchKnowledge">
          <label class="field">
            <span>检索内容</span>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="输入关键词或问题片段"
              autocomplete="off"
            />
          </label>

          <div class="segmented-control" role="group" aria-label="检索模式">
            <button
              v-for="mode in searchModes"
              :key="mode.value"
              type="button"
              :class="{ active: searchMode === mode.value }"
              @click="searchMode = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>

          <label class="field field--small">
            <span>Top K</span>
            <input v-model="searchTopK" type="number" min="1" max="50" />
          </label>

          <button class="primary-button" type="submit" :disabled="isSearching">
            {{ isSearching ? '检索中...' : '搜索' }}
          </button>
        </form>

        <p v-if="documentError" class="error-message">{{ documentError }}</p>
        <p v-if="searchError" class="error-message">{{ searchError }}</p>

        <div v-if="ingestResult" class="result-strip">
          <span>扫描 {{ ingestResult.scannedCount }}</span>
          <span>解析 {{ ingestResult.parsedCount }}</span>
          <span>跳过 {{ ingestResult.skippedCount }}</span>
          <span>失败 {{ ingestResult.failedCount }}</span>
        </div>

        <section class="stats-row" aria-label="文档统计">
          <div>
            <strong>{{ documents.length }}</strong>
            <span>文档记录</span>
          </div>
          <div>
            <strong>{{ documentStats.parsed }}</strong>
            <span>解析成功</span>
          </div>
          <div>
            <strong>{{ documentStats.chunks }}</strong>
            <span>文本块</span>
          </div>
          <div>
            <strong>{{ documentStats.failed }}</strong>
            <span>失败记录</span>
          </div>
        </section>

        <section v-if="searchResult" class="search-results">
          <div class="section-title-line">
            <h3>检索结果</h3>
            <span>{{ searchResult.mode }} / {{ searchResult.hits.length }} hits</span>
          </div>

          <p v-if="searchResult.hits.length === 0" class="panel-message">没有命中文档片段。</p>

          <article v-for="hit in searchResult.hits" v-else :key="hit.chunkId" class="search-hit">
            <div class="search-hit__top">
              <h4>{{ hit.fileName }}</h4>
              <span>{{ formatScore(hit.score) }}</span>
            </div>
            <p class="path-text">{{ hit.sourcePath }}</p>
            <p class="hit-preview">{{ hit.preview }}</p>
            <div class="document-meta">
              <span v-if="hit.heading">标题：{{ hit.heading }}</span>
              <span v-if="hit.pageNumber">页码：{{ hit.pageNumber }}</span>
              <span v-if="hit.keywordScore !== null && hit.keywordScore !== undefined">
                BM25 {{ formatScore(hit.keywordScore) }}
              </span>
              <span v-if="hit.vectorScore !== null && hit.vectorScore !== undefined">
                Vector {{ formatScore(hit.vectorScore) }}
              </span>
            </div>
          </article>
        </section>

        <section class="document-list">
          <div class="section-title-line">
            <h3>文档列表</h3>
            <button class="secondary-button" type="button" :disabled="isLoadingDocuments" @click="fetchDocuments">
              刷新列表
            </button>
          </div>
          <p v-if="isLoadingDocuments" class="panel-message">正在读取文档列表...</p>
          <p v-else-if="documents.length === 0" class="panel-message">还没有导入文档。</p>

          <template v-else>
            <article v-for="document in documents" :key="document.id" class="document-row">
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
              <button class="text-button" type="button" @click="deleteDocument(document.id)">删除记录</button>
            </article>
          </template>
        </section>
      </template>

      <template v-else-if="activeView === 'model'">
        <form class="model-form" @submit.prevent="saveModelConfig">
          <label class="field field--full">
            <span>DashScope API Key</span>
            <input
              v-model="modelForm.apiKey"
              type="password"
              :placeholder="modelApiKeyPlaceholder"
              autocomplete="off"
            />
          </label>

          <label class="field">
            <span>Chat 模型</span>
            <input v-model="modelForm.chatModel" type="text" autocomplete="off" />
          </label>

          <label class="field">
            <span>Embedding 模型</span>
            <input v-model="modelForm.embeddingModel" type="text" autocomplete="off" />
          </label>

          <label class="field">
            <span>Embedding 维度</span>
            <input v-model="modelForm.embeddingDimensions" type="number" min="1" max="8192" />
          </label>

          <label class="field">
            <span>Temperature</span>
            <input v-model="modelForm.temperature" type="number" min="0" max="2" step="0.1" />
          </label>

          <label class="field">
            <span>默认 Top K</span>
            <input v-model="modelForm.topK" type="number" min="1" max="50" />
          </label>

          <div class="model-form__actions">
            <button class="primary-button" type="submit" :disabled="isSavingModelConfig">
              {{ isSavingModelConfig ? '保存中...' : '保存配置' }}
            </button>
            <button class="secondary-button" type="button" :disabled="isTestingModelConfig" @click="testModelConfig">
              {{ isTestingModelConfig ? '测试中...' : '测试连接' }}
            </button>
            <button class="secondary-button" type="button" :disabled="isLoadingModelConfig" @click="fetchModelConfig">
              重新读取
            </button>
          </div>
        </form>

        <p v-if="modelConfigError" class="error-message">{{ modelConfigError }}</p>
        <p v-if="modelConfigMessage" class="success-message">{{ modelConfigMessage }}</p>

        <section class="config-summary">
          <div>
            <span>Provider</span>
            <strong>{{ modelConfig?.provider || 'DASHSCOPE' }}</strong>
          </div>
          <div>
            <span>API Key</span>
            <strong>{{ modelConfig?.apiKeyConfigured ? '已保存' : '未配置' }}</strong>
          </div>
          <div>
            <span>Chat</span>
            <strong>{{ modelConfig?.chatModel || modelForm.chatModel }}</strong>
          </div>
          <div>
            <span>Embedding</span>
            <strong>{{ modelConfig?.embeddingModel || modelForm.embeddingModel }}</strong>
          </div>
          <div>
            <span>更新于</span>
            <strong>{{ formatTime(modelConfig?.updatedAt) }}</strong>
          </div>
        </section>

        <p class="warning-message">
          当前阶段 API Key 会以明文保存到本机 SQLite，仅用于开发态闭环；后续交付阶段再接入 Windows 本地加密或凭据管理。
        </p>
      </template>

      <template v-else>
        <section class="settings-grid">
          <div>
            <span>应用</span>
            <strong>{{ systemStatus?.appName || '-' }}</strong>
          </div>
          <div>
            <span>版本</span>
            <strong>{{ systemStatus?.version || '-' }}</strong>
          </div>
          <div>
            <span>状态</span>
            <strong>{{ systemStatus?.status || '-' }}</strong>
          </div>
          <div>
            <span>数据目录</span>
            <strong class="path-text">{{ systemStatus?.dataDir || '-' }}</strong>
          </div>
          <div>
            <span>索引目录</span>
            <strong class="path-text">{{ indexStatus?.indexPath || '-' }}</strong>
          </div>
          <div>
            <span>当前能力</span>
            <strong>导入 / 检索 / RAG 对话</strong>
          </div>
        </section>

        <div class="button-row">
          <button class="secondary-button" type="button" @click="fetchSystemStatus">刷新系统状态</button>
          <button class="secondary-button" type="button" @click="fetchIndexStatus">刷新索引状态</button>
        </div>
      </template>
    </section>
  </main>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  min-width: 320px;
  margin: 0;
  color: #182230;
  background: #f4f7f8;
  font-family:
    Inter,
    "Microsoft YaHei",
    "PingFang SC",
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
}

:global(button),
:global(input),
:global(textarea) {
  font: inherit;
}

.app-shell {
  width: min(1180px, calc(100% - 32px));
  margin: 0 auto;
  padding: 32px 0 44px;
}

.topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 20px;
  align-items: stretch;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2b6f61;
  font-size: 13px;
  font-weight: 800;
}

h1,
h2,
h3,
h4 {
  margin: 0;
  color: #101828;
  letter-spacing: 0;
}

h1 {
  font-size: 44px;
  line-height: 1.1;
}

h2 {
  font-size: 30px;
}

h3 {
  font-size: 20px;
}

h4 {
  font-size: 17px;
}

.subtitle {
  max-width: 820px;
  margin: 14px 0 0;
  color: #526071;
  font-size: 17px;
  line-height: 1.7;
}

.connection-panel,
.workspace {
  border: 1px solid #d8e2e7;
  border-radius: 8px;
  background: #ffffff;
}

.connection-panel {
  display: grid;
  gap: 14px;
  align-content: start;
  padding: 18px;
}

.panel-header,
.section-title-line,
.document-title-line,
.search-hit__top {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.panel-header {
  font-weight: 800;
}

.connection-summary,
.panel-message,
.hint-message,
.warning-message,
.success-message,
.error-message {
  margin: 0;
  line-height: 1.65;
}

.connection-summary,
.panel-message,
.hint-message {
  color: #526071;
}

.status-pill {
  min-width: 72px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}

.status-pill--loading {
  color: #715300;
  background: #fff2bf;
}

.status-pill--ok {
  color: #0f513f;
  background: #d7f4e8;
}

.status-pill--error {
  color: #842029;
  background: #f8d7da;
}

.module-nav {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.module-tab {
  display: grid;
  min-height: 132px;
  gap: 14px;
  padding: 18px;
  border: 1px solid #d8e2e7;
  border-radius: 8px;
  color: #526071;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background 160ms ease,
    color 160ms ease;
}

.module-tab:hover,
.module-tab:focus-visible,
.module-tab.active {
  border-color: #1f6f68;
  background: #f1faf7;
  color: #31514d;
}

.module-tab__top {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.module-tab strong {
  color: #101828;
  font-size: 19px;
}

.module-tab em {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: #31514d;
  background: #dff2ee;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.workspace {
  margin-top: 18px;
  padding: 24px;
}

.workspace-header {
  display: grid;
  grid-template-columns: minmax(0, 360px) minmax(0, 1fr);
  gap: 24px;
  align-items: end;
  margin-bottom: 22px;
}

.workspace-header p:last-child {
  margin: 0;
  color: #526071;
  line-height: 1.7;
}

.chat-layout {
  display: grid;
  grid-template-columns: minmax(300px, 420px) minmax(0, 1fr);
  gap: 18px;
}

.chat-composer,
.answer-panel,
.sources-panel,
.search-hit,
.document-row,
.source-row {
  border: 1px solid #e1e8ec;
  border-radius: 8px;
  background: #fbfcfd;
}

.chat-composer,
.answer-panel,
.sources-panel {
  padding: 18px;
}

.chat-composer {
  display: grid;
  gap: 16px;
  align-content: start;
}

.field {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.field span,
.checkbox-field {
  color: #475467;
  font-size: 14px;
  font-weight: 800;
}

.field input,
.field textarea {
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #cfd8e6;
  border-radius: 6px;
  color: #182230;
  background: #ffffff;
}

.field textarea {
  min-height: 150px;
  padding: 12px;
  line-height: 1.6;
  resize: vertical;
}

.field--small {
  width: 98px;
}

.field--small input {
  text-align: center;
}

.field--full {
  grid-column: 1 / -1;
}

.inline-controls,
.button-row,
.header-actions,
.model-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: end;
}

.segmented-control {
  display: flex;
  min-height: 44px;
  overflow: hidden;
  border: 1px solid #bdd2ca;
  border-radius: 6px;
  background: #ffffff;
}

.segmented-control button {
  min-width: 72px;
  border: 0;
  border-right: 1px solid #d7e2de;
  color: #31514d;
  background: transparent;
  cursor: pointer;
}

.segmented-control button:last-child {
  border-right: 0;
}

.segmented-control button.active {
  color: #ffffff;
  background: #1f6f68;
}

.primary-button,
.secondary-button,
.text-button {
  min-height: 44px;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 160ms ease,
    border-color 160ms ease,
    color 160ms ease,
    opacity 160ms ease;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #1f6f68;
}

.primary-button:hover:not(:disabled),
.primary-button:focus-visible {
  background: #175b55;
}

.secondary-button {
  border: 1px solid #bdd2ca;
  color: #1f6f68;
  background: #f6fbf9;
}

.secondary-button:hover:not(:disabled),
.secondary-button:focus-visible {
  border-color: #1f6f68;
}

.text-button {
  flex: 0 0 auto;
  border: 0;
  color: #9a3412;
  background: transparent;
}

.text-button:hover,
.text-button:focus-visible {
  color: #7c2d12;
  background: #fff3ed;
}

.primary-button:focus-visible,
.secondary-button:focus-visible,
.text-button:focus-visible,
.module-tab:focus-visible,
.segmented-control button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid #9ee6d8;
  outline-offset: 2px;
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.answer-panel {
  display: grid;
  gap: 16px;
  align-content: start;
  min-height: 360px;
}

.answer-text {
  white-space: pre-wrap;
  color: #253041;
  line-height: 1.8;
}

.sources-panel,
.search-results,
.document-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.source-row,
.document-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
}

.source-index {
  display: grid;
  flex: 0 0 48px;
  min-height: 48px;
  place-items: center;
  border-radius: 6px;
  color: #18413d;
  background: #dff2ee;
  font-weight: 900;
}

.source-main,
.document-main {
  min-width: 0;
  flex: 1 1 auto;
}

.path-text {
  overflow-wrap: anywhere;
  font-family:
    "Cascadia Mono",
    "SFMono-Regular",
    Consolas,
    monospace;
  font-size: 13px;
}

.hit-preview {
  margin: 10px 0;
  color: #354152;
  line-height: 1.7;
}

.document-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #667085;
  font-size: 13px;
}

.score-chip,
.search-hit__top span {
  padding: 4px 8px;
  border-radius: 999px;
  color: #18413d;
  background: #dff2ee;
  font-size: 12px;
  font-weight: 800;
}

.index-status-grid,
.stats-row,
.config-summary,
.settings-grid {
  display: grid;
  gap: 12px;
}

.index-status-grid,
.stats-row {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.config-summary {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 18px;
}

.settings-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.index-status-grid div,
.stats-row div,
.config-summary div,
.settings-grid div {
  min-width: 0;
  padding: 16px;
  border: 1px solid #dfe8ee;
  border-radius: 8px;
  background: #f7fbfc;
}

.index-status-grid span,
.stats-row span,
.config-summary span,
.settings-grid span,
.muted-text {
  color: #526071;
  font-size: 13px;
}

.index-status-grid strong,
.stats-row strong,
.config-summary strong,
.settings-grid strong {
  display: block;
  margin-top: 8px;
  color: #101828;
  font-size: 20px;
  line-height: 1.35;
}

.index-status-grid strong,
.stats-row strong {
  font-size: 26px;
}

.index-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  background: #ffffff;
}

.index-toolbar p {
  margin: 0;
}

.muted-text {
  margin-top: 6px;
}

.ingest-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 14px;
  align-items: end;
  margin-top: 22px;
}

.search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 96px auto;
  gap: 14px;
  align-items: end;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e1e7ef;
}

.checkbox-field {
  display: flex;
  min-height: 44px;
  gap: 8px;
  align-items: center;
  white-space: nowrap;
}

.checkbox-field input {
  width: 18px;
  height: 18px;
  accent-color: #1f6f68;
}

.error-message {
  margin-top: 16px;
  color: #842029;
}

.success-message {
  margin-top: 16px;
  color: #0f513f;
}

.warning-message {
  margin-top: 18px;
  padding: 14px 16px;
  border: 1px solid #f6d7a9;
  border-radius: 8px;
  color: #7a4a08;
  background: #fff8e8;
}

.result-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.result-strip--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.result-strip span {
  padding: 10px 12px;
  border-radius: 6px;
  color: #18413d;
  background: #e7f5f1;
  font-weight: 800;
  text-align: center;
}

.search-hit {
  padding: 16px;
}

.document-title-line {
  justify-content: flex-start;
}

.document-main p,
.source-main p {
  margin: 8px 0;
  color: #526071;
}

.status-chip {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.status-chip--parsed {
  color: #0f513f;
  background: #d7f4e8;
}

.status-chip--failed {
  color: #842029;
  background: #f8d7da;
}

.status-chip--skipped {
  color: #715300;
  background: #fff2bf;
}

.model-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.model-form__actions {
  grid-column: 1 / -1;
}

@media (max-width: 980px) {
  .topbar,
  .workspace-header,
  .chat-layout,
  .module-nav,
  .index-status-grid,
  .stats-row,
  .config-summary,
  .settings-grid,
  .ingest-form,
  .search-form,
  .model-form,
  .result-strip {
    grid-template-columns: 1fr;
  }

  .field--full,
  .model-form__actions {
    grid-column: auto;
  }

  .source-row,
  .document-row,
  .index-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .app-shell {
    width: min(100% - 24px, 1180px);
    padding: 24px 0;
  }

  .workspace,
  .connection-panel {
    padding: 18px;
  }

  h1 {
    font-size: 34px;
  }

  h2 {
    font-size: 25px;
  }

  .subtitle {
    font-size: 16px;
  }

  .inline-controls,
  .button-row,
  .header-actions,
  .model-form__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .segmented-control {
    width: 100%;
  }

  .segmented-control button {
    flex: 1;
    min-width: 0;
  }

  .field--small {
    width: 100%;
  }
}
</style>
