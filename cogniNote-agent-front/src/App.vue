<script setup>
// App 负责 业务 页面或组件的状态组织、用户交互和后端同步。
import { onMounted } from 'vue'
import AppShell from './components/app-shell.vue'
import { useChatStore } from './stores/chat'
import { useKnowledgeFoldersStore } from './stores/knowledge-folders'
import { useModelConfigStore } from './stores/model-config'
import { useSearchStore } from './stores/search'
import { useSystemStore } from './stores/system'
import { useThemeStore } from './stores/theme'

const systemStore = useSystemStore()
const chatStore = useChatStore()
const knowledgeFoldersStore = useKnowledgeFoldersStore()
const searchStore = useSearchStore()
const modelConfigStore = useModelConfigStore()
const themeStore = useThemeStore()

onMounted(() => {
  themeStore.applyTheme()
  chatStore.initializeSessions()
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
