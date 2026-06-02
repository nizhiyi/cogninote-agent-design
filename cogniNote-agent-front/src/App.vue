<script setup>
import { onMounted } from 'vue'
import AppShell from './components/app-shell.vue'
import { useDocumentsStore } from './stores/documents'
import { useModelConfigStore } from './stores/model-config'
import { useSearchStore } from './stores/search'
import { useSystemStore } from './stores/system'

const systemStore = useSystemStore()
const documentsStore = useDocumentsStore()
const searchStore = useSearchStore()
const modelConfigStore = useModelConfigStore()

onMounted(() => {
  systemStore.fetchStatus()
  documentsStore.fetchDocuments()
  searchStore.fetchIndexStatus()
  modelConfigStore.fetchModelConfig()
})
</script>

<template>
  <AppShell>
    <RouterView />
  </AppShell>
</template>
