<script setup>
// query-contextualizer-settings-panel 负责全局知识库追问补全策略的展示和保存。
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatSettingsStore } from '../stores/chat-settings'

const chatSettingsStore = useChatSettingsStore()

onMounted(() => {
  chatSettingsStore.fetchSettings({ force: true })
})

/**
 * 保存知识库追问补全策略。
 * <p>该配置持久化在后端，全局影响知识库模式下的检索 query 补全，不跟随单个对话模型配置。</p>
 */
async function handleSaveSettings() {
  await chatSettingsStore.saveSettings()
  if (chatSettingsStore.error) {
    ElMessage.error(chatSettingsStore.error)
    return
  }
  ElMessage.success(chatSettingsStore.message || '追问补全策略已保存')
}

/**
 * 选择并保存追问补全模式。
 * <p>三张大按钮就是最终设置入口，点击后立即写入后端 SQLite，避免刷新页面回到旧值。</p>
 */
async function handleSelectMode(mode) {
  if (chatSettingsStore.loading || chatSettingsStore.saving) {
    return
  }
  chatSettingsStore.setQueryContextualizerMode(mode)
  await handleSaveSettings()
}
</script>

<template>
  <section class="settings-panel query-contextualizer-panel" aria-labelledby="query-contextualizer-title">
    <header class="settings-panel__header">
      <p class="eyebrow">知识库</p>
      <h3 id="query-contextualizer-title">追问补全策略</h3>
    </header>

    <article class="query-contextualizer-card">
      <div class="query-contextualizer-card__header">
        <div>
          <h4>选择触发方式</h4>
          <p>
            该功能只影响知识库检索 query；不会修改聊天记录中的用户原文，也不会影响纯模型对话。
          </p>
        </div>
<!--        <el-button-->
<!--          type="primary"-->
<!--          :loading="chatSettingsStore.saving"-->
<!--          :disabled="chatSettingsStore.loading"-->
<!--          @click="handleSaveSettings"-->
<!--        >-->
<!--          保存策略-->
<!--        </el-button>-->
      </div>

      <div class="query-contextualizer-modes" role="group" aria-label="知识库追问补全策略">
        <button
          v-for="option in chatSettingsStore.queryContextualizerModeOptions"
          :key="option.value"
          class="query-contextualizer-mode"
          :class="{ active: chatSettingsStore.queryContextualizerMode === option.value }"
          type="button"
          :aria-pressed="chatSettingsStore.queryContextualizerMode === option.value"
          :disabled="chatSettingsStore.loading || chatSettingsStore.saving"
          @click="handleSelectMode(option.value)"
        >
          <span class="query-contextualizer-mode__top">
            <strong>{{ option.label }}</strong>
            <span class="query-contextualizer-mode__indicator" aria-hidden="true"></span>
          </span>
          <span class="query-contextualizer-mode__description">{{ option.description }}</span>
        </button>
      </div>

      <p class="query-contextualizer-note">
        点击策略后会立即保存到本地数据库；默认“自动”只在省略式追问或原问题检索较弱时调用补全 Agent。
      </p>
      <p v-if="chatSettingsStore.error" class="error-message">{{ chatSettingsStore.error }}</p>
    </article>
  </section>
</template>
