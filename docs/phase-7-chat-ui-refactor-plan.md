# CogniNote Agent 第七阶段任务计划：对话式前端重构

## Summary

第七阶段只做前端产品形态重构，不改后端接口和数据库。目标是把当前“功能页导航”改成桌面对话应用：左侧为会话列表，底部固定设置入口，主区域为对话流，输入区支持“使用知识库”开关。非聊天相关能力统一移动到设置页内。

第八阶段再做真正的 SQLite 聊天记忆。第七阶段会先把前端状态模型和 UI 预留成可对接 SQLite 会话的结构，避免第八阶段推倒重来。

## Key Changes

- 前端布局改为桌面聊天形态：左侧 sidebar 显示应用标识、新建对话、临时会话列表和设置入口；主区域显示消息流、引用来源、输入框、发送/停止按钮。
- 设置页承载知识库、模型配置、索引状态、系统状态等非聊天功能。
- 不新增后端 API，继续使用现有 `POST /api/chat/stream`。
- “使用知识库”第一版只做全库开关。开启时沿用当前 RAG；关闭时显示纯对话将在第八阶段接入，不发送假请求。
- 前端会话只做运行期临时状态，刷新页面后不保证恢复。真正跨重启记忆由第八阶段 SQLite 实现。

## Implementation Changes

- `AppShell` 改为左侧栏 + 主内容区布局，移除顶部大标题式工作台。
- `/chat` 保持默认页面；`/settings` 成为非聊天能力总入口；`/knowledge` 和 `/model-config` 保留路由但从主导航隐藏。
- `chat` store 增加 `sessions/messages/activeSessionId`，发送时先追加 user 消息，再把 SSE delta 追加到 assistant 消息。
- 聊天页改为消息气泡流：支持 user、assistant、error、loading、stopped 状态；assistant 消息下方展示本轮引用来源。
- 设置页内部用分段入口归拢系统状态、知识库管理、模型配置三个区域，复用现有知识库和模型配置逻辑。
- 样式采用深色桌面应用风格，左侧 sidebar 固定宽度，移动端降级为上方会话切换与单列聊天流。

## Phase 8 Follow-up

- 新增 `chat_sessions` 与 `chat_messages` SQLite 表。
- 新增会话 CRUD 与消息查询 API。
- 扩展 `POST /api/chat/stream` 请求体，支持 `conversationId` 与 `useKnowledgeBase`。
- `useKnowledgeBase=false` 时走纯模型对话；`useKnowledgeBase=true` 时走当前 RAG，并注入最近 N 轮会话历史。
- 第一版记忆只做最近 N 轮短期会话记忆，不做长期摘要、用户画像、向量化历史记忆。

## Test Plan

- `npm --prefix cogniNote-agent-front run build` 通过。
- `/chat` 默认打开为左侧会话 + 主对话布局。
- 新建对话、切换临时会话、发送问题、停止生成可用。
- RAG 开启时沿用现有 `/api/chat/stream`，回答和 sources 正常显示。
- 关闭“使用知识库”时不发送请求，并显示第八阶段提示。
- 设置页能进入模型配置、知识库管理、索引状态和系统状态。
- 刷新 `/chat`、`/settings` 不白屏。

## Assumptions

- 第七阶段严格前端优先，不新增数据库和后端 API。
- SQLite 聊天记忆放到第八阶段实现。
- 知识库选择第一版只做全库开关，不做文档级筛选。
- 第七阶段可以调整前端路由导航，但不删除旧路由，避免刷新和历史链接失效。
- 不新增大型 UI 组件库。
