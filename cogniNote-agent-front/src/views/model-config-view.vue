<script setup>
import StatGrid from '../components/stat-grid.vue'
import { useModelConfigStore } from '../stores/model-config'
import { formatTime } from '../utils/formatters'

const modelConfigStore = useModelConfigStore()
</script>

<template>
  <form class="model-form" @submit.prevent="modelConfigStore.saveModelConfig">
    <label class="field">
      <span>配置名称</span>
      <input v-model="modelConfigStore.form.displayName" type="text" autocomplete="off" />
    </label>

    <label class="field">
      <span>Provider</span>
      <input v-model="modelConfigStore.form.provider" type="text" autocomplete="off" readonly />
    </label>

    <label class="field field--full">
      <span>Base URL</span>
      <input
        v-model="modelConfigStore.form.baseUrl"
        type="url"
        autocomplete="off"
        placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
      />
    </label>

    <label class="field field--full">
      <span>DashScope API Key</span>
      <input
        v-model="modelConfigStore.form.apiKey"
        type="password"
        :placeholder="modelConfigStore.apiKeyPlaceholder"
        autocomplete="off"
      />
    </label>

    <label class="field">
      <span>Chat 模型</span>
      <select v-model="modelConfigStore.form.chatModel">
        <option
          v-for="model in modelConfigStore.chatModelOptions"
          :key="model.id"
          :value="model.id"
        >
          {{ model.name || model.id }}
        </option>
      </select>
      <input v-model="modelConfigStore.form.chatModel" type="text" autocomplete="off" />
    </label>

    <label class="field">
      <span>Embedding 模型</span>
      <select v-model="modelConfigStore.form.embeddingModel">
        <option
          v-for="model in modelConfigStore.embeddingModelOptions"
          :key="model.id"
          :value="model.id"
        >
          {{ model.name || model.id }}
        </option>
      </select>
      <input v-model="modelConfigStore.form.embeddingModel" type="text" autocomplete="off" />
    </label>

    <label class="field">
      <span>Embedding 维度</span>
      <input v-model="modelConfigStore.form.embeddingDimensions" type="number" min="1" max="8192" />
    </label>

    <label class="field">
      <span>Temperature</span>
      <input v-model="modelConfigStore.form.temperature" type="number" min="0" max="2" step="0.1" />
    </label>

    <label class="field">
      <span>默认 Top K</span>
      <input v-model="modelConfigStore.form.topK" type="number" min="1" max="50" />
    </label>

    <div class="model-form__actions">
      <button
        class="secondary-button"
        type="button"
        :disabled="modelConfigStore.isFetchingModels"
        @click="modelConfigStore.fetchModels"
      >
        {{ modelConfigStore.isFetchingModels ? '获取中...' : '获取模型' }}
      </button>
      <button class="primary-button" type="submit" :disabled="modelConfigStore.isSavingModelConfig">
        {{ modelConfigStore.isSavingModelConfig ? '保存中...' : '保存配置' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        :disabled="modelConfigStore.isTestingModelConfig"
        @click="modelConfigStore.testModelConfig"
      >
        {{ modelConfigStore.isTestingModelConfig ? '测试中...' : '测试连接' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        :disabled="modelConfigStore.isLoadingModelConfig"
        @click="modelConfigStore.fetchModelConfig"
      >
        重新读取
      </button>
    </div>
  </form>

  <p v-if="modelConfigStore.error" class="error-message">{{ modelConfigStore.error }}</p>
  <p v-if="modelConfigStore.message" class="success-message">{{ modelConfigStore.message }}</p>

  <StatGrid
    class="config-summary"
    :columns="6"
    :items="[
      { label: 'Provider', value: modelConfigStore.modelConfig?.provider || 'DASHSCOPE' },
      { label: 'Base URL', value: modelConfigStore.modelConfig?.baseUrl || modelConfigStore.form.baseUrl },
      { label: 'API Key', value: modelConfigStore.modelConfig?.apiKeyConfigured ? '已保存' : '未配置' },
      { label: 'Chat', value: modelConfigStore.modelConfig?.chatModel || modelConfigStore.form.chatModel },
      { label: 'Embedding', value: modelConfigStore.modelConfig?.embeddingModel || modelConfigStore.form.embeddingModel },
      { label: '更新于', value: formatTime(modelConfigStore.modelConfig?.updatedAt) }
    ]"
  />

  <section v-if="modelConfigStore.modelOptions.length" class="model-options-panel">
    <div class="section-title-line">
      <h3>模型列表</h3>
      <span>{{ modelConfigStore.modelOptions.length }} 个模型</span>
    </div>
    <div class="model-options-grid">
      <span
        v-for="model in modelConfigStore.modelOptions"
        :key="model.id"
        class="model-option-chip"
      >
        <strong>{{ model.name || model.id }}</strong>
        <em>{{ model.capability }}</em>
      </span>
    </div>
  </section>

  <p class="warning-message">
    当前阶段 API Key 会以明文保存到本机 SQLite，仅用于开发态闭环；后续交付阶段再接入 Windows 本地加密或凭据管理。
  </p>
</template>
