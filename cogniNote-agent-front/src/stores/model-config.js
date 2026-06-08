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

const FIXED_EMBEDDING_DIMENSIONS = 1024
const DEFAULT_CONTEXT_WINDOW_TOKENS = 128000
const MIN_CONTEXT_WINDOW_TOKENS = 1024
const MAX_CONTEXT_WINDOW_TOKENS = 2000000

/**
 * 定义 模型配置 的 Pinia Store。
 * <p>集中维护响应式状态、派生值和异步动作，组件只消费 Store 暴露的接口。</p>
 */
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
      label: 'OpenAI-compatible',
      baseUrl: '',
      displayName: 'OpenAI-compatible'
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
  const contextWindowPresets = [
    { label: '32K', value: 32000 },
    { label: '64K', value: 64000 },
    { label: '128K', value: DEFAULT_CONTEXT_WINDOW_TOKENS },
    { label: '200K', value: 200000 },
    { label: '1M', value: 1000000 }
  ]

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
  const form = computed(() => currentState.value.form)
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
  const roleLabel = computed(() => activeRole.value === ROLES.CHAT ? '对话模型' : '向量模型')
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

  /**
   * 加载 fetch Model 配置 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
  async function fetchModelConfig() {
    await refreshActiveSummary()
  }

  /**
   * 执行 模型配置 中的 ensure Model 配置 Loaded 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function ensureModelConfigLoaded() {
    if (!activeSummary.value.chat || !activeSummary.value.embedding) {
      await refreshActiveSummary()
    }
  }

  /**
   * 执行 模型配置 中的 enter Model Settings 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function enterModelSettings() {
    activeRole.value = ROLES.CHAT
    return await loadRoleSettings(ROLES.CHAT, { showMask: true })
  }

  /**
   * 执行 模型配置 中的 initialize Editor 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function initializeEditor() {
    if (!currentState.value.loaded) {
      return await loadRoleSettings(activeRole.value, { showMask: true })
    }
    return roleSnapshot(activeRole.value)
  }

  /**
   * 执行 模型配置 中的 reload Editor 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function reloadEditor() {
    return await loadRoleSettings(activeRole.value, { showMask: true })
  }

  /**
   * 执行 模型配置 中的 switch Role 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function switchRole(role) {
    const normalizedRole = normalizeRoleValue(role)
    activeRole.value = normalizedRole
    message.value = ''
    return await loadRoleSettings(normalizedRole, { showMask: true })
  }

  /**
   * 执行 模型配置 中的 select Active 配置 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function selectActiveConfig(role = activeRole.value) {
    activeRole.value = role
    applySelectedConfig(role, activeConfigFor(role))
  }

  /**
   * 创建或启动 start Create 对应的前端流程。
   * <p>该方法通常会同步本地响应式状态和后端快照。</p>
   */
  function startCreate(role = activeRole.value) {
    const normalizedRole = normalizeRoleValue(role)
    activeRole.value = normalizedRole
    const state = roleState.value[normalizedRole]
    state.selectedConfig = null
    replaceEditorForm(normalizedRole, defaultForm(normalizedRole))
    state.error = ''
    state.revision += 1
    visibleApiKeyByRole.value[normalizedRole] = false
    message.value = ''
  }

  /**
   * 执行 模型配置 中的 edit 配置 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function editConfig(config) {
    if (!config) {
      return
    }
    const role = normalizeRoleValue(config.role)
    activeRole.value = role
    applySelectedConfig(role, normalizeConfigForRole(config, role))
    roleState.value[role].error = ''
    visibleApiKeyByRole.value[role] = false
    message.value = ''
  }

  /**
   * 执行 模型配置 中的 mark Form Touched 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function markFormTouched() {
    // 新 store 不再依赖 touched 状态。保留此方法是为了兼容模板事件绑定。
  }

  /**
   * 更新 save Model 配置 对应的状态。
   * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
   */
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

  /**
   * 执行 模型配置 中的 activate 配置 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
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
        message.value = '向量模型配置已启用'
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

  /**
   * 删除或清理 remove 配置 对应的数据。
   * <p>清理时同步处理本地缓存，避免界面保留过期状态。</p>
   */
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

  /**
   * 加载 fetch Models 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
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

  /**
   * 执行 模型配置 中的 test Model 配置 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
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

  /**
   * 执行 模型配置 中的 change Provider 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function changeProvider(provider) {
    const currentForm = form.value
    const normalizedProvider = normalizeProviderValue(provider, currentForm.baseUrl)
    const option = providerOptions.find(item => item.value === normalizedProvider)
    if (!option) {
      return
    }
    const role = activeRole.value
    const state = roleState.value[role]
    const previousProvider = currentForm.provider
    const nextModelName = previousProvider === option.value
      ? currentForm.modelName
      : role === ROLES.CHAT ? 'qwen-plus' : 'text-embedding-v4'
    replaceEditorForm(role, {
      ...currentForm,
      role,
      provider: option.value,
      displayName: option.displayName,
      baseUrl: option.baseUrl,
      modelName: nextModelName,
      embeddingDimensions: role === ROLES.EMBEDDING ? FIXED_EMBEDDING_DIMENSIONS : null,
      contextWindowTokens: role === ROLES.CHAT
        ? normalizeContextWindowTokens(currentForm.contextWindowTokens)
        : null
    })
    modelOptionsByRole.value[role] = []
    state.error = ''
    message.value = ''
  }

  /**
   * 切换 toggle Api Key Visible 状态。
   * <p>状态切换只影响当前组件，不改变后端数据。</p>
   */
  function toggleApiKeyVisible(role = activeRole.value) {
    visibleApiKeyByRole.value[role] = !visibleApiKeyByRole.value[role]
  }

  /**
   * 复制代码块内容。
   * <p>优先使用 Clipboard API，失败时回退到传统 textarea 方案。</p>
   */
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
      // 复制能力依赖浏览器权限，失败时走兼容兜底。
      await navigator.clipboard.writeText(apiKey)
      message.value = 'API Key 已复制'
      roleState.value[role].error = ''
    } catch (err) {
      roleState.value[role].error = `复制失败：${err.message}`
    }
  }

  /**
   * 加载 refresh Active Summary 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
  async function refreshActiveSummary() {
    const active = await getActiveModelConfigs()
    activeSummary.value = {
      chat: normalizeConfigForRole(active?.chat, ROLES.CHAT),
      embedding: normalizeConfigForRole(active?.embedding, ROLES.EMBEDDING)
    }
  }

  /**
   * 加载 load Role Settings 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
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

  /**
   * 更新 apply Snapshot 对应的状态。
   * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
   */
  function applySnapshot(snapshot) {
    if (!snapshot) {
      return
    }
    activeSummary.value = {
      chat: normalizeConfigForRole(snapshot.active?.chat, ROLES.CHAT),
      embedding: normalizeConfigForRole(snapshot.active?.embedding, ROLES.EMBEDDING)
    }
    const role = normalizeRoleValue(snapshot.role || activeRole.value)
    activeRole.value = role
    const state = roleState.value[role]
    state.configs = (snapshot.configs || []).map(config => normalizeConfigForRole(config, role))
    state.selectedConfig = snapshot.selectedConfig ? normalizeConfigForRole(snapshot.selectedConfig, role) : null
    replaceEditorForm(role, snapshot.selectedConfig
      ? formFromConfig(state.selectedConfig)
      : defaultForm(role))
    state.loaded = true
    state.error = ''
    state.revision += 1
  }

  /**
   * 更新 apply Selected 配置 对应的状态。
   * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
   */
  function applySelectedConfig(role, config) {
    const normalizedRole = normalizeRoleValue(role)
    const state = roleState.value[normalizedRole]
    const normalizedConfig = config ? normalizeConfigForRole(config, normalizedRole) : null
    state.selectedConfig = normalizedConfig
    replaceEditorForm(normalizedRole, normalizedConfig ? formFromConfig(normalizedConfig) : defaultForm(normalizedRole))
    state.revision += 1
  }

  /**
   * 执行 模型配置 中的 form Payload 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function formPayload(role = activeRole.value, formOverride = null) {
    const current = formOverride || roleState.value[role].form
    return {
      role,
      provider: normalizeProviderValue(current.provider, current.baseUrl),
      displayName: current.displayName.trim(),
      baseUrl: current.baseUrl.trim(),
      apiKey: current.apiKey,
      modelName: current.modelName.trim(),
      embeddingDimensions: role === ROLES.EMBEDDING ? FIXED_EMBEDDING_DIMENSIONS : undefined,
      temperature: role === ROLES.CHAT ? Number(current.temperature) : undefined,
      defaultTopK: role === ROLES.CHAT ? Number(current.defaultTopK) : undefined,
      contextWindowTokens: role === ROLES.CHAT
        ? normalizeContextWindowTokens(current.contextWindowTokens)
        : undefined
    }
  }

  /**
   * 更新 set Context Window Tokens 对应的状态。
   * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
   */
  function setContextWindowTokens(value, role = activeRole.value) {
    if (role !== ROLES.CHAT) {
      return
    }
    replaceEditorForm(role, {
      ...roleState.value[role].form,
      contextWindowTokens: normalizeContextWindowTokens(value)
    })
  }

  /**
   * 执行 模型配置 中的 auto Select Model 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function autoSelectModel(role) {
    if (role !== ROLES.CHAT) {
      return
    }
    const options = chatModelOptions.value
    const state = roleState.value[role]
    if (options.length && !options.some(model => model.id === state.form.modelName)) {
      replaceEditorForm(role, {
        ...state.form,
        modelName: options[0].id
      })
    }
  }

  /**
   * 执行 模型配置 中的 active 配置 For 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function activeConfigFor(role) {
    return role === ROLES.CHAT ? activeSummary.value.chat : activeSummary.value.embedding
  }

  /**
   * 执行 模型配置 中的 replace Editor Form 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function replaceEditorForm(role, nextForm) {
    // 每个 role 只保留一份编辑表单。组件通过 computed form 读取当前 role，
    // 避免“全局表单”和“role 表单”互相覆盖导致 Provider select 停在旧值。
    const normalizedForm = normalizeFormForRole(nextForm, role)
    roleState.value[role].form = { ...normalizedForm }
  }

  /**
   * 执行 模型配置 中的 role Snapshot 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
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
    contextWindowPresets,
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
    setContextWindowTokens,
    formatContextWindowTokens,
    copyApiKey,
    fetchModels,
    saveModelConfig,
    testModelConfig,
    activateConfig,
    removeConfig,
    changeProvider
  }
})

/**
 * 执行 模型配置 中的 empty Role State 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
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

/**
 * 执行 模型配置 中的 default Form 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
function defaultForm(role) {
  return {
    role,
    provider: 'DASHSCOPE',
    displayName: role === ROLES.CHAT ? 'DashScope Chat' : 'DashScope 向量模型',
    baseUrl: 'https://dashscope.aliyuncs.com/api/v1',
    apiKey: '',
    modelName: role === ROLES.CHAT ? 'qwen-plus' : 'text-embedding-v4',
    embeddingDimensions: role === ROLES.EMBEDDING ? FIXED_EMBEDDING_DIMENSIONS : null,
    temperature: role === ROLES.CHAT ? 0.7 : null,
    defaultTopK: role === ROLES.CHAT ? 8 : null,
    contextWindowTokens: role === ROLES.CHAT ? DEFAULT_CONTEXT_WINDOW_TOKENS : null
  }
}

/**
 * 执行 模型配置 中的 form From 配置 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
function formFromConfig(config) {
  const role = normalizeRoleValue(config.role)
  const defaults = defaultForm(role)
  const provider = normalizeProviderValue(config.provider, config.baseUrl)
  return {
    role,
    provider,
    displayName: config.displayName || defaults.displayName,
    baseUrl: config.baseUrl || defaults.baseUrl,
    apiKey: config.apiKey || '',
    modelName: config.modelName || defaults.modelName,
    embeddingDimensions: role === ROLES.EMBEDDING
      ? FIXED_EMBEDDING_DIMENSIONS
      : null,
    temperature: role === ROLES.CHAT
      ? (config.temperature ?? defaults.temperature)
      : null,
    defaultTopK: role === ROLES.CHAT
      ? (config.defaultTopK ?? defaults.defaultTopK)
      : null,
    contextWindowTokens: role === ROLES.CHAT
      ? normalizeContextWindowTokens(config.contextWindowTokens ?? defaults.contextWindowTokens)
      : null
  }
}

/**
 * 规范化 normalize Role Value 输入。
 * <p>把后端、表单或浏览器传入的异常值收敛为安全范围。</p>
 */
