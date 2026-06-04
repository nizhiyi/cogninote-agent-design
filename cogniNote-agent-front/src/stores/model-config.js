import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  activateSettingsModelConfig,
  createSettingsModelConfig,
  deleteSettingsModelConfig,
  fetchModelOptions as requestFetchModelOptions,
  getActiveModelConfigs,
  getModelConfigSettings,
  testModelConfig as requestTestModelConfig,
  updateSettingsModelConfig
} from '../api/model-config-api'
import { useSearchStore } from './search'

const ROLES = {
  CHAT: 'CHAT',
  EMBEDDING: 'EMBEDDING'
}

export const useModelConfigStore = defineStore('modelConfig', () => {
  const providerOptions = [
    {
      value: 'DASHSCOPE',
      label: '阿里百炼 DashScope',
      baseUrl: 'https://dashscope.aliyuncs.com/api/v1',
      displayName: 'DashScope'
    },
    {
      value: 'OPENAI_COMPATIBLE',
      label: 'OpenAI-compatible Completions',
      baseUrl: '',
      displayName: 'OpenAI-compatible Completions'
    }
  ]

  const activeRole = ref(ROLES.CHAT)
  const activeSummary = ref({ chat: null, embedding: null })
  const roleState = ref({
    CHAT: emptyRoleState(ROLES.CHAT),
    EMBEDDING: emptyRoleState(ROLES.EMBEDDING)
  })
  const modelOptionsByRole = ref({ CHAT: [], EMBEDDING: [] })
  const modelsFetchedAtByRole = ref({ CHAT: null, EMBEDDING: null })
  const visibleApiKeyByRole = ref({ CHAT: false, EMBEDDING: false })
  const isFetchingModels = ref(false)
  const isTestingModelConfig = ref(false)
  const message = ref('')
  const form = ref(defaultForm(ROLES.CHAT))

  const activeChatConfig = computed(() => activeSummary.value.chat)
  const activeEmbeddingConfig = computed(() => activeSummary.value.embedding)
  const chatConfigs = computed(() => roleState.value.CHAT.configs)
  const embeddingConfigs = computed(() => roleState.value.EMBEDDING.configs)
  const activeConfigs = computed(() => ({
    chat: activeChatConfig.value,
    embedding: activeEmbeddingConfig.value
  }))
  const modelConfig = computed(() => activeChatConfig.value)
  const currentState = computed(() => roleState.value[activeRole.value])
  const activeList = computed(() => currentState.value.configs)
  const selectedConfig = computed(() => currentState.value.selectedConfig)
  const activeConfigForRole = computed(() => activeRole.value === ROLES.CHAT
    ? activeChatConfig.value
    : activeEmbeddingConfig.value)
  const editingIdByRole = computed(() => ({
    CHAT: roleState.value.CHAT.selectedConfig?.id || null,
    EMBEDDING: roleState.value.EMBEDDING.selectedConfig?.id || null
  }))
  const isEditingExisting = computed(() => Boolean(selectedConfig.value?.id))
  const roleLabel = computed(() => activeRole.value === ROLES.CHAT ? '对话模型' : 'Embedding 模型')
  const isOpenAiCompatible = computed(() => form.value.provider === 'OPENAI_COMPATIBLE')
  const providerLabel = computed(() => {
    return providerOptions.find(option => option.value === form.value.provider)?.label || form.value.provider
  })
  const apiKeyPlaceholder = computed(() => {
    return selectedConfig.value?.apiKeyConfigured
      ? '已保存，留空表示继续使用当前 Key'
      : '请输入 API Key'
  })
  const modelOptions = computed(() => modelOptionsByRole.value[activeRole.value] || [])
  const modelsFetchedAt = computed(() => modelsFetchedAtByRole.value[activeRole.value])
  const chatModelOptions = computed(() => {
    return (modelOptionsByRole.value.CHAT || [])
      .filter(model => model.capability === 'CHAT' || model.capability === 'UNKNOWN')
  })
  const embeddingModelOptions = computed(() => {
    return (modelOptionsByRole.value.EMBEDDING || [])
      .filter(model => model.capability === 'EMBEDDING' || model.capability === 'UNKNOWN')
  })
  const isLoadingModelConfig = computed(() => currentState.value.loading)
  const isSavingModelConfig = computed(() => currentState.value.saving)
  const isActivating = computed(() => currentState.value.activating)
  const isDeleting = computed(() => currentState.value.deleting)
  const error = computed(() => currentState.value.error)

  async function fetchModelConfig() {
    await refreshActiveSummary()
  }

  async function ensureModelConfigLoaded() {
    if (!activeSummary.value.chat || !activeSummary.value.embedding) {
      await refreshActiveSummary()
    }
  }

  async function enterModelSettings() {
    activeRole.value = ROLES.CHAT
    return await loadRoleSettings(ROLES.CHAT, { showMask: true })
  }

  async function initializeEditor() {
    if (!currentState.value.loaded) {
      return await loadRoleSettings(activeRole.value, { showMask: true })
    }
    return roleSnapshot(activeRole.value)
  }

  async function reloadEditor() {
    return await loadRoleSettings(activeRole.value, { showMask: true })
  }

  async function switchRole(role) {
    activeRole.value = role
    form.value = roleState.value[role].form
    message.value = ''
    return await loadRoleSettings(role, { showMask: true })
  }

  function selectActiveConfig(role = activeRole.value) {
    activeRole.value = role
    applySelectedConfig(role, activeConfigFor(role))
  }

  function startCreate(role = activeRole.value) {
    activeRole.value = role
    const state = roleState.value[role]
    state.selectedConfig = null
    replaceRoleForm(role, defaultForm(role))
    state.error = ''
    state.revision += 1
    visibleApiKeyByRole.value[role] = false
    message.value = ''
  }

  function editConfig(config) {
    if (!config) {
      return
    }
    activeRole.value = config.role
    applySelectedConfig(config.role, config)
    roleState.value[config.role].error = ''
    visibleApiKeyByRole.value[config.role] = false
    message.value = ''
  }

  function markFormTouched() {
    // 新 store 不再依赖 touched 状态。保留此方法是为了兼容模板事件绑定。
  }

  async function saveModelConfig(formOverride = null) {
    const role = activeRole.value
    const state = roleState.value[role]
    const searchStore = useSearchStore()
    state.saving = true
    state.error = ''
    message.value = ''

    try {
      const payload = formPayload(role, formOverride)
      const snapshot = state.selectedConfig?.id
        ? await updateSettingsModelConfig(state.selectedConfig.id, payload)
        : await createSettingsModelConfig(payload)
      applySnapshot(snapshot)
      message.value = `${roleLabel.value}配置已保存`
      if (role === ROLES.EMBEDDING) {
        message.value += '。Embedding 维度或模型变化后，请按需重建索引。'
        await searchStore.fetchIndexStatus()
      }
      return snapshot
    } catch (err) {
      state.error = `保存失败：${err.message}`
      return null
    } finally {
      state.saving = false
    }
  }

  async function activateConfig(config) {
    if (!config) {
      return
    }
    const role = config.role
    const state = roleState.value[role]
    const searchStore = useSearchStore()
    state.activating = true
    state.error = ''
    message.value = ''

    try {
      const snapshot = await activateSettingsModelConfig(config.id)
      applySnapshot(snapshot)
      if (role === ROLES.EMBEDDING) {
        await searchStore.fetchIndexStatus()
        message.value = 'Embedding 配置已启用。模型或维度变化后，请按需重建索引。'
      } else {
        message.value = '对话模型配置已启用'
      }
      return snapshot
    } catch (err) {
      state.error = `启用失败：${err.message}`
      return null
    } finally {
      state.activating = false
    }
  }

  async function removeConfig(config) {
    if (!config) {
      return
    }
    const role = config.role
    const state = roleState.value[role]
    state.deleting = true
    state.error = ''
    message.value = ''

    try {
      const snapshot = await deleteSettingsModelConfig(config.id)
      applySnapshot(snapshot)
      message.value = '模型配置已删除'
      return snapshot
    } catch (err) {
      state.error = `删除失败：${err.message}`
      return null
    } finally {
      state.deleting = false
    }
  }

  async function fetchModels(formOverride = null) {
    const role = activeRole.value
    const state = roleState.value[role]
    isFetchingModels.value = true
    state.error = ''
    message.value = ''

    try {
      const result = await requestFetchModelOptions(formPayload(role, formOverride))
      modelOptionsByRole.value[role] = result.models || []
      modelsFetchedAtByRole.value[role] = result.fetchedAt || Date.now()
      autoSelectModel(role)
      message.value = modelOptionsByRole.value[role].length
        ? `已获取 ${modelOptionsByRole.value[role].length} 个模型`
        : '模型列表为空，可继续手动输入模型 ID'
    } catch (err) {
      state.error = `获取模型失败：${err.message}`
    } finally {
      isFetchingModels.value = false
    }
  }

  async function testModelConfig(formOverride = null) {
    const role = activeRole.value
    const state = roleState.value[role]
    isTestingModelConfig.value = true
    state.error = ''
    message.value = ''

    try {
      const result = await requestTestModelConfig(formPayload(role, formOverride))
      message.value = result.message || '模型连接测试成功'
    } catch (err) {
      state.error = `连接测试失败：${err.message}`
    } finally {
      isTestingModelConfig.value = false
    }
  }

  function changeProvider(provider) {
    const option = providerOptions.find(item => item.value === provider)
    if (!option) {
      return
    }
    const role = activeRole.value
    const state = roleState.value[role]
    replaceRoleForm(role, {
      ...form.value,
      provider: option.value,
      displayName: option.displayName,
      baseUrl: option.baseUrl,
      modelName: role === ROLES.CHAT ? 'qwen-plus' : 'text-embedding-v4'
    })
    modelOptionsByRole.value[role] = []
    state.error = ''
    message.value = ''
  }

  function toggleApiKeyVisible(role = activeRole.value) {
    visibleApiKeyByRole.value[role] = !visibleApiKeyByRole.value[role]
  }

  async function copyApiKey(formOverride = null, role = activeRole.value) {
    const apiKey = formOverride
      ? formOverride.apiKey
      : role === activeRole.value
        ? form.value.apiKey
        : roleState.value[role].form.apiKey
    if (!apiKey) {
      roleState.value[role].error = '当前没有可复制的 API Key'
      return
    }
    try {
      await navigator.clipboard.writeText(apiKey)
      message.value = 'API Key 已复制'
      roleState.value[role].error = ''
    } catch (err) {
      roleState.value[role].error = `复制失败：${err.message}`
    }
  }

  async function refreshActiveSummary() {
    const active = await getActiveModelConfigs()
    activeSummary.value = {
      chat: active?.chat || null,
      embedding: active?.embedding || null
    }
  }

  async function loadRoleSettings(role, { showMask = false } = {}) {
    const state = roleState.value[role]
    if (showMask) {
      state.loading = true
    }
    state.error = ''
    message.value = ''

    try {
      const snapshot = await getModelConfigSettings(role)
      applySnapshot(snapshot)
      return snapshot
    } catch (err) {
      state.error = `模型配置读取失败：${err.message}`
      return null
    } finally {
      state.loading = false
    }
  }

  function applySnapshot(snapshot) {
    if (!snapshot) {
      return
    }
    activeSummary.value = {
      chat: snapshot.active?.chat || null,
      embedding: snapshot.active?.embedding || null
    }
    const role = snapshot.role || activeRole.value
    activeRole.value = role
    const state = roleState.value[role]
    state.configs = snapshot.configs || []
    state.selectedConfig = snapshot.selectedConfig || null
    replaceRoleForm(role, snapshot.selectedConfig
      ? formFromConfig(snapshot.selectedConfig)
      : defaultForm(role))
    state.loaded = true
    state.error = ''
    state.revision += 1
  }

  function applySelectedConfig(role, config) {
    const state = roleState.value[role]
    state.selectedConfig = config || null
    replaceRoleForm(role, config ? formFromConfig(config) : defaultForm(role))
    state.revision += 1
  }

  function formPayload(role = activeRole.value, formOverride = null) {
    const current = formOverride || (role === activeRole.value ? form.value : roleState.value[role].form)
    return {
      role,
      provider: current.provider,
      displayName: current.displayName.trim(),
      baseUrl: current.baseUrl.trim(),
      apiKey: current.apiKey,
      modelName: current.modelName.trim(),
      embeddingDimensions: role === ROLES.EMBEDDING ? Number(current.embeddingDimensions) : undefined,
      temperature: role === ROLES.CHAT ? Number(current.temperature) : undefined,
      defaultTopK: role === ROLES.CHAT ? Number(current.defaultTopK) : undefined
    }
  }

  function autoSelectModel(role) {
    if (role !== ROLES.CHAT) {
      return
    }
    const options = chatModelOptions.value
    const state = roleState.value[role]
    if (options.length && !options.some(model => model.id === state.form.modelName)) {
      replaceRoleForm(role, {
        ...state.form,
        modelName: options[0].id
      })
    }
  }

  function activeConfigFor(role) {
    return role === ROLES.CHAT ? activeSummary.value.chat : activeSummary.value.embedding
  }

  function replaceRoleForm(role, nextForm) {
    // 模型设置页右侧表单必须只有一个当前编辑对象。Pinia setup store 中
    // computed 对象再接 v-model 容易出现“列表有数据但表单空”的响应式错位。
    roleState.value[role].form = nextForm
    if (role === activeRole.value) {
      form.value = nextForm
    }
  }

  function roleSnapshot(role) {
    const state = roleState.value[role]
    return {
      active: activeSummary.value,
      role,
      configs: state.configs,
      selectedConfig: state.selectedConfig
    }
  }

  return {
    ROLES,
    activeRole,
    activeSummary,
    roleState,
    chatConfigs,
    embeddingConfigs,
    activeChatConfig,
    activeEmbeddingConfig,
    activeConfigs,
    modelConfig,
    isLoadingModelConfig,
    isSavingModelConfig,
    isTestingModelConfig,
    isFetchingModels,
    isActivating,
    isDeleting,
    modelOptionsByRole,
    modelOptions,
    modelsFetchedAt,
    modelsFetchedAtByRole,
    error,
    message,
    form,
    editingIdByRole,
    visibleApiKeyByRole,
    providerOptions,
    providerLabel,
    isOpenAiCompatible,
    apiKeyPlaceholder,
    activeList,
    selectedConfig,
    activeConfigForRole,
    isEditingExisting,
    roleLabel,
    chatModelOptions,
    embeddingModelOptions,
    fetchModelConfig,
    ensureModelConfigLoaded,
    enterModelSettings,
    initializeEditor,
    reloadEditor,
    selectActiveConfig,
    switchRole,
    startCreate,
    editConfig,
    markFormTouched,
    toggleApiKeyVisible,
    copyApiKey,
    fetchModels,
    saveModelConfig,
    testModelConfig,
    activateConfig,
    removeConfig,
    changeProvider
  }
})

