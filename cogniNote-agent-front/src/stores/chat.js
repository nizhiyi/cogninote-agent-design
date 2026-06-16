import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  cancelChatAnswer,
  clearChatSessionMessages,
  createChatSession,
  deleteChatSession,
  getChatSession,
  listChatSessions,
  streamChatAnswer,
  updateChatSession
} from '../api/chat-stream'

const DEFAULT_RETRIEVAL_MODE = 'HYBRID'
const DEFAULT_TOP_K = 8
const POST_ERROR_REFRESH_DELAYS = [600, 1800, 4200, 9000, 18000, 36000]
const MAX_PENDING_REFERENCES = 5
const MAX_REFERENCE_SNIPPET_CHARS = 1200
const MAX_REFERENCE_TOTAL_CHARS = 4000

let localIdSeed = 0

function nextId(prefix) {
  localIdSeed += 1
  return `${prefix}-${Date.now()}-${localIdSeed}`
}

function normalizeKnowledgeBaseFlag(value) {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    return normalized !== '' && normalized !== 'false' && normalized !== '0' && normalized !== 'off'
  }
  return value !== false
}

function normalizeTopK(value) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_TOP_K
  }
  return Math.min(50, Math.max(1, Math.trunc(parsed)))
}

function normalizeRole(role) {
  const value = String(role || '').toUpperCase()
  if (value === 'USER') {
    return 'user'
  }
  if (value === 'ASSISTANT') {
    return 'assistant'
  }
  return value.toLowerCase() || 'assistant'
}

function normalizeStatus(status, role) {
  if (role === 'user') {
    return 'done'
  }
  return String(status || 'done').toLowerCase()
}

function normalizeMessage(message, fallbackRole = 'assistant') {
  const role = normalizeRole(message?.role || fallbackRole)
  return {
    id: message?.id || nextId(role),
    role,
    content: message?.content || '',
    status: normalizeStatus(message?.status, role),
    sources: message?.sources || [],
    references: normalizeReferences(message?.references),
    retrievalMode: message?.retrievalMode || '',
    conversationId: message?.conversationId || '',
    requestId: message?.requestId || '',
    createdAt: message?.createdAt || Date.now()
  }
}

function normalizeReferences(references) {
  if (!Array.isArray(references)) {
    return []
  }
  const seen = new Set()
  const normalized = []
  let totalChars = 0
  for (const reference of references) {
    if (!reference || normalized.length >= MAX_PENDING_REFERENCES) {
      continue
    }
    const messageId = normalizeText(reference.messageId)
    let snippet = truncateText(normalizeText(reference.snippet), MAX_REFERENCE_SNIPPET_CHARS)
    if (!messageId || !snippet) {
      continue
    }
    const remaining = MAX_REFERENCE_TOTAL_CHARS - totalChars
    if (remaining <= 0) {
      break
    }
    snippet = truncateText(snippet, remaining)
    const dedupeKey = `${messageId}\n${snippet}`
    if (seen.has(dedupeKey)) {
      continue
    }
    seen.add(dedupeKey)
    normalized.push({
      id: normalizeText(reference.id) || nextId('reference'),
      messageId,
      snippet
    })
    totalChars += snippet.length
  }
  return normalized
}

function normalizeText(value) {
  return String(value || '').replace(/\s+/g, ' ').trim()
}

function truncateText(value, maxLength) {
  return value.length <= maxLength ? value : value.slice(0, maxLength)
}

function normalizeContextUsage(usage) {
  if (!usage) {
    return null
  }
  const contextWindowTokens = normalizeNonNegativeInteger(usage.contextWindowTokens)
  const usedTokens = normalizeNonNegativeInteger(usage.usedTokens)
  const usageRatio = normalizeRatio(
    usage.usageRatio,
    contextWindowTokens > 0 ? usedTokens / contextWindowTokens : 0
  )
  return {
    contextWindowTokens,
    usedTokens,
    availableTokens: normalizeNonNegativeInteger(usage.availableTokens),
    usageRatio,
    compressed: Boolean(usage.compressed),
    summaryTokens: normalizeNonNegativeInteger(usage.summaryTokens),
    recentMessageTokens: normalizeNonNegativeInteger(usage.recentMessageTokens),
    recentMessageCount: normalizeNonNegativeInteger(usage.recentMessageCount),
    totalMessageCount: normalizeNonNegativeInteger(usage.totalMessageCount),
    summaryMessageSequence: normalizeNonNegativeInteger(usage.summaryMessageSequence),
    estimationMethod: String(usage.estimationMethod || '')
  }
}

