<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { RefreshCw, Settings2 } from 'lucide-vue-next'
import KnowledgeDirectoryManagerPanel from './knowledge-directory-manager-panel.vue'
import KnowledgeFolderPanel from './knowledge-folder-panel.vue'
import KnowledgeGraphPanel from './knowledge-graph-panel.vue'
import KnowledgeHealthPanel from './knowledge-health-panel.vue'
import KnowledgeSearchPanel from './knowledge-search-panel.vue'
import { normalizeKnowledgePanel } from '../config/knowledge-navigation'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'
import { useKnowledgeHealthStore } from '../stores/knowledge-health'
import { useKnowledgeMaintenanceStore } from '../stores/knowledge-maintenance'
import { useModelConfigStore } from '../stores/model-config'
import { useSearchStore } from '../stores/search'

const route = useRoute()
const knowledgeStore = useKnowledgeFoldersStore()
const knowledgeHealthStore = useKnowledgeHealthStore()
const maintenanceStore = useKnowledgeMaintenanceStore()
const searchStore = useSearchStore()
const modelConfigStore = useModelConfigStore()

/**
 * 知识库工作区的面板编排组件。
 *
 * <p>这里聚合目录、索引和模型配置三个 store 的启动快照，避免每个子面板重复触发初始化请求。</p>
 */
const activePanel = computed(() => normalizeKnowledgePanel(route.query.panel))
const embeddingReady = computed(() => {
  const config = modelConfigStore.activeEmbeddingConfig
  return Boolean(config?.apiKeyConfigured && config?.modelName)
})
const isRefreshing = computed(() =>
  knowledgeStore.isLoading || knowledgeHealthStore.isLoading || searchStore.isLoadingIndexStatus
    || maintenanceStore.isLoadingQueue
)
onMounted(() => {
  // 三个请求互不依赖，并行加载能让侧栏摘要和当前面板尽快进入可用状态。
  void Promise.all([
    knowledgeStore.ensureFoldersLoaded(),
    knowledgeHealthStore.ensureHealthLoaded(),
    maintenanceStore.ensureQueueLoaded(),
    searchStore.ensureIndexStatusLoaded(),
    modelConfigStore.ensureModelConfigLoaded()
  ])
})

async function refreshWorkbench() {
  await Promise.all([
    maintenanceStore.refreshKnowledgeSnapshots(),
    modelConfigStore.ensureModelConfigLoaded()
  ])
}
</script>

<template>
  <section
    class="knowledge-workbench"
    :class="{ 'knowledge-workbench--directory-manager': activePanel === 'directories' }"
  >
    <header
      v-if="activePanel === 'search'"
      class="knowledge-workbench__header knowledge-workbench__header--actions-only"
    >
      <div class="knowledge-workbench__actions">
        <button
          class="secondary-button knowledge-workbench__refresh-button"
          type="button"
          :disabled="isRefreshing"
          aria-label="刷新知识库"
          title="刷新知识库"
          @click="refreshWorkbench"
        >
          <RefreshCw aria-hidden="true" />
        </button>
        <RouterLink class="secondary-button" :to="{ name: 'settings', query: { item: 'model-embedding' } }">
          <Settings2 aria-hidden="true" />
          <span>向量模型</span>
        </RouterLink>
      </div>
    </header>

    <el-alert
      v-if="activePanel === 'search' && !embeddingReady"
      class="settings-inline-alert"
      type="warning"
      title="尚未配置可用向量模型；知识库仍可使用关键词检索，向量/混合检索会受限。"
      :closable="false"
      show-icon
    />

    <div class="knowledge-workbench__panel">
      <KnowledgeFolderPanel v-if="activePanel === 'folders'" />
      <KnowledgeHealthPanel v-else-if="activePanel === 'health'" />
      <KnowledgeDirectoryManagerPanel v-else-if="activePanel === 'directories'" />
      <KnowledgeSearchPanel v-else-if="activePanel === 'search'" />
      <KnowledgeGraphPanel v-else />
    </div>
  </section>
</template>
