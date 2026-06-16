# 第 29 阶段计划：聊天回复片段引用

## Summary

第 29 阶段为聊天页增加“引用助手回复片段”能力。用户在已完成的助手回复中选中文本后，可以把片段添加到本轮输入上下文；发送后，用户消息保留引用标签，刷新会话后仍能恢复，并在鼠标悬停或键盘聚焦时预览引用内容。

该能力只增强模型上下文，不改写用户原始问题。SQLite 中 `chat_messages.content` 仍保存用户输入原文，引用片段单独保存到 `chat_messages.references_json`，模型输入和 token 估算时才由 `ChatReferencePromptFormatter` 拼成“引用片段块 + 用户问题”。

## Key Changes

- 前端交互：
  - 仅允许引用已完成助手回复中的选中文本；用户消息、空白区域、跨消息选择和正在流式输出的助手消息不触发引用。
  - 选中文本后在选区附近展示浮动按钮 `添加到对话`。
  - 添加后清理浏览器选区，并在发送区上方显示紧凑引用标签 `n 个已选文本片段`。
  - 引用标签支持清空按钮；hover、focus 和移动到预览面板时保持预览可见。
  - 发送后用户消息继续显示同样的引用标签，刷新会话后从后端 `references` 字段恢复。

- 前端状态：
  - `chat` store 维护 `pendingReferences`，发送时随 `POST /api/chat/stream` 一起提交。
  - 本地 optimistic user message 带 `references` 字段，避免发送后引用标签闪烁或丢失。
  - `normalizeMessage()` 读取后端返回的 `references`，保证刷新、切换会话和错误恢复后显示一致。

- 后端接口与持久化：
  - `ChatStreamRequest` / `AgentRequest` 增加 `references` 字段。
  - `ChatMessageResponse` 增加 `references` 字段，通常只有 user 消息有值。
  - `chat_messages` 新增 `references_json TEXT`，schema 初始化会为旧库补列。
  - `ChatReferenceSanitizer` 对引用做服务端二次清洗、截断和去重。
  - `ChatReferencesJsonCodec` 负责引用列表 JSON 编解码，解析失败时回落为空数组。

- 模型上下文：
  - 数据库中的用户 `content` 始终保持原始问题。
  - 有引用时，模型实际看到的 user 内容由引用说明、按序编号的引用片段和用户问题组成。
  - 会话记忆、上下文窗口估算和压缩后的历史回放都使用包含引用块的模型内容，保证刷新后继续对话时引用仍作为上下文参与。

## Data Contract

引用对象结构：

```json
{
  "id": "reference-1",
  "messageId": "assistant-message-id",
  "snippet": "用户选中的助手回复片段"
}
```

限制：

| 项 | 限制 |
| --- | --- |
| 单次引用数量 | 最多 5 个 |
| 单个片段长度 | 最多 1200 字符 |
| 总片段长度 | 最多 4000 字符 |
| 去重规则 | `messageId + snippet` |
| v1 支持范围 | 只引用助手回复文本 |

## API

`POST /api/chat/stream` 请求体新增可选字段：

```json
{
  "question": "基于这些内容继续展开",
  "references": [
    {
      "id": "ref-1",
      "messageId": "assistant-1",
      "snippet": "需要继续讨论的助手回复片段"
    }
  ]
}
```

会话详情中的消息响应新增 `references`：

```json
{
  "id": "user-message-1",
  "role": "user",
  "content": "基于这些内容继续展开",
  "references": [
    {
      "id": "ref-1",
      "messageId": "assistant-1",
      "snippet": "需要继续讨论的助手回复片段"
    }
  ]
}
```

## UI Notes

- 引用标签不嵌入 textarea，而是放在输入框上方的独立行，避免挤压输入文本。
- 标签、预览面板、清空按钮颜色接应用主题 token，适配浅色和深色主题。
- 预览面板与标签之间保留 hover 桥接区，鼠标从标签移动到预览面板时不会立即消失。
- 发送区引用标签与输入框之间保持紧凑间距，避免视觉上漂浮。

## Verification

- 前端构建：`npm --prefix cogniNote-agent-front run build` 通过；保留既有 Rolldown `#__PURE__` annotation warning 和 chunk size warning。
- 后端测试：覆盖引用落库、会话详情恢复、模型 prompt 包含引用块、用户原始 content 不被污染、上下文 token 估算包含引用内容。
- 交互验证：助手文本选择后出现 `添加到对话`；添加后显示引用标签；悬停/聚焦可预览；清空按钮可移除；发送和刷新后用户消息仍显示引用标签。

## Assumptions

- v1 不支持引用用户消息，也不做“在侧边聊天中提问”。
- 后端信任前端只提交助手消息来源，但仍对数量、长度、空值和重复项做防御性清洗。
- 引用是上下文增强能力，不改变消息正文展示，也不改变 RAG sources 的含义。
