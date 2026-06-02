import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { streamChatAnswer } from '../api/chat-stream'

export const useChatStore = defineStore('chat', () => {
  const question = ref('')
  const mode = ref('HYBRID')
  const topK = ref(8)
  const answer = ref('')
  const sources = ref([])
  const retrievalMode = ref('')
  const conversationId = ref('')
  const isStreaming = ref(false)
  const error = ref('')
  const abortController = ref(null)

  const canSend = computed(() => question.value.trim().length > 0 && !isStreaming.value)

  async function streamChat() {
    const trimmedQuestion = question.value.trim()
    if (!trimmedQuestion) {
      error.value = '请输入问题'
      return
    }

    resetResponse()
    isStreaming.value = true
    abortController.value = new AbortController()

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
    } catch (err) {
      if (err.name !== 'AbortError') {
        error.value = `对话失败：${err.message}`
      }
    } finally {
      isStreaming.value = false
      abortController.value = null
    }
  }

  function stopChat() {
    abortController.value?.abort()
    isStreaming.value = false
  }

  function resetResponse() {
    answer.value = ''
    sources.value = []
    retrievalMode.value = ''
    conversationId.value = ''
    error.value = ''
  }

  function handleEvent(eventName, payload) {
    // SSE 的 meta/delta/error 在这里集中归档，视图层只消费最终状态，
    // 避免多个组件同时理解流式协议细节。
    if (eventName === 'meta') {
      conversationId.value = payload.conversationId || ''
      retrievalMode.value = payload.retrievalMode || ''
      sources.value = payload.sources || []
      return
    }

    if (eventName === 'delta') {
      answer.value += payload.text || ''
      return
    }

    if (eventName === 'error') {
      error.value = payload.message || '模型返回错误'
    }
  }

  function askAboutSource(source) {
    question.value = `请解释 ${source.fileName} 中和这段内容相关的要点。`
  }

  return {
    question,
    mode,
    topK,
    answer,
    sources,
    retrievalMode,
    conversationId,
    isStreaming,
    error,
    canSend,
    streamChat,
    stopChat,
    askAboutSource
  }
})
