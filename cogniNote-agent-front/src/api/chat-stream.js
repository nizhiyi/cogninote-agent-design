import { jsonOptions } from './http-client'

export async function streamChatAnswer(payload, { signal, onEvent }) {
  const response = await fetch('/api/chat/stream', {
    ...jsonOptions('POST', payload),
    signal
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || body?.code || `HTTP ${response.status}`)
  }

  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  await readSseStream(response.body, onEvent)
}

async function readSseStream(body, onEvent) {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const dispatchEvent = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }

    onEvent(eventName, parsePayload(dataLines.join('\n')))
    eventName = 'message'
    dataLines = []
  }

  const handleLine = (line) => {
    if (line === '') {
      dispatchEvent()
      return
    }
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }

    // 对话接口需要 POST JSON body，不能使用只支持 GET 的 EventSource。
    // 这里手动按 SSE 空行分帧，确保半包数据不会提前触发 UI 更新。
    buffer += decoder.decode(value, { stream: true })
    let newlineIndex = buffer.indexOf('\n')
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).replace(/\r$/, '')
      buffer = buffer.slice(newlineIndex + 1)
      handleLine(line)
      newlineIndex = buffer.indexOf('\n')
    }
  }

  buffer += decoder.decode()
  if (buffer.length > 0) {
    handleLine(buffer.replace(/\r$/, ''))
  }
  dispatchEvent()
}

function parsePayload(rawData) {
  try {
    return JSON.parse(rawData)
  } catch {
    return { text: rawData }
  }
}
