<script setup>
import { ref } from 'vue'
import KnowledgeView from './knowledge-view.vue'
import ModelConfigView from './model-config-view.vue'
import StatGrid from '../components/stat-grid.vue'
import { useSearchStore } from '../stores/search'
import { useSystemStore } from '../stores/system'

const systemStore = useSystemStore()
const searchStore = useSearchStore()
const activeSection = ref('system')

const sections = [
  { id: 'system', label: '系统' },
  { id: 'knowledge', label: '知识库' },
  { id: 'model', label: '模型' }
]
</script>

<template>
  <section class="settings-page">
    <header class="settings-header">
      <div>
        <p class="eyebrow">设置</p>
        <h2>系统与知识库配置</h2>
      </div>
      <div class="settings-tabs" role="tablist" aria-label="设置分类">
        <button
          v-for="section in sections"
          :key="section.id"
          type="button"
          :class="{ active: activeSection === section.id }"
          @click="activeSection = section.id"
        >
          {{ section.label }}
        </button>
      </div>
    </header>

    <section v-if="activeSection === 'system'" class="settings-section">
      <StatGrid
        class="settings-grid"
        :columns="3"
        :items="[
          { label: '应用', value: systemStore.status?.appName || '-' },
          { label: '版本', value: systemStore.status?.version || '-' },
          { label: '状态', value: systemStore.status?.status || '-' },
          { label: '数据目录', value: systemStore.status?.dataDir || '-', mono: true },
          { label: '索引目录', value: searchStore.indexStatus?.indexPath || '-', mono: true },
          { label: '当前能力', value: '导入 / 检索 / RAG 对话' }
        ]"
      />

      <div class="button-row">
        <button class="secondary-button" type="button" @click="systemStore.fetchStatus">刷新系统状态</button>
        <button class="secondary-button" type="button" @click="searchStore.fetchIndexStatus">刷新索引状态</button>
      </div>
    </section>

    <section v-else-if="activeSection === 'knowledge'" class="settings-section">
      <KnowledgeView />
    </section>

    <section v-else class="settings-section">
      <ModelConfigView />
    </section>
  </section>
</template>