function normalizeRoleValue(role) {
  return String(role || '').trim().toUpperCase() === ROLES.EMBEDDING ? ROLES.EMBEDDING : ROLES.CHAT
}

/**
 * 规范化 normalize 配置 For Role 输入。
 * <p>把后端、表单或浏览器传入的异常值收敛为安全范围。</p>
 */
function normalizeConfigForRole(config, role = normalizeRoleValue(config?.role)) {
  if (!config) {
    return null
  }
  return {
    ...config,
    role,
    provider: normalizeProviderValue(config.provider, config.baseUrl),
    embeddingDimensions: role === ROLES.EMBEDDING ? FIXED_EMBEDDING_DIMENSIONS : null,
    contextWindowTokens: role === ROLES.CHAT
      ? normalizeContextWindowTokens(config.contextWindowTokens)
      : null
  }
}

/**
 * 规范化 normalize Form For Role 输入。
 * <p>把后端、表单或浏览器传入的异常值收敛为安全范围。</p>
 */
function normalizeFormForRole(nextForm, role = normalizeRoleValue(nextForm?.role)) {
  const defaults = defaultForm(role)
  const provider = normalizeProviderValue(nextForm?.provider, nextForm?.baseUrl)
  return {
    role,
    provider,
    displayName: nextForm?.displayName || defaults.displayName,
    baseUrl: nextForm?.baseUrl ?? defaults.baseUrl,
    apiKey: nextForm?.apiKey || '',
    modelName: nextForm?.modelName || defaults.modelName,
    embeddingDimensions: role === ROLES.EMBEDDING ? FIXED_EMBEDDING_DIMENSIONS : null,
    temperature: role === ROLES.CHAT ? (nextForm?.temperature ?? defaults.temperature) : null,
    defaultTopK: role === ROLES.CHAT ? (nextForm?.defaultTopK ?? defaults.defaultTopK) : null,
    contextWindowTokens: role === ROLES.CHAT
      ? normalizeContextWindowTokens(nextForm?.contextWindowTokens ?? defaults.contextWindowTokens)
      : null
  }
}

