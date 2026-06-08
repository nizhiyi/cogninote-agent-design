# 第 23 阶段计划：知识库追问补全自动触发与前端可配置

## Summary

第 23 阶段把第 21 阶段的“知识库模式每轮都先调用追问补全 Agent”调整为“用户可配置策略”，默认 `AUTO`。这样完整问题可以直接检索，省略式追问或原问题检索弱时才补全检索 query，降低额外模型调用带来的延迟和成本。

该功能只影响知识库模式下的检索 query，不会改写用户原始消息，不会改变普通对话，也不会把内部补全结果写入 SQLite 聊天记录。

## 背景

第 21 阶段解决了“给出代码示例”“继续”“展开”等省略式追问直接检索不准的问题。但在第 22 阶段之后，主流模型上下文窗口已经普遍达到 `128K`、`200K` 甚至 `1M`，完整问题场景没有必要每轮都额外调用一次模型来判断是否需要改写检索问题。

第 23 阶段保留追问补全能力，但把触发权收敛为后端全局聊天设置，并在前端“设置 -> 知识库 -> 知识库追问补全策略”里暴露给用户选择。

## 模式语义

| 模式 | 说明 | 适用场景 |
| --- | --- | --- |
| `AUTO` | 默认值。通过本地轻量打分判断是否调用补全 Agent；省略式追问或弱检索时再补全。 | 日常使用，兼顾准确性、延迟和成本。 |
| `ALWAYS` | 保持第 21 阶段行为，知识库模式每轮都先调用补全 Agent 判断是否需要改写检索 query。 | 更看重追问稳健性，不介意额外延迟。 |
| `OFF` | 完全关闭追问补全，始终使用用户原问题检索。 | 成本最低，或用户明确不希望内部模型改写检索 query。 |

## 配置优先级

1. SQLite `app_settings` 用户设置优先。
2. 没有用户设置时读取 `COGNINOTE_QUERY_CONTEXTUALIZER_MODE`。
3. 未配置 mode 且旧配置 `COGNINOTE_QUERY_CONTEXTUALIZER_ENABLED=false` 时等价于 `OFF`。
4. 全部缺省时为 `AUTO`。

新增配置：

```yaml
app:
  chat:
    query-contextualizer:
      mode: ${COGNINOTE_QUERY_CONTEXTUALIZER_MODE:}
      enabled: ${COGNINOTE_QUERY_CONTEXTUALIZER_ENABLED:true}
```

`enabled` 只作为兼容旧版本的兜底开关，新代码应优先使用 `mode` 或 `/api/chat/settings`。

## 后端改动

- 新增 `QueryContextualizerMode`：`AUTO`、`ALWAYS`、`OFF`。
- 新增 SQLite `app_settings(setting_key, setting_value, updated_at)`，用于保存全局聊天设置。
- 新增普通 JSON API：
  - `GET /api/chat/settings`
  - `PUT /api/chat/settings`
- 新增请求/响应字段：
  - `queryContextualizerMode`: `"AUTO" | "ALWAYS" | "OFF"`
- 新增 `QueryContextualizerTriggerDecider`：
  - 不做精确短语匹配。
  - 通过历史存在、短句、省略/指代/延续动作、英文领域切换和完整问题反向信号做轻量打分。
  - 指代信号覆盖“它/这个/那种/上文/刚提到/你说的/那应该”等会话引用。
  - 省略补全信号覆盖“哪个更适合/有没有证书/怎么退款/怎么治疗/吃什么药”等缺少主体的问题。
  - 动作型追问信号覆盖“实现代码案例/给出示例/怎么部署/怎么排查/优缺点/对比/总结”等短请求。
  - 英文追问覆盖 `in travel`、`what about finance`、`code sample`、`implementation` 等约束切换或补充请求。
  - 无历史消息时不调用补全 Agent。
  - 完整独立问题默认直接检索。
