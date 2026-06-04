import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChatAnswer } from '../api/chat-stream'

const DEFAULT_RETRIEVAL_MODE = 'HYBRID'
const DEFAULT_TOP_K = 8

let localIdSeed = 0

function nextId(prefix) {
  localIdSeed += 1
  return `${prefix}-${Date.now()}-${localIdSeed}`
}

function createMessage(role, content = '') {
  return {
    id: nextId(role),
    role,
    content,
    status: role === 'assistant' ? 'streaming' : 'done',
    sources: [],
    retrievalMode: '',
    conversationId: '',
    createdAt: Date.now()
  }
}

function createSession(title = '新对话') {
  return {
    id: nextId('session'),
    title,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    useKnowledgeBase: true,
    mode: DEFAULT_RETRIEVAL_MODE,
    topK: DEFAULT_TOP_K,
    messages: []
  }
}

function normalizeKnowledgeBaseFlag(value) {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    return normalized !== '' && normalized !== 'false' && normalized !== '0' && normalized !== 'off'
  }
  return Boolean(value)
}

function normalizeTopK(value) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_TOP_K
  }
  return Math.min(50, Math.max(1, Math.trunc(parsed)))
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref([createSession()])
  const activeSessionId = ref(sessions.value[0].id)
  const draft = ref('')
  const useKnowledgeBaseValue = ref(true)
  const modeValue = ref(DEFAULT_RETRIEVAL_MODE)
  const topKValue = ref(DEFAULT_TOP_K)
  const isStreaming = ref(false)
  const error = ref('')
  const abortController = ref(null)
  const streamingContext = ref(null)

  const activeSession = computed(() =>
    sessions.value.find((session) => session.id === activeSessionId.value) || sessions.value[0]
  )
  const activeMessages = computed(() => activeSession.value?.messages || [])
  const hasMessages = computed(() => activeMessages.value.length > 0)
  const canSend = computed(() => draft.value.trim().length > 0 && !isStreaming.value)
  const useKnowledgeBase = computed({
    get: () => normalizeKnowledgeBaseFlag(useKnowledgeBaseValue.value),
    set: (value) => {
      useKnowledgeBaseValue.value = normalizeKnowledgeBaseFlag(value)
      syncSessionOptions()
      if (useKnowledgeBaseValue.value) {
        error.value = ''
      }
    }
  })
  const mode = computed({
    get: () => modeValue.value || DEFAULT_RETRIEVAL_MODE,
    set: (value) => {
      modeValue.value = value || DEFAULT_RETRIEVAL_MODE
      syncSessionOptions()
    }
  })
  const topK = computed({
    get: () => normalizeTopK(topKValue.value),
    set: (value) => {
      topKValue.value = normalizeTopK(value)
      syncSessionOptions()
    }
  })
  const knowledgeDisabledHint = computed(() =>
    useKnowledgeBase.value ? '' : '纯对话将在第十一阶段接入后端聊天记忆后启用。'
  )

  function setUseKnowledgeBase(value) {
    useKnowledgeBase.value = value
  }

  function setMode(value) {
    mode.value = value
  }

  function setTopK(value) {
    topK.value = value
  }

  function startNewSession() {
    const session = createSession()
    sessions.value.unshift(session)
    selectSession(session.id)
    draft.value = ''
    error.value = ''
  }

  function selectSession(sessionId) {
    if (isStreaming.value) {
      return
    }
    const session = sessions.value.find((item) => item.id === sessionId)
    if (!session) {
      return
    }
    activeSessionId.value = session.id
    useKnowledgeBaseValue.value = normalizeKnowledgeBaseFlag(session.useKnowledgeBase)
    modeValue.value = session.mode || DEFAULT_RETRIEVAL_MODE
    topKValue.value = normalizeTopK(session.topK)
    error.value = ''
  }

  async function streamChat() {
    const trimmedQuestion = draft.value.trim()
    if (!trimmedQuestion) {
      error.value = '请输入问题'
      return
    }
    if (!useKnowledgeBase.value) {
      error.value = knowledgeDisabledHint.value
      return
    }

    const session = activeSession.value
    syncSessionOptions(session)
    appendMessage(session, createMessage('user', trimmedQuestion))
    const assistantMessage = createMessage('assistant')
    appendMessage(session, assistantMessage)
    updateSessionTitle(session, trimmedQuestion)

    draft.value = ''
    error.value = ''
    isStreaming.value = true
    abortController.value = new AbortController()
    streamingContext.value = {
      sessionId: session.id,
      messageId: assistantMessage.id
    }

    try {
      await streamChatAnswer(
        {
          question: trimmedQuestion,
          mode: mode.value,
          topK: Number(topK.value)
        },
        {
          signal: abortController.value.signal,
          onEvent: handleEvent
        }
      )
      updateAssistantMessage((message) => {
        message.status = 'done'
      })
    } catch (err) {
      if (err.name === 'AbortError') {
        updateAssistantMessage((message) => {
          message.status = message.content ? 'stopped' : 'error'
          if (!message.content) {
            message.content = '已停止生成。'
          }
        })
      } else {
        error.value = `对话失败：${err.message}`
        updateAssistantMessage((message) => {
          message.status = 'error'
          message.content = err.message || '模型返回错误'
        })
      }
    } finally {
      isStreaming.value = false
      abortController.value = null
      streamingContext.value = null
    }
  }

  function stopChat() {
    abortController.value?.abort()
  }

  function handleEvent(eventName, payload) {
    // SSE 的 meta/delta/error 在 store 中归档，页面只消费消息状态，
    // 第十一阶段接入 SQLite 会话时可以复用同一套前端消息模型。
    if (eventName === 'meta') {
      updateAssistantMessage((message) => {
        message.conversationId = payload.conversationId || ''
        message.retrievalMode = payload.retrievalMode || ''
        message.sources = payload.sources || []
      })
      return
    }

    if (eventName === 'delta') {
      updateAssistantMessage((message) => {
        message.content += payload.text || ''
      })
      return
    }

    if (eventName === 'error') {
      updateAssistantMessage((message) => {
        message.status = 'error'
        message.content = payload.message || '模型返回错误'
      })
    }
  }

  function askAboutSource(source) {
    draft.value = `请解释 ${source.fileName} 中和这段内容相关的要点。`
  }

  function appendMessage(session, message) {
    session.messages.push(message)
    session.updatedAt = Date.now()
  }

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
    // 输入区设置会频繁开关弹层，直接写回当前临时会话，避免 UI 和会话列表状态分叉。
    session.useKnowledgeBase = useKnowledgeBase.value
    session.mode = mode.value
    session.topK = topK.value
    session.updatedAt = Date.now()
  }

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

  return {
    sessions,
    activeSessionId,
    activeSession,
    activeMessages,
    hasMessages,
    draft,
    useKnowledgeBase,
    mode,
    topK,
    isStreaming,
    error,
    canSend,
    knowledgeDisabledHint,
    setUseKnowledgeBase,
    setMode,
    setTopK,
    startNewSession,
    selectSession,
    streamChat,
    stopChat,
    askAboutSource
  }
})
