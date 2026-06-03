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
  <section class="conversation-page">
    <header class="conversation-header">
      <div>
        <p class="eyebrow">对话</p>
        <h2>{{ chatStore.activeSession?.title || '新对话' }}</h2>
      </div>
      <div class="conversation-meta">
        <span>{{ chatStore.useKnowledgeBase ? '知识库已启用' : '纯对话待接入' }}</span>
        <span>{{ chatStore.mode }}</span>
      </div>
    </header>

    <section class="message-stream" aria-live="polite">
      <div v-if="!chatStore.hasMessages" class="empty-chat">
        <p class="eyebrow">开始一次检索增强对话</p>
        <h3>导入资料后，直接问它。</h3>
        <p>第七阶段先完成对话式前端。左侧会话是临时状态；跨重启聊天记忆会在第八阶段用 SQLite 接上。</p>
      </div>

      <article
        v-for="message in chatStore.activeMessages"
        :key="message.id"
        class="message-bubble"
        :class="[`message-bubble--${message.role}`, `message-bubble--${message.status}`]"
      >
        <div class="message-label">
          <span>{{ message.role === 'user' ? '你' : 'CogniNote' }}</span>
          <em v-if="message.retrievalMode">{{ message.retrievalMode }}</em>
          <em v-else-if="message.status === 'streaming'">生成中</em>
          <em v-else-if="message.status === 'stopped'">已停止</em>
        </div>
        <p class="message-content">{{ message.content || '正在等待模型返回...' }}</p>
        <p v-if="message.conversationId" class="path-text">conversationId: {{ message.conversationId }}</p>
        <SourceList
          v-if="message.sources?.length"
          :sources="message.sources"
          compact
          @ask-source="chatStore.askAboutSource"
        />
      </article>
    </section>

    <form class="composer-bar" @submit.prevent="chatStore.streamChat">
      <label class="knowledge-toggle">
        <input v-model="chatStore.useKnowledgeBase" type="checkbox" />
        <span>使用知识库</span>
      </label>

      <div class="composer-controls">
        <SegmentedControl v-model="chatStore.mode" :options="SEARCH_MODES" label="RAG 检索模式" />
        <label class="field field--small">
          <span>Top K</span>
          <input v-model="chatStore.topK" type="number" min="1" max="50" />
        </label>
      </div>

      <textarea
        v-model="chatStore.draft"
        rows="3"
        :placeholder="chatStore.useKnowledgeBase ? '向知识库提问...' : '纯对话将在第八阶段启用'"
        :disabled="chatStore.isStreaming"
      ></textarea>

      <div class="composer-actions">
        <p v-if="chatStore.error" class="error-message">{{ chatStore.error }}</p>
        <p v-else-if="chatStore.knowledgeDisabledHint" class="hint-message">{{ chatStore.knowledgeDisabledHint }}</p>
        <p v-else-if="!modelConfigStore.modelConfig?.apiKeyConfigured" class="hint-message">
          尚未保存模型 API Key。请先到设置中的模型配置保存后再对话。
        </p>
        <div class="button-row">
          <button class="secondary-button" type="button" :disabled="!chatStore.isStreaming" @click="chatStore.stopChat">
            停止
          </button>
          <button class="primary-button" type="submit" :disabled="!chatStore.canSend">
            {{ chatStore.isStreaming ? '回答中...' : '发送' }}
          </button>
        </div>
      </div>
    </form>
  </section>
</template>