function normalizeNonNegativeInteger(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0
}

function normalizeRatio(value, fallback = 0) {
  const parsed = Number(value)
  const ratio = Number.isFinite(parsed) ? parsed : fallback
  return Math.min(1, Math.max(0, ratio))
}

function normalizeSession(session) {
  return {
    id: session?.id || nextId('session'),
    title: session?.title || '新对话',
    summary: session?.summary || '',
    createdAt: session?.createdAt || Date.now(),
    updatedAt: session?.updatedAt || Date.now(),
    useKnowledgeBase: normalizeKnowledgeBaseFlag(session?.useKnowledgeBase),
    mode: session?.mode || DEFAULT_RETRIEVAL_MODE,
    topK: normalizeTopK(session?.topK),
    messageCount: Number(session?.messageCount || session?.messages?.length || 0),
    contextUsage: normalizeContextUsage(session?.contextUsage),
    messages: (session?.messages || []).map((message) => normalizeMessage(message))
  }
}

function createLocalMessage(role, content = '') {
  return normalizeMessage({
    id: nextId(role),
    role,
    content,
    status: role === 'assistant' ? 'streaming' : 'done',
    createdAt: Date.now()
  }, role)
}

function delay(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

/**
 * 对话页的会话、草稿和流式回复状态。
 *
 * Store 先用本地 optimistic 消息保持界面即时响应，再用后端 SSE/meta/done 快照校正真实状态。
 */
export const useChatStore = defineStore('chat', () => {
  const sessions = ref([])
  const activeSessionId = ref('')
  const draft = ref('')
  const useKnowledgeBaseValue = ref(true)
  const modeValue = ref(DEFAULT_RETRIEVAL_MODE)
  const topKValue = ref(DEFAULT_TOP_K)
  const isLoadingSessions = ref(false)
  const isLoadingActiveSession = ref(false)
  const isSubmittingChat = ref(false)
  const isStreaming = ref(false)
  const error = ref('')
  const abortController = ref(null)
  const streamingContext = ref(null)
  const pendingReferences = ref([])
  const sessionScrollPositions = ref({})
  const pendingSessionOptionPayloads = new Map()
  const savingSessionOptionIds = new Set()

  const activeSession = computed(() =>
    sessions.value.find((session) => session.id === activeSessionId.value) || null
  )
  const activeMessages = computed(() => activeSession.value?.messages || [])
  const activeContextUsage = computed(() => activeSession.value?.contextUsage || null)
  const hasMessages = computed(() => activeMessages.value.length > 0)
  const hasPendingReferences = computed(() => pendingReferences.value.length > 0)
  const canSend = computed(() => draft.value.trim().length > 0 && !isStreaming.value && !isSubmittingChat.value)
  const useKnowledgeBase = computed({
    get: () => normalizeKnowledgeBaseFlag(useKnowledgeBaseValue.value),
    set: (value) => {
      useKnowledgeBaseValue.value = normalizeKnowledgeBaseFlag(value)
      syncSessionOptions()
      persistActiveSessionOptions()
    }
  })
  const mode = computed({
    get: () => modeValue.value || DEFAULT_RETRIEVAL_MODE,
    set: (value) => {
      modeValue.value = value || DEFAULT_RETRIEVAL_MODE
      syncSessionOptions()
      persistActiveSessionOptions()
    }
  })
  const topK = computed({
    get: () => normalizeTopK(topKValue.value),
    set: (value) => {
      topKValue.value = normalizeTopK(value)
      syncSessionOptions()
      persistActiveSessionOptions()
    }
  })
  const knowledgeDisabledHint = computed(() => '')

  async function initializeSessions() {
    isLoadingSessions.value = true
    error.value = ''
    try {
      const response = await listChatSessions()
      sessions.value = (response || []).map(normalizeSession)
      // 启动时只进入空白草稿页，避免“打开应用”被误写成一条真实会话。
      activeSessionId.value = ''
    } catch (err) {
      error.value = `读取会话失败：${err.message}`
    } finally {
      isLoadingSessions.value = false
    }
  }

  async function startNewSession() {
    if (isStreaming.value || isSubmittingChat.value) {
      return
    }
    error.value = ''
    // “新建”只是进入空白草稿页；真实会话要等用户发送第一条消息时再创建。
    activeSessionId.value = ''
    isLoadingActiveSession.value = false
    draft.value = ''
    clearPendingReferences()
  }

  async function selectSession(sessionId, options = {}) {
    if ((isStreaming.value || isSubmittingChat.value) && !options.force) {
      return
    }
    if (!sessionId) {
      return
    }
    activeSessionId.value = sessionId
    isLoadingActiveSession.value = true
    error.value = ''
    clearPendingReferences()
    try {
      const detail = normalizeSession(await getChatSession(sessionId))
      upsertSession(detail)
      applySessionOptions(detail)
    } catch (err) {
      error.value = `读取会话详情失败：${err.message}`
    } finally {
      isLoadingActiveSession.value = false
    }
  }

  async function renameSession(sessionId, title) {
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) {
      return
    }
    const nextTitle = String(title || '').trim()
    if (!nextTitle || nextTitle === session.title) {
      return
    }
    try {
      const updated = normalizeSession(await updateChatSession(sessionId, { title: nextTitle }))
      upsertSession(updated)
    } catch (err) {
      error.value = `重命名会话失败：${err.message}`
    }
  }

  /**
   * 删除指定聊天会话。
   *
   * 删除当前会话时回到空白草稿页，同时移除滚动锚点，避免新会话继承旧阅读位置。
   */
  async function removeSession(sessionId) {
    if (isStreaming.value || isSubmittingChat.value) {
      return
    }
    try {
      await deleteChatSession(sessionId)
      forgetSessionScrollPosition(sessionId)
      const wasActiveSession = activeSessionId.value === sessionId
      sessions.value = sessions.value.filter((item) => item.id !== sessionId)
      if (wasActiveSession) {
        activeSessionId.value = ''
        isLoadingActiveSession.value = false
        draft.value = ''
        clearPendingReferences()
      }
    } catch (err) {
      error.value = `删除会话失败：${err.message}`
    }
  }

  /**
   * 清空当前会话的消息。
   *
   * 只清消息列表，保留会话本身和检索配置，避免用户需要重新选择知识库模式。
   */
  async function clearActiveMessages() {
    if (!activeSession.value || isStreaming.value) {
      return
    }
    try {
      const updated = normalizeSession(await clearChatSessionMessages(activeSession.value.id))
      upsertSession(updated)
      applySessionOptions(updated)
      clearPendingReferences()
    } catch (err) {
      error.value = `清空会话失败：${err.message}`
    }
  }

  async function streamChat() {
    const trimmedQuestion = draft.value.trim()
    if (!trimmedQuestion) {
      error.value = '请输入问题'
      return
    }
    if (isStreaming.value || isSubmittingChat.value) {
      return
    }

    isSubmittingChat.value = true
    const session = activeSession.value || await createSessionForFirstMessage()
    if (!session) {
      isSubmittingChat.value = false
      return
    }
    syncSessionOptions(session)
    const referencesForMessage = normalizeReferences(pendingReferences.value)
    const userMessage = createLocalMessage('user', trimmedQuestion)
    userMessage.references = referencesForMessage
    appendMessage(session, userMessage)
    clearPendingReferences()
    const assistantMessage = createLocalMessage('assistant')
    const requestId = nextId('request')
    assistantMessage.requestId = requestId
    // 先插入本地 assistant 占位，delta 事件只需要追加内容，不必反复重排消息列表。
    appendMessage(session, assistantMessage)
    updateSessionTitle(session, trimmedQuestion)

    draft.value = ''
    error.value = ''
    isSubmittingChat.value = false
    isStreaming.value = true
    abortController.value = new AbortController()
    streamingContext.value = {
      sessionId: session.id,
      messageId: assistantMessage.id,
      requestId,
      cancelPromise: null
    }

    try {
      await updateChatSession(session.id, sessionPayload(session))
      // 这里建立对话 SSE 流，后端事件会持续驱动助手消息更新。
      await streamChatAnswer(
        {
          conversationId: session.id,
          question: trimmedQuestion,
          mode: mode.value,
          topK: Number(topK.value),
          useKnowledgeBase: useKnowledgeBase.value,
          requestId,
          references: referencesForMessage
        },
        {
          signal: abortController.value.signal,
          onEvent: handleEvent
        }
      )
      updateAssistantMessage((message) => {
        if (message.status !== 'error') {
          message.status = 'done'
        }
      })
      await refreshActiveSession()
      await refreshSessionList()
    } catch (err) {
      if (err.name === 'AbortError') {
        await streamingContext.value?.cancelPromise?.catch(() => {})
        updateAssistantMessage((message) => {
          message.status = message.content ? 'stopped' : 'error'
          if (!message.content) {
            message.content = '已停止生成。'
          }
        })
        await refreshActiveSession()
        await refreshSessionList()
      } else {
        const failedSessionId = streamingContext.value?.sessionId || session.id
        const failedRequestId = streamingContext.value?.requestId || requestId
        markAssistantError(err.message || '模型返回错误')
        // 服务端可能已经落库错误/截断状态，延迟刷新可覆盖本地临时错误文案。
        void refreshSessionAfterStreamError(failedSessionId, failedRequestId)
      }
    } finally {
      isSubmittingChat.value = false
      isStreaming.value = false
      abortController.value = null
      streamingContext.value = null
    }
  }

  function stopChat() {
    const requestId = streamingContext.value?.requestId
    if (requestId) {
      // 后端 cancel 停模型，AbortController 只关闭当前浏览器连接，两边都要触发。
      streamingContext.value.cancelPromise = cancelChatAnswer(requestId).catch(() => {})
    }
    abortController.value?.abort()
  }

  /**
   * 处理后端 SSE 事件。
   * <p>事件处理函数只保留必要副作用，复杂状态交给 Store 维护。</p>
   */
  function handleEvent(eventName, payload) {
    if (eventName === 'meta') {
      updateAssistantMessage((message) => {
        // 后端可能返回正式 conversationId/requestId，用它替换本地临时 id 以便后续取消和刷新。
        message.requestId = payload.requestId || message.requestId
        message.conversationId = payload.conversationId || activeSessionId.value
        message.retrievalMode = payload.retrievalMode || ''
        message.sources = payload.sources || []
        if (payload.requestId && streamingContext.value) {
          streamingContext.value.requestId = payload.requestId
        }
      })
      updateSessionContextUsage(payload.conversationId || activeSessionId.value, payload.contextUsage)
      return
    }

    if (eventName === 'delta') {
      updateAssistantMessage((message) => {
        message.content += payload.text || ''
      })
      return
    }

    if (eventName === 'error') {
      const messageText = payload.message || '模型返回错误'
      markAssistantError(messageText)
      return
    }

    if (eventName === 'done') {
      updateSessionContextUsage(streamingContext.value?.sessionId || activeSessionId.value, payload.contextUsage)
    }
  }

  function markAssistantError(messageText) {
    error.value = `对话失败：${messageText}`
    updateAssistantMessage((message) => {
      message.status = 'error'
      if (message.content) {
        message.content = appendErrorNotice(message.content, messageText)
        return
      }
      message.content = messageText
    })
  }

  function appendErrorNotice(content, messageText) {
    if (content.includes(messageText)) {
      return content
    }
    return `${content}\n\n> ${messageText}`
  }

  function askAboutSource(source) {
    draft.value = `请解释 ${source.fileName} 中和这段内容相关的要点。`
  }

  function addPendingReference(reference) {
    pendingReferences.value = normalizeReferences([...pendingReferences.value, reference])
  }

  function clearPendingReferences() {
    pendingReferences.value = []
  }

  /**
   * 保存会话滚动位置。
   *
   * 锚点数据只保存在前端内存中，用于切换会话或流式追加内容后恢复阅读位置。
   */
  function saveSessionScrollPosition(sessionId, position) {
    if (!sessionId || !position) {
      return
    }
    sessionScrollPositions.value = {
      ...sessionScrollPositions.value,
      [sessionId]: {
        scrollTop: Math.max(0, Number(position.scrollTop) || 0),
        scrollHeight: Math.max(0, Number(position.scrollHeight) || 0),
        clientHeight: Math.max(0, Number(position.clientHeight) || 0),
        distanceFromBottom: Math.max(0, Number(position.distanceFromBottom) || 0),
        anchorMessageId: String(position.anchorMessageId || ''),
        anchorMessageIndex: Math.max(0, Number(position.anchorMessageIndex) || 0),
        anchorOffsetTop: Number(position.anchorOffsetTop) || 0,
        anchorProgress: Math.min(1, Math.max(0, Number(position.anchorProgress) || 0)),
        anchorViewportRatio: Math.min(1, Math.max(0, Number(position.anchorViewportRatio) || 0))
      }
    }
  }

  function getSessionScrollPosition(sessionId) {
    return sessionScrollPositions.value[sessionId] || null
  }

  /**
   * 维护聊天滚动锚点。
   * <p>长对话切换、流式输出和 DOM 高度变化时都依赖该逻辑恢复阅读位置。</p>
   */
  function forgetSessionScrollPosition(sessionId) {
    if (!sessionId || !sessionScrollPositions.value[sessionId]) {
      return
    }
    const nextPositions = { ...sessionScrollPositions.value }
    delete nextPositions[sessionId]
    sessionScrollPositions.value = nextPositions
  }

  function setUseKnowledgeBase(value) {
    useKnowledgeBase.value = value
  }

  function setMode(value) {
    mode.value = value
  }

  function setTopK(value) {
    topK.value = value
  }

  async function refreshActiveSession() {
    if (!activeSessionId.value) {
      return
    }
    await refreshSessionById(activeSessionId.value)
  }

  async function refreshSessionById(sessionId, options = {}) {
    if (!sessionId) {
      return false
    }
    const detail = normalizeSession(await getChatSession(sessionId))
    if (options.requiredAssistantRequestId && !hasAssistantMessage(detail, options.requiredAssistantRequestId)) {
      return false
    }
    upsertSession(detail)
    if (activeSessionId.value === sessionId) {
      applySessionOptions(detail)
    }
    return true
  }

  /**
   * 流式错误后延迟刷新会话详情。
   *
   * SSE 连接失败时后端可能仍在落库错误或截断状态，按退避等待带 requestId 的助手消息出现。
   */
  async function refreshSessionAfterStreamError(sessionId, requestId) {
    if (!sessionId || !requestId) {
      return
    }
    for (const waitMs of POST_ERROR_REFRESH_DELAYS) {
      await delay(waitMs)
      if (isStreaming.value) {
        continue
      }
      try {
        const refreshed = await refreshSessionById(sessionId, {
          requiredAssistantRequestId: requestId
        })
        if (refreshed) {
          await refreshSessionList().catch(() => {})
          return
        }
      } catch {
        // 错误态气泡已经展示给用户；后台同步失败不应再覆盖当前可见错误。
      }
    }
  }

  function hasAssistantMessage(session, requestId) {
    return session.messages.some((message) =>
      message.role === 'assistant' && message.requestId === requestId
    )
  }

  /**
   * 刷新会话摘要列表。
   *
   * 摘要接口不带完整消息；刷新侧栏时保留已加载 messages，避免当前对话内容被空列表覆盖。
   */
  async function refreshSessionList() {
    const response = await listChatSessions()
    const summaries = (response || []).map(normalizeSession)
    sessions.value = summaries.map((summary) => {
      const existing = sessions.value.find((item) => item.id === summary.id)
      return existing ? { ...summary, messages: existing.messages } : summary
    })
  }

  function appendMessage(session, message) {
    session.messages.push(message)
    session.messageCount = session.messages.length
    session.updatedAt = Date.now()
  }

  /**
   * 根据首条问题更新会话标题。
   *
   * 仅在默认标题上生成本地预览，用户重命名后的标题不能被后续提问覆盖。
   */
  function updateSessionTitle(session, question) {
    if (session.title !== '新对话') {
      return
    }
    session.title = question.length > 18 ? `${question.slice(0, 18)}...` : question
  }

  function syncSessionOptions(session = activeSession.value) {
    if (!session) {
      return
    }
    session.useKnowledgeBase = useKnowledgeBase.value
    session.mode = mode.value
    session.topK = topK.value
    session.updatedAt = Date.now()
  }

  /**
   * 为首条消息创建真实会话。
   * <p>空白草稿页不落库，只有用户真正发送消息时才调用后端创建会话。</p>
   */
  async function createSessionForFirstMessage() {
    try {
      const created = normalizeSession(await createChatSession(defaultSessionPayload()))
      upsertSession(created)
      activeSessionId.value = created.id
      applySessionOptions(created)
      return created
    } catch (err) {
      error.value = `新建会话失败：${err.message}`
      return null
    }
  }

  /**
   * 持久化当前会话的检索选项。
   * <p>知识库开关属于会话级状态，用户切换后必须立即写库；否则刷新页面会被旧快照覆盖。</p>
   */
  function persistActiveSessionOptions(session = activeSession.value) {
    if (!session?.id) {
      return
    }
    pendingSessionOptionPayloads.set(session.id, sessionOptionsPayload(session))
    void flushSessionOptions(session.id)
  }

  /**
   * 串行保存指定会话的检索选项。
   * <p>连续点击开关时只允许同一会话一个保存循环运行，保证最终落库的是最后一次本地状态。</p>
   */
  async function flushSessionOptions(sessionId) {
    if (savingSessionOptionIds.has(sessionId)) {
      return
    }
    savingSessionOptionIds.add(sessionId)
    try {
      while (pendingSessionOptionPayloads.has(sessionId)) {
        const payload = pendingSessionOptionPayloads.get(sessionId)
        pendingSessionOptionPayloads.delete(sessionId)
        try {
          const updated = normalizeSession(await updateChatSession(sessionId, payload, { keepalive: true }))
          if (!pendingSessionOptionPayloads.has(sessionId)) {
            applyPersistedSessionOptions(updated)
          }
        } catch (err) {
          error.value = `保存会话设置失败：${err.message}`
        }
      }
    } finally {
      savingSessionOptionIds.delete(sessionId)
      if (pendingSessionOptionPayloads.has(sessionId)) {
        void flushSessionOptions(sessionId)
      }
    }
  }

  /**
   * 应用后端保存后的会话快照。
   * <p>保存期间如果又产生了新的本地修改，旧响应不回写 UI，避免开关视觉状态闪回。</p>
   */
  function applyPersistedSessionOptions(updated) {
    const existing = sessions.value.find((item) => item.id === updated.id)
    const merged = existing
      ? { ...updated, messages: updated.messages.length ? updated.messages : existing.messages }
      : updated
    upsertSession(merged)
    if (activeSessionId.value === updated.id) {
      applySessionOptions(merged)
    }
  }

  /**
   * 更新当前流式助手消息。
   *
   * 所有 SSE delta 都通过 streamingContext 定位占位消息，避免更新到用户切换后的其他会话。
   */
  function updateAssistantMessage(updater) {
    const context = streamingContext.value
    if (!context) {
      return
    }
    const session = sessions.value.find((item) => item.id === context.sessionId)
    const message = session?.messages.find((item) => item.id === context.messageId)
    if (!message) {
      return
    }
    updater(message)
    session.updatedAt = Date.now()
  }

  /**
   * 更新会话上下文占用快照。
   *
   * 后端在 meta/done 事件里返回 token 估算，前端只保存可展示快照，不自行重新估算。
   */
  function updateSessionContextUsage(sessionId, contextUsage) {
    const normalized = normalizeContextUsage(contextUsage)
    if (!sessionId || !normalized) {
      return
    }
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) {
      return
    }
    session.contextUsage = normalized
    if (normalized.totalMessageCount > 0) {
      session.messageCount = normalized.totalMessageCount
    }
  }

  function upsertSession(session) {
    const index = sessions.value.findIndex((item) => item.id === session.id)
    if (index >= 0) {
      sessions.value[index] = session
    } else {
      sessions.value.unshift(session)
    }
    sessions.value.sort((left, right) => Number(right.updatedAt) - Number(left.updatedAt))
  }

  function applySessionOptions(session) {
    useKnowledgeBaseValue.value = normalizeKnowledgeBaseFlag(session.useKnowledgeBase)
    modeValue.value = session.mode || DEFAULT_RETRIEVAL_MODE
    topKValue.value = normalizeTopK(session.topK)
  }

  function defaultSessionPayload() {
    return {
      useKnowledgeBase: useKnowledgeBase.value,
      mode: mode.value,
      topK: topK.value
    }
  }

  function sessionPayload(session) {
    return {
      title: session.title,
      useKnowledgeBase: session.useKnowledgeBase,
      mode: session.mode,
      topK: session.topK
    }
  }

  /**
   * 构造会话检索选项更新载荷。
   * <p>这里只写会话级检索配置，不携带标题，避免和重命名、首问改标题互相覆盖。</p>
   */
  function sessionOptionsPayload(session) {
    return {
      useKnowledgeBase: session.useKnowledgeBase,
      mode: session.mode,
      topK: session.topK
    }
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    activeMessages,
    activeContextUsage,
    hasMessages,
    hasPendingReferences,
    draft,
    pendingReferences,
    useKnowledgeBase,
    mode,
    topK,
    isLoadingSessions,
    isLoadingActiveSession,
    isSubmittingChat,
    isStreaming,
    error,
    canSend,
    knowledgeDisabledHint,
    getSessionScrollPosition,
    saveSessionScrollPosition,
    forgetSessionScrollPosition,
    initializeSessions,
    setUseKnowledgeBase,
    setMode,
    setTopK,
    startNewSession,
    selectSession,
    renameSession,
    removeSession,
    clearActiveMessages,
    streamChat,
    stopChat,
    askAboutSource,
    addPendingReference,
    clearPendingReferences
  }
})
