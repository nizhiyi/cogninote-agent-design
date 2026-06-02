# CogniNote Agent 可维护性重构计划

## Summary

本次重构目标是解决 Phase 4 完成后出现的结构性可读性问题，不新增业务能力。核心目标是把前端从单个超长 `App.vue` 拆成 `Vue Router + Pinia + API client + views/components`，把后端按“全局三层，每层下按业务领域分包”重排，并统一普通 JSON API 返回格式。

计划落地日期：2026-06-02。  
执行状态：已完成。  
适用范围：保持 Phase 4 已有能力可用，包括文档导入、索引状态、搜索、模型配置、RAG SSE 对话、Spring Boot 静态前端托管。

## Key Changes

- 前端新增依赖：`vue-router`、`pinia`。
- 前端结构调整为：
  - `src/router/`：四个页面路由：`chat`、`knowledge`、`model-config`、`settings`。
  - `src/stores/`：按业务拆 `system`、`documents`、`search`、`modelConfig`、`chat`。
  - `src/api/`：统一封装 `httpClient`、普通 JSON API、SSE chat stream parser。
  - `src/views/`：页面级组件。
  - `src/components/`：可复用 UI 组件，如应用壳、导航、状态、列表、来源展示。
  - `src/styles/`：全局基础样式；`App.vue` 只保留应用壳和 `<router-view />`。
- 后端结构调整为：
  - `controller/{document,search,index,model,chat,system}`：只处理 HTTP、校验、响应包装。
  - `service/{document,search,index,model,chat,system}`：承载业务编排。
  - `repository/{document,model}`：只做 SQLite/JDBC 访问。
  - `domain/{document,search,model,chat,storage,ingestion}`：领域记录、枚举、接口、纯逻辑对象。
  - `dto/{document,search,index,model,chat,system}`：请求/响应 DTO。
  - `common/api`：统一响应、错误码、异常处理。
- 普通 JSON API 统一返回：

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "data": {},
  "timestamp": 1780000000000
}
```

- `POST /api/chat/stream` 保持 `text/event-stream`，不包装。
- `DELETE /api/documents/{id}` 成功时保持 `204 No Content`，不包装。
- 新增 SPA history 路由转发，支持整包 Jar 下刷新 `/chat`、`/knowledge`、`/model-config`、`/settings`。
- 补充中文维护型代码注释，要求美观、可读、方便维护；注释解释“为什么这样做”“这里有什么约束”“修改时要注意什么”，不做机械逐行复述。

## Implementation Changes

### Frontend

- 加入 `vue-router` 和 `pinia`，保留原有页面外观和业务能力。
- 抽出 `api/http-client.js`，集中解包 `ApiResponse<T>`，错误统一读取 `message/code`。
- 抽出 `api/chat-stream.js`，保留 `fetch + ReadableStream` 的 POST SSE 行为。
- 将 `App.vue` 的业务状态迁移到 Pinia stores，页面只消费 store action/state。
- 拆分四个 view：
  - `chat-view.vue`
  - `knowledge-view.vue`
  - `model-config-view.vue`
  - `settings-view.vue`
- 拆分复用组件：
  - `app-shell.vue`
  - `module-nav.vue`
  - `status-pill.vue`
  - `stat-grid.vue`
  - `segmented-control.vue`
  - `document-list.vue`
  - `search-results.vue`
  - `source-list.vue`
- 对 SSE 解析、统一响应解包、Pinia store 中的跨页面状态同步添加中文维护注释。

### Backend

- 新增统一响应层：
  - `ApiResponse<T>`
  - `ApiErrorCode`
  - `GlobalExceptionHandler`
  - `ResourceNotFoundException`
- 所有普通 JSON controller 返回 `ApiResponse<T>`。
- `ChatController` 的 SSE 接口保留原始事件协议。
- `DocumentController` 不再直接依赖 repository，列表查询和删除通过 service 编排。
- `SearchController`、`IndexController` 不再直接依赖 `KnowledgeStore`，改由 `SearchService`、`IndexService` 编排。
- 新增 `SpaForwardController`，只转发明确的前端 history 路由，避免误吞 `/api` 或静态资源。
- 包迁移按业务领域完成，旧的扁平包迁移到 `controller/service/repository/domain/dto/common`。
- 对事务边界、索引失败降级、RAG 检索降级、模型配置密钥复用、外部模型实例缓存等非显然设计决策添加中文维护注释。

## Compatibility Rules

- 不新增业务能力，不改变 Phase 4 的功能语义。
- 普通 JSON API 统一包装是一次有意的接口结构变更，前端同步适配，不保留旧裸 JSON。
- SSE 和 204 删除成功响应不参与 `ApiResponse<T>` 包装。
- SQLite 仍是知识库事实来源，Lucene 索引失败不删除已解析数据。
- Embedding 不可用时，RAG 从 `HYBRID`/`VECTOR` 降级到 `KEYWORD`，并通过 SSE `meta.retrievalMode` 暴露实际检索模式。

## Comment Standard

- 必须使用中文维护型注释。
- 注释只写有维护价值的内容：
  - 解释为什么这样设计。
  - 说明边界条件和兼容约束。
  - 提醒后续修改时不能破坏的约定。
- 复杂事务边界、索引失败降级、RAG 检索降级、SSE 手动解析、API Key 复用、DashScope 模型实例缓存等逻辑必须有注释。
- 简单自解释代码不逐行注释，避免注释噪音。
- 过期、误导或英文维护注释应同步更新为准确中文注释。

## Test Plan

- 后端：
  - `mvn test` 必须通过。
  - Controller 测试覆盖统一成功响应、统一错误响应、404、400。
  - SSE 测试保持断言 `event:meta`、`event:delta`、`event:done`，不检查 JSON 包装。
  - 删除文档仍验证成功时 `204`，不存在时返回统一包装的 `404` 错误。
  - SPA history 路由刷新应 forward 到 `index.html`。
- 前端：
  - `npm --prefix cogniNote-agent-front run build` 必须通过。
  - 四个路由页面可切换，刷新页面不白屏。
  - 模型配置保存/测试、文档导入、搜索、RAG 流式回答均通过统一 API client 工作。
- 整包：

```powershell
$env:JAVA_HOME='D:\CodeApps\Java-JDK\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
npm --prefix cogniNote-agent-front run build
mvn -Pwith-frontend package
```

## Verification Result

本次重构完成后的验证结果：

- `mvn test`：27 个测试全部通过。
- `npm --prefix cogniNote-agent-front run build`：构建通过。
- `mvn -Pwith-frontend package`：构建通过，Spring Boot Jar 包含前端静态资源。
- 注释检查：`src/main/java` 和前端 `src` 下的 `//` 注释均已改为中文维护型注释。

JDK 25 下仍会出现 SQLite native access、Mockito 动态 agent、Lucene vector module 相关运行警告；这些警告不影响当前构建结果，后续可在运行参数或测试配置中单独治理。

## Assumptions

- JDK 25 继续作为后端基线。
- 本次只做结构重构，不改业务语义、不新增功能、不引入 UI 组件库。
- 前端允许新增 `vue-router` 和 `pinia` 两个依赖。
- 普通 JSON API 统一包装允许破坏旧裸响应；当前前端已同步适配。
- SSE 和 `204 No Content` 删除成功响应不参与统一响应包装。
- 注释目标是提升维护理解效率，不追求逐行注释。
