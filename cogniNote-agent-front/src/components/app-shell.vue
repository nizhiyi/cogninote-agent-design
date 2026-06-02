<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ModuleNav from './module-nav.vue'
import StatusPill from './status-pill.vue'
import { useSystemStore } from '../stores/system'

const route = useRoute()
const systemStore = useSystemStore()

const navItems = [
  {
    id: 'chat',
    to: { name: 'chat' },
    name: '对话',
    state: '可用',
    description: '基于知识库检索片段，流式生成带引用的回答。'
  },
  {
    id: 'knowledge',
    to: { name: 'knowledge' },
    name: '知识库',
    state: '可检索',
    description: '导入本地文档，管理 SQLite 记录和 Lucene 索引。'
  },
  {
    id: 'model-config',
    to: { name: 'model-config' },
    name: '模型配置',
    state: 'DashScope',
    description: '配置 Spring AI Alibaba DashScope 的 Chat 与 Embedding。'
  },
  {
    id: 'settings',
    to: { name: 'settings' },
    name: '系统设置',
    state: '基础',
    description: '查看系统状态、数据目录和当前阶段能力边界。'
  }
]

const activeNavItem = computed(() => navItems.find((item) => item.id === route.name) || navItems[0])
const pillState = computed(() => {
  if (systemStore.isLoading) {
    return 'loading'
  }
  return systemStore.error ? 'error' : 'ok'
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
          <StatusPill :label="systemStore.connectionLabel" :state="pillState" />
        </div>
        <p v-if="systemStore.status" class="connection-summary">
          {{ systemStore.status.appName }} / {{ systemStore.status.version }}
        </p>
        <p v-else class="panel-message">
          {{ systemStore.isLoading ? '正在读取系统状态...' : systemStore.error }}
        </p>
        <button class="secondary-button" type="button" :disabled="systemStore.isLoading" @click="systemStore.fetchStatus">
          刷新
        </button>
      </aside>
    </header>

    <ModuleNav :items="navItems" />

    <section class="workspace" :aria-label="activeNavItem.name">
      <div class="workspace-header">
        <div>
          <p class="eyebrow">{{ activeNavItem.state }}</p>
          <h2>{{ activeNavItem.name }}</h2>
        </div>
        <p>{{ activeNavItem.description }}</p>
      </div>

      <slot />
    </section>
  </main>
</template>
