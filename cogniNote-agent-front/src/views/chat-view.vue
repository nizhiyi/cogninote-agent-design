<script setup>
import SegmentedControl from '../components/segmented-control.vue'
import SourceList from '../components/source-list.vue'
import { useChatStore } from '../stores/chat'
import { useModelConfigStore } from '../stores/model-config'
import { SEARCH_MODES } from '../stores/search'

const chatStore = useChatStore()
const modelConfigStore = useModelConfigStore()
</script>

<template>
  <section class="chat-layout">
    <form class="chat-composer" @submit.prevent="chatStore.streamChat">
      <label class="field">
        <span>问题</span>
        <textarea v-model="chatStore.question" rows="6" placeholder="例如：这个项目如何打包？"></textarea>
      </label>

      <div class="inline-controls">
        <SegmentedControl v-model="chatStore.mode" :options="SEARCH_MODES" label="RAG 检索模式" />

        <label class="field field--small">
          <span>Top K</span>
          <input v-model="chatStore.topK" type="number" min="1" max="50" />
        </label>
      </div>

      <div class="button-row">
        <button class="primary-button" type="submit" :disabled="!chatStore.canSend">
          {{ chatStore.isStreaming ? '回答中...' : '发送问题' }}
        </button>
        <button class="secondary-button" type="button" :disabled="!chatStore.isStreaming" @click="chatStore.stopChat">
          停止
        </button>
      </div>

      <p v-if="chatStore.error" class="error-message">{{ chatStore.error }}</p>
      <p v-if="!modelConfigStore.modelConfig?.apiKeyConfigured" class="hint-message">
        尚未保存 DashScope API Key。请先到“模型配置”页保存后再对话。
      </p>
    </form>

    <article class="answer-panel" aria-live="polite">
      <div class="section-title-line">
        <h3>回答</h3>
        <span>{{ chatStore.retrievalMode || '等待提问' }}</span>
      </div>
      <p v-if="!chatStore.answer && !chatStore.isStreaming" class="panel-message">
        这里会显示模型的流式回答。回答中的 [1]、[2] 对应下方引用来源。
      </p>
      <p v-else-if="!chatStore.answer && chatStore.isStreaming" class="panel-message">正在等待模型返回...</p>
      <div v-else class="answer-text">{{ chatStore.answer }}</div>
      <p v-if="chatStore.conversationId" class="path-text">conversationId: {{ chatStore.conversationId }}</p>
    </article>
  </section>

  <SourceList :sources="chatStore.sources" @ask-source="chatStore.askAboutSource" />
</template>
