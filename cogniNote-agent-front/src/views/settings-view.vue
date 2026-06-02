<script setup>
import StatGrid from '../components/stat-grid.vue'
import { useSearchStore } from '../stores/search'
import { useSystemStore } from '../stores/system'

const systemStore = useSystemStore()
const searchStore = useSearchStore()
</script>

<template>
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
</template>