- 补全 Prompt 输入升级为“会话摘要 + 最近 N 条原文消息 + 当前问题”，压缩会话不会只看最近消息。
- AUTO 模式下，如果原问题检索无来源且存在历史，会允许一次弱检索补全重试。
- AUTO 模式下，如果本地判断已经触发但补全模型误判 `shouldRewrite=false`，且当前问题是短动作型追问，会使用最近一条有明确主题的用户问题构造本地兜底检索 query。例如先问“红黑树是什么？”，再问“实现代码案例”，兜底 query 为“红黑树是什么？ 实现代码案例”。兜底 query 仍只用于检索，不写入聊天记录。

## 前端改动

- 新增 `chat-settings-api` 和 `chat-settings` Pinia store。
- 在“设置 -> 知识库 -> 知识库追问补全策略”中展示该全局策略，不跟随单个对话模型配置。
- 使用三个大按钮暴露选项，点击后立即保存到后端 SQLite：
  - `自动`：推荐。只有像追问或检索较弱时才补全检索问题。
  - `始终`：每轮知识库问答都先判断是否需要补全，准确性更稳但更慢。
  - `关闭`：不补全追问，成本最低，但“继续/给个例子”等追问可能检索不准。
- 页面说明文本明确：
  - 只影响知识库检索 query。
  - 不会修改聊天记录中的用户原文。
  - 不会影响纯模型对话。

## API 示例

读取设置：

```http
GET /api/chat/settings
```

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "data": {
    "queryContextualizerMode": "AUTO"
  },
  "timestamp": 1710000000000
}
```

保存设置：

```http
PUT /api/chat/settings
Content-Type: application/json
```

```json
{
  "queryContextualizerMode": "OFF"
}
```

## Test Plan

- 默认返回 `queryContextualizerMode=AUTO`。
- `PUT /api/chat/settings` 保存 `AUTO/ALWAYS/OFF` 后，`GET` 能正确回显。
- `OFF` 模式不调用 `AiChatRuntime.callText`，检索 query 等于用户原问题。
- `ALWAYS` 模式保持第 21 阶段行为，非法 JSON 仍回退原问题。
- `AUTO` 无历史消息时不调用补全 Agent。
- `AUTO` 完整问题不调用补全 Agent。
- `AUTO` 省略式追问调用补全 Agent，检索 query 包含历史主题。
- `AUTO` 能识别中文指代、省略式比较、动作型追问和英文领域切换追问。
- `AUTO` 在补全模型误判短动作追问为不改写时，使用最近明确主题用户问题做本地兜底。
- 已有摘要时，补全 Prompt 包含摘要和最近消息。
- 前端“设置 -> 知识库”页默认显示“自动”，切换保存后刷新仍能回显。
- `mvn test` 通过。
- `npm --prefix cogniNote-agent-front run build` 通过。

## 资料依据

- [A Surprisingly Simple yet Effective Multi-Query Rewriting Method for Conversational Passage Retrieval](https://arxiv.org/html/2406.18960v1)：对话检索改写需要处理 coreference、ellipsis 和 topic transition。
- [Learning When to Retrieve, What to Rewrite, and How to Respond in Conversational QA](https://arxiv.org/html/2409.15515v1)：多轮 RAG 不应盲目检索或改写，应判断何时需要检索，并利用会话摘要/上下文提高检索信号完整性。
- [Query Rewriting in RAG Applications](https://shekhargulati.com/2024/07/17/query-rewriting-in-rag-applications/)：短 query、缩写、历史约束继承和 `in travel` 这类领域切换追问都需要 query rewrite 或 query enrichment。
- [为什么要问题重写](https://javaup.chat/super-agent/rag/query-rewrite)：中文 RAG 常见问题包括代词指代、省略主语/宾语、口语化表达和短模糊问题。
- [来自工业界的知识库 RAG 方案：多轮会话优化](https://hustyichi.github.io/2024/12/10/multi-round-rag/)：基于历史会话改写当前追问是多轮 RAG 中性价比较高的优化路径。

## Assumptions

- 追问补全策略是全局聊天设置，不跟随单个模型配置保存。
- 默认使用 `AUTO`，不是 `ALWAYS`，以符合大上下文窗口时代对延迟和调用成本的要求。
- 本阶段不在前端展示每轮是否触发补全；触发原因先通过后端日志观察。
