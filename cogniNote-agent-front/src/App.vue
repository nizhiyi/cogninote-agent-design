<script setup>
import { onMounted } from 'vue'
import AppShell from './components/app-shell.vue'
import { useKnowledgeFoldersStore } from './stores/knowledge-folders'
import { useModelConfigStore } from './stores/model-config'
import { useSearchStore } from './stores/search'
import { useSystemStore } from './stores/system'
import { useThemeStore } from './stores/theme'

const systemStore = useSystemStore()
const knowledgeFoldersStore = useKnowledgeFoldersStore()
const searchStore = useSearchStore()
const modelConfigStore = useModelConfigStore()
const themeStore = useThemeStore()

onMounted(() => {
  themeStore.applyTheme()
  systemStore.fetchStatus()
  knowledgeFoldersStore.fetchFolders()
  searchStore.fetchIndexStatus()
  modelConfigStore.fetchModelConfig()
})
</script>

<template>
  <AppShell>
    <RouterView />
  </AppShell>
</template>
