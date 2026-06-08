# 第 22 阶段计划：对话上下文窗口与 Token 估算优化

## Summary

第 22 阶段把对话上下文预算从固定 `6000` token 升级为“按当前 active Chat 模型配置动态计算”。对话模型新增 `contextWindowTokens`，默认 `128000` tokens，前端展示为 `128K`；用户可以在“设置 -> 模型 -> 对话模型”中设置上下文长度。

本阶段同时改善 token 估算方式：优先使用本地 tokenizer，失败时再回退保守估算；聊天页展示当前会话上下文占用。会话已经压缩时，占用值按“摘要 + 最近原文消息”重新计算，而不是直接信任旧消息落库时的 `token_estimate`。

## Key Changes

### 模型配置

- `model_configs` 新增 `context_window_tokens INTEGER`。
- `ModelConfig`、模型配置请求 DTO、响应 DTO、设置页 settings snapshot 都新增 `contextWindowTokens`。
- Chat 配置默认 `128000`，前端显示为 `128K`。
- Embedding 配置的 `contextWindowTokens` 保持 `null`，不参与聊天上下文预算。
- 校验范围为 `1024` 到 `2000000`，避免过小窗口导致记忆策略失效，也避免用户误填极端值。

### 配置文件调整

- `minimum-recent-messages` 默认保持 `8`，作为最近原文消息兜底。
- `summarize-after-messages` 默认从 `40` 调整为 `200`，避免 128K 上下文下过早压缩。
- `max-history-tokens` 保留为兼容兜底，但不再作为主预算；实际历史预算优先来自 active Chat 配置的 `contextWindowTokens`。

对应配置：

```yaml
app:
  chat:
    memory:
      max-history-tokens: ${COGNINOTE_CHAT_MEMORY_MAX_HISTORY_TOKENS:6000}
      minimum-recent-messages: ${COGNINOTE_CHAT_MEMORY_MINIMUM_RECENT_MESSAGES:8}
      summarize-after-messages: ${COGNINOTE_CHAT_MEMORY_SUMMARIZE_AFTER_MESSAGES:200}
```

## 上下文预算策略

- 总窗口来自 `contextWindowTokens`。
- 系统提示、当前问题、RAG 片段和模型输出需要预留空间，历史消息不能占满总窗口。
- 历史消息最多使用约 `80%` 的可用窗口。
- 超过 token 预算时，优先压缩较早消息，最近至少保留 `minimum-recent-messages=8` 条原文。
- 触发压缩时优先看 token 是否超预算；消息数超过 `summarize-after-messages=200` 只作为兜底条件。
- SQLite 继续保存全量会话历史，摘要只覆盖较早消息范围，不删除原始消息。

## Token 估算

- 新增依赖 `com.knuddels:jtokkit:1.1.0`。
- DashScope/Qwen 默认使用 `o200k_base`。
- OpenAI-compatible 优先按模型名匹配 tokenizer；无法识别时使用 `cl100k_base`。
- 每条 chat message 增加少量 framing overhead，避免低估真实聊天消息开销。
- 旧消息展示和预算选择时动态重算，不完全信任落库时的旧 `token_estimate`。
- 本地 tokenizer 初始化或匹配失败时，回退到更保守的字符估算。

## 聊天上下文展示

新增 `ChatContextUsageResponse`：

```text
contextWindowTokens
usedTokens
availableTokens
usageRatio
compressed
summaryTokens
recentMessageTokens
recentMessageCount
totalMessageCount
summaryMessageSequence
estimationMethod
```

返回位置：

- `ChatSessionResponse.contextUsage`
- SSE `meta.contextUsage`
- SSE `done.contextUsage`

前端聊天页在发送按钮、知识库配置按钮左侧显示圆环进度。默认只展示上下文占用状态，鼠标移入后展示 `used / max`、百分比、是否已压缩、摘要 token、最近原文消息 token、最近消息数和估算方式等细节。

## API 与数据契约

模型配置请求和响应中的 Chat 配置示例：

```json
{
  "role": "CHAT",
  "provider": "OPENAI_COMPATIBLE",
  "displayName": "Local Chat",
  "baseUrl": "http://127.0.0.1:11434/v1",
  "apiKey": "sk-...",
  "modelName": "qwen-plus",
  "temperature": 0.7,
  "defaultTopK": 8,
  "contextWindowTokens": 128000
}
```

聊天会话响应会携带上下文占用：

```json
{
  "id": "conversation-1",
  "messageCount": 12,
  "contextUsage": {
    "contextWindowTokens": 128000,
    "usedTokens": 4200,
    "availableTokens": 123800,
    "usageRatio": 0.0328,
    "compressed": false,
    "summaryTokens": 0,
    "recentMessageTokens": 4200,
    "recentMessageCount": 12,
    "totalMessageCount": 12,
    "summaryMessageSequence": 0,
    "estimationMethod": "jtokkit:o200k_base"
  }
}
```

SSE `meta` 和 `done` 保持同一份占用口径：

```text
event: meta
data: {"requestId":"...","conversationId":"...","retrievalMode":"HYBRID","sources":[],"contextUsage":{...}}

event: done
data: {"usage":null,"contextUsage":{...}}
```

## Test Plan

- 默认 Chat 配置返回 `contextWindowTokens=128000`。
- 保存自定义上下文长度后，设置页和聊天页都能正确回显。
- `minimum-recent-messages=8` 时，压缩后仍保留最近 8 条原文。
- `summarize-after-messages=200` 前，只要 token 未超预算，不因 40 条消息过早压缩。
- 已有摘要的会话按摘要 token 重新计算 `contextUsage`。
- SSE `meta/done` 和 `GET /api/chat/sessions/{id}` 返回一致的上下文占用。

## Assumptions

- `128K` 按 `128000` tokens 处理。
- 本阶段不向模型 API 发送通用上下文长度参数，只用于本地上下文裁剪、压缩和展示。
- `max-history-tokens=6000` 仅作为旧配置兼容兜底，不能覆盖模型配置中的上下文窗口。

## 参考资料

- [OpenAI token 说明](https://help.openai.com/en/articles/4936856-what-are-tokens-and-how-to-count-them)
- [OpenAI tiktoken cookbook](https://developers.openai.com/cookbook/examples/how_to_count_tokens_with_tiktoken)
- [Anthropic token counting docs](https://platform.claude.com/docs/en/build-with-claude/token-counting)
- [JTokkit 项目文档](https://github.com/knuddelsgmbh/jtokkit)