function emptyRoleState(role) {
  return {
    configs: [],
    selectedConfig: null,
    form: defaultForm(role),
    loaded: false,
    loading: false,
    saving: false,
    deleting: false,
    activating: false,
    error: '',
    revision: 0
  }
}

function defaultForm(role) {
  return {
    role,
    provider: 'DASHSCOPE',
    displayName: role === ROLES.CHAT ? 'DashScope Chat' : 'DashScope Embedding',
    baseUrl: 'https://dashscope.aliyuncs.com/api/v1',
    apiKey: '',
    modelName: role === ROLES.CHAT ? 'qwen-plus' : 'text-embedding-v4',
    embeddingDimensions: role === ROLES.EMBEDDING ? 1024 : null,
    temperature: role === ROLES.CHAT ? 0.7 : null,
    defaultTopK: role === ROLES.CHAT ? 8 : null
  }
}

function formFromConfig(config) {
  const role = config.role || ROLES.CHAT
  const defaults = defaultForm(role)
  return {
    role,
    provider: config.provider || defaults.provider,
    displayName: config.displayName || defaults.displayName,
    baseUrl: config.baseUrl || defaults.baseUrl,
    apiKey: config.apiKey || '',
    modelName: config.modelName || defaults.modelName,
    embeddingDimensions: role === ROLES.EMBEDDING
      ? (config.embeddingDimensions ?? defaults.embeddingDimensions)
      : null,
    temperature: role === ROLES.CHAT
      ? (config.temperature ?? defaults.temperature)
      : null,
    defaultTopK: role === ROLES.CHAT
      ? (config.defaultTopK ?? defaults.defaultTopK)
      : null
  }
}
