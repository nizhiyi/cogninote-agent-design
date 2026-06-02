import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getModelConfig,
  saveModelConfig as requestSaveModelConfig,
  testModelConfig as requestTestModelConfig
} from '../api/model-config-api'
import { useSearchStore } from './search'

export const useModelConfigStore = defineStore('modelConfig', () => {
  const modelConfig = ref(null)
  const isLoadingModelConfig = ref(false)
  const isSavingModelConfig = ref(false)
  const isTestingModelConfig = ref(false)
  const error = ref('')
  const message = ref('')
  const form = ref(defaultForm())

  const apiKeyPlaceholder = computed(() => {
    if (modelConfig.value?.apiKeyConfigured) {
      return '已保存，留空表示继续使用当前 Key'
    }
    return '请输入 DashScope API Key'
  })

  async function fetchModelConfig() {
    isLoadingModelConfig.value = true
    error.value = ''
    message.value = ''

    try {
      const config = await getModelConfig()
      modelConfig.value = config
      form.value = {
        apiKey: '',
        chatModel: config.chatModel,
        embeddingModel: config.embeddingModel,
        embeddingDimensions: config.embeddingDimensions,
        temperature: config.temperature,
        topK: config.topK
      }
    } catch (err) {
      modelConfig.value = null
      error.value = `模型配置读取失败：${err.message}`
    } finally {
      isLoadingModelConfig.value = false
    }
  }

  async function saveModelConfig() {
    const searchStore = useSearchStore()
    isSavingModelConfig.value = true
    error.value = ''
    message.value = ''

    try {
      const saved = await requestSaveModelConfig(payload())
      modelConfig.value = saved
      form.value.apiKey = ''
      message.value = '模型配置已保存'
      await searchStore.fetchIndexStatus()
    } catch (err) {
      error.value = `保存失败：${err.message}`
    } finally {
      isSavingModelConfig.value = false
    }
  }

  async function testModelConfig() {
    isTestingModelConfig.value = true
    error.value = ''
    message.value = ''

    try {
      const result = await requestTestModelConfig(payload())
      message.value = result.message || 'DashScope 连接测试成功'
    } catch (err) {
      error.value = `连接测试失败：${err.message}`
    } finally {
      isTestingModelConfig.value = false
    }
  }

  function payload() {
    return {
      apiKey: form.value.apiKey,
      chatModel: form.value.chatModel.trim(),
      embeddingModel: form.value.embeddingModel.trim(),
      embeddingDimensions: Number(form.value.embeddingDimensions),
      temperature: Number(form.value.temperature),
      topK: Number(form.value.topK)
    }
  }

  return {
    modelConfig,
    isLoadingModelConfig,
    isSavingModelConfig,
    isTestingModelConfig,
    error,
    message,
    form,
    apiKeyPlaceholder,
    fetchModelConfig,
    saveModelConfig,
    testModelConfig
  }
})

function defaultForm() {
  return {
    apiKey: '',
    chatModel: 'qwen-plus',
    embeddingModel: 'text-embedding-v4',
    embeddingDimensions: 1024,
    temperature: 0.7,
    topK: 8
  }
}
