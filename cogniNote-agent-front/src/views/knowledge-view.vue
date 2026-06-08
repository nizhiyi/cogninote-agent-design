<script setup>
// knowledge-view 负责 知识库 页面或组件的状态组织、用户交互和后端同步。
import KnowledgeFolderPanel from '../components/knowledge-folder-panel.vue'
import KnowledgeSearchPanel from '../components/knowledge-search-panel.vue'
</script>

<template>
  <section class="knowledge-split">
    <KnowledgeFolderPanel />
    <KnowledgeSearchPanel />
  </section>
</template>
