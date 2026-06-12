<script setup>
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

/**
 * 应用启动时拉取首屏共享快照。
 *
 * <p>这些请求互不阻塞业务页面渲染；失败状态由各自 store 暴露给侧栏、设置页和知识库页。</p>
 */
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
