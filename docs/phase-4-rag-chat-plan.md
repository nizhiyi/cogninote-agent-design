# CogniNote Agent 第四阶段任务计划：RAG 对话闭环

## Summary

第四阶段只做 Milestone 4：RAG 对话，目标是在 Phase 3 的 SQLite + Lucene 混合检索基础上，完成“配置模型 -> 提问 -> 检索片段 -> 构造 Prompt -> 调用 DashScope Chat -> SSE 流式回答 -> 展示引用来源”的闭环。

## Key Changes

- 模型配置第一版只支持 Spring AI Alibaba DashScope，不做 OpenAI-compatible、Ollama、LM Studio 多 provider。
- API Key 先以开发态明文保存到 SQLite，README 明确风险；加密存储放到本地交付或安全加固阶段。
- 新增模型配置 API：`GET /api/model-config`、`PUT /api/model-config`、`POST /api/model-config/test`。
- 新增 RAG 对话 API：`POST /api/chat/stream`，返回 `text/event-stream`。
- 前端把“对话”入口升级为可用页；“模型配置”入口升级为 DashScope 配置页。

## Implementation Changes

- SQLite 新增单行 `model_config` 表，保存 DashScope API Key、Chat 模型、Embedding 模型、Embedding 维度、temperature 和 Top K。
- `EmbeddingGateway` 优先读取 SQLite active config 动态构造 DashScope EmbeddingModel；没有 SQLite 配置时回退 Phase 3 环境变量配置。
- 新增 `LlmGateway` 和 `RagChatService`，通过 Spring AI `ChatModel.stream(Prompt)` 流式调用 DashScope。
- RAG 默认使用 `HYBRID` 检索；Embedding 不可用时自动降级 `KEYWORD`。
- Prompt 要求答案基于上下文，信息不足时明确说明，并使用 `[1]`、`[2]` 标注引用来源。
- SSE 事件固定为 `meta`、`delta`、`done`、`error`。
- 前端新增对话页和模型配置页；知识库页保留 Phase 3 检索面板。

## Test Plan

- 模型配置保存、读取、默认值填充。
- API Key 缺失时测试连接和对话返回明确错误。
- RAG Prompt 包含上下文、问题和来源编号。
- HYBRID 不可用时降级 KEYWORD。
- `/api/chat/stream` 输出 `meta -> delta -> done`，异常时输出 `error`。
- 前端构建和整包构建通过。

## Assumptions

- 后端继续统一使用 JDK 25。
- 第四阶段默认 provider 是 Spring AI Alibaba DashScope。
- API Key 明文 SQLite 仅作为开发态取舍；后续再做 Windows 本地加密或凭据管理。
- 不保存聊天历史；刷新页面后对话内容丢失。
- 不新增 UI 组件库，继续用原生 Vue + CSS。