/**
 * 规范化 normalize Context Window Tokens 输入。
 * <p>把后端、表单或浏览器传入的异常值收敛为安全范围。</p>
 */
function normalizeContextWindowTokens(value) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_CONTEXT_WINDOW_TOKENS
  }
  return Math.min(
    MAX_CONTEXT_WINDOW_TOKENS,
    Math.max(MIN_CONTEXT_WINDOW_TOKENS, Math.trunc(parsed))
  )
}

/**
 * 格式化 format Context Window Tokens 展示文本。
 * <p>统一页面上的数字、时间或语言标签展示口径。</p>
 */
function formatContextWindowTokens(value) {
  const normalized = normalizeContextWindowTokens(value)
  if (normalized >= 1000000) {
    return `${formatCompactNumber(normalized / 1000000)}M`
  }
  if (normalized >= 1000) {
    return `${formatCompactNumber(normalized / 1000)}K`
  }
  return String(normalized)
}

/**
 * 格式化 format Compact Number 展示文本。
 * <p>统一页面上的数字、时间或语言标签展示口径。</p>
 */
function formatCompactNumber(value) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, '')
}

/**
 * 规范化 normalize Provider Value 输入。
 * <p>把后端、表单或浏览器传入的异常值收敛为安全范围。</p>
 */
function normalizeProviderValue(provider, baseUrl = '') {
  const normalized = String(provider || '').trim().toUpperCase()
  if (normalized === 'OPENAI_COMPATIBLE' || normalized === 'OPENAI' || normalized.includes('OPENAI')) {
    return 'OPENAI_COMPATIBLE'
  }
  if (normalized === 'DASHSCOPE' || normalized.includes('DASH')) {
    return 'DASHSCOPE'
  }

  // 旧数据或手动导入数据可能只有自定义 Base URL。非 DashScope 地址按 OpenAI-compatible 渲染，
  // 避免 <select> 因未知 value 回落显示第一个 DashScope 选项。
  const normalizedBaseUrl = String(baseUrl || '').trim().toLowerCase()
  if (normalizedBaseUrl && !normalizedBaseUrl.includes('dashscope.aliyuncs.com')) {
    return 'OPENAI_COMPATIBLE'
  }
  return 'DASHSCOPE'
}
