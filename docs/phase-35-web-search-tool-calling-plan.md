# 第 35 阶段：联网搜索 Tool Calling 与网页来源引用

## Summary

第 35 阶段为聊天 Agent 增加用户显式启用的联网搜索能力。实现方式直接采用 Spring AI Tool Calling：当用户本轮开启联网并且全局搜索配置可用时，后端才把 `WebSearchTools` 挂载到本轮 `ChatClient` 调用；未开启联网时不注册工具，模型没有任何联网函数可调用。

本阶段不做“后端先搜索再塞上下文”的过渡版。联网搜索是 Agent 的可选工具能力，而不是知识库检索的另一种固定上下文来源。模型可以在本轮生成过程中根据问题决定是否调用 `searchWeb`，工具返回结果同时进入模型上下文，并以结构化网页来源回传前端和落库。

## Implementation Status

当前实现已落地为单 provider MVP：`EXA`。后端新增 `WebSearchToolPolicy`、`WebSearchTools`、`ExaWebSearchClient`、`ToolExecutionCollector` 和 `/api/web-search/*` 设置接口；前端新增“策略 -> 联网搜索”设置页、聊天设置弹层联网开关、`tool` SSE 合并逻辑和网页来源展示。

实现后补充的边界：

- API Key 输入框只在编辑态短暂保存草稿，保存响应不回显明文；`enabled=true` 没有 Key 时会被后端归一化为 `false`。
- 设置页提供“配置说明”和“调用策略”弹窗，链接到 Exa API Keys、Search API、Coding Agents Guide 和 Pricing 官方文档。
- `ExaWebSearchClient` 按归一化超时值缓存 `RestClient`，避免每次搜索重建 HTTP client；Exa 请求只开启 `contents.highlights`，不拉全文和 summary。
- `WebSearchSettingsService.snapshot()` 保留读写事务语义，因为首次读取会初始化默认关闭配置并写入 `app_settings`。

## Core Judgment

联网搜索值得做，但必须是显式开关和显式工具挂载。

- 用户没有开启联网：后端不挂 `WebSearchTools`，不读取联网搜索配置，不调用搜索 API，不产生网页来源。
- 用户开启联网但全局配置不可用：后端拒绝本轮联网能力或返回可配置错误，不把一个必失败工具暴露给模型。
- 用户开启联网且配置可用：本轮 Agent 挂载 `WebSearchTools`，通过 `toolContext` 传入 `requestId`、搜索配置快照和工具结果收集器。

## Reference Findings

本阶段参考 `资料参考/tj-aigc` 的工具调用设计：

- `CourseTools` / `OrderTools` 使用 `@Tool` 和 `@ToolParam` 暴露工具方法。
- 各 Agent 自己决定 `tools()`，不是所有 Agent 默认拥有全部工具。
- `AbstractAgent` 在构造 `ChatClientRequestSpec` 时调用 `.tools(this.tools())` 和 `.toolContext(...)`。
- 工具通过 `ToolContext` 获取 `requestId/userId` 等运行时上下文。
- 工具结果一份返回给模型，另一份通过 `ToolResultHolder` 传给前端参数事件。

CogniNote 只借鉴这些边界，不照搬静态 `ToolResultHolder`、数字事件和基于内存全局状态的停止机制。CogniNote 使用请求级 `ToolExecutionCollector`，并沿用当前 `meta/delta/done/error` SSE 语义，必要时新增 `tool` 事件。

## Key Changes

- `ChatStreamRequest` 新增 `Boolean useWebSearch`，表示本轮用户是否启用联网。
- `AgentRequest` 新增 `useWebSearch`，从 HTTP DTO 传到 Agent 执行层。
- 新增联网搜索设置服务，保存 provider、启用状态、API Key 配置状态、默认结果数、超时和调用上限。MVP 固定 `EXA`，不暴露 Base URL 输入。
- 新增 `WebSearchTools`，通过 Spring AI `@Tool` 暴露 `searchWeb(...)`。
- 新增 `WebSearchClient` 抽象，MVP 只实现 `EXA`。保留 provider 字段是为了数据兼容和后续替换，不在本阶段实现第二个 provider。
- 新增请求级 `ToolExecutionCollector`，收集本轮工具调用产生的网页来源和工具调用元数据。
- 扩展 `AiChatRuntime.stream(...)`，支持 `tools` 和 `toolContext`。
- 扩展 `SpringAiChatRuntime`，构建 `ChatClientRequestSpec` 后按需调用 `.tools(...)` 和 `.toolContext(...)`。
- SSE 新增 `tool` 事件，用于在模型流式生成过程中把网页来源增量推给前端。
- 聊天消息来源模型增加网页来源字段，支持本地知识库来源和网页来源同屏展示、同消息落库。

## Non-Goals

- 不做默认联网。用户没有开启本轮联网时，模型看不到联网工具。
- 不做 LLM 自动路由决定“是否启用联网”。本轮是否可联网由用户开关和后端配置共同决定。
- 不把联网搜索结果写入本地知识库、Lucene 索引或知识图谱。
- 不让联网工具执行有副作用的动作。本阶段工具只读，只查询公开网页。
- 不支持浏览器自动打开网页、登录态复用、爬取私有页面或访问本地文件。
- 不实现 MCP 工具市场。MCP 可作为后续统一工具协议阶段。

## Backend Flow

不开启联网时：

```text
POST /api/chat/stream
  -> ChatStreamRequest(useWebSearch=false/null)
  -> AgentRequest.from(...)
  -> ChatAgentRouter route GENERAL_CHAT / KNOWLEDGE_BASE
  -> Agent prepareInvocation(...)
       advisors: memory advisor / RAG advisor
       tools: []
       toolContext: {}
  -> AiChatRuntime.stream(...)
  -> SpringAiChatRuntime builds ChatClientRequestSpec
       does not call .tools(...)
       does not call .toolContext(...)
  -> normal meta/delta/done/error stream
```

开启联网且配置可用时：

```text
POST /api/chat/stream
  -> ChatStreamRequest(useWebSearch=true)
  -> AgentRequest.from(...)
  -> WebSearchToolPolicy evaluates request + settings
  -> Agent prepareInvocation(...)
       advisors: memory advisor / RAG advisor
       tools: [webSearchTools]
       toolContext:
         requestId
         conversationId
         webSearchSettingsSnapshot
         toolExecutionCollector
  -> AiChatRuntime.stream(...)
  -> SpringAiChatRuntime attaches .tools(...) and .toolContext(...)
  -> model may call searchWeb(...)
  -> WebSearchTools calls provider client
  -> tool result returns to model and collector
  -> SSE tool event pushes web sources
  -> assistant done/error/stopped persists answer + all sources
```

## Switch Policy

联网工具挂载由 `WebSearchToolPolicy` 统一判断，避免多个 Agent 散落分支。

推荐规则：

```text
enabledForThisTurn =
  request.useWebSearch == true
  && settings.enabled == true
  && settings.provider == EXA
  && API Key 已配置
```

结果分三类：

- `DISABLED_BY_REQUEST`：本轮未开启联网，返回空 tools 和空 toolContext。
- `BLOCKED_BY_SETTINGS`：用户开启了联网，但全局搜索配置不可用，返回明确配置错误。
- `ENABLED`：允许挂载 `WebSearchTools`，创建本轮 `ToolExecutionCollector`。

四种对话组合：

```text
纯模型，联网关：memory only
纯模型，联网开：memory + WebSearchTools
知识库，联网关：memory + RAG Advisor
知识库，联网开：memory + RAG Advisor + WebSearchTools
```

## Runtime Contract

`AiChatRuntime` 增加带工具的流式入口：

```java
Flux<String> stream(
        String systemPrompt,
        String userMessage,
        List<Advisor> advisors,
        Map<String, Object> advisorParams,
        List<Object> tools,
        Map<String, Object> toolContext
);
```

兼容策略：

- 现有不带工具的 `stream(...)` 保留，内部委托到新方法并传空工具。
- `tools` 为空时，`SpringAiChatRuntime` 不调用 `.tools(...)`。
- Spring AI 1.1.x 的 `ChatClientRequestSpec.tools(...)` 是 `Object...` varargs；实现时必须调用 `spec.tools(tools.toArray())`，不能直接把 `List<Object>` 当作单个工具传入。
- `toolContext` 为空时，`SpringAiChatRuntime` 不调用 `.toolContext(...)`。
- 直接 `Prompt` 调用路径不挂工具，保留给连接测试和简单内部调用。

## Tool Design

`WebSearchTools` 是 Spring Bean，但每轮执行状态不保存在 Bean 字段里。工具通过 `ToolContext` 获取请求级上下文。

工具方法建议：

```java
@Tool(
    name = "searchWeb",
    description = """
            搜索公开网页，获取最新、外部或可核验的信息。
            仅当回答需要实时事实、公开网页证据，或当前上下文与已提供资料不足以可靠回答时使用。
            """
)
public WebSearchToolResult searchWeb(
        @ToolParam(description = "简洁明确的搜索关键词。不要包含 API Key、Token、密码或用户隐私数据。")
        String query,
        ToolContext toolContext
) {
    WebSearchToolContext context = WebSearchToolContext.from(toolContext);
    String normalizedQuery = WebSearchQuerySanitizer.normalize(query);
    context.collector().checkCallLimit();

    WebSearchRequest request = new WebSearchRequest(
            normalizedQuery,
            context.settings().maxResults(),
            context.settings().searchMode(),
            true
    );
    WebSearchToolResult result = webSearchClient.search(request, context.settings());
    context.collector().record("searchWeb", normalizedQuery, result);
    return result;
}
```

工具执行规则：

- 不设置 `returnDirect = true`，保持默认行为：工具结果先返回给模型，再由模型生成最终回答；前端 sources 由 `ToolExecutionCollector` 单独推送。
- `query` 最大长度限制为 300 字符，空 query 直接返回工具错误结果，不调用 Exa。
- 单轮最多允许 1 到 3 次搜索调用，默认 2 次。
- 每次搜索最多返回 5 到 8 条结果，默认 5 条。
- 默认超时 8 到 15 秒，超时返回工具错误结果，不让调用线程无限等待。
- Exa 返回 `401/429/5xx` 或超时时，工具返回可读错误给模型，并记录脱敏日志。
- 工具结果必须是 Jackson 可序列化的普通 DTO/record，包含 `title`、`url`、`snippet`、`provider`、`score` 和可选 `publishedAt`；不要返回 `Flux`、`Mono`、`CompletableFuture`、`Optional` 或 SDK 原始对象。
- `ToolContext` 参数只用于读取后端运行时上下文，不会进入模型可见的工具入参 schema；放入其中的 settings snapshot 仍要避免 `toString()` 泄露 API Key。

Exa 客户端封装建议：

```java
public final class ExaWebSearchClient implements WebSearchClient {
    private static final URI SEARCH_URI = URI.create("https://api.exa.ai/search");

    private final ConcurrentMap<Integer, RestClient> restClientsByTimeoutMs = new ConcurrentHashMap<>();

    @Override
    public WebSearchToolResult search(WebSearchRequest request, WebSearchSettingsSnapshot settings) {
        ExaSearchResponse response = restClient(settings.timeoutMs()).post()
                .uri(SEARCH_URI)
                .header("x-api-key", settings.apiKey())
                .body(ExaSearchRequest.from(request))
                .retrieve()
                .body(ExaSearchResponse.class);
        return ExaSearchMapper.toToolResult(response, settings.provider());
    }

    private RestClient restClient(int timeoutMs) {
        int normalizedTimeoutMs = Math.clamp(timeoutMs, 1000, 30000);
        return restClientsByTimeoutMs.computeIfAbsent(normalizedTimeoutMs, ExaWebSearchClient::buildRestClient);
    }

    private static RestClient buildRestClient(int timeoutMs) {
        ...
    }
}
```

`ExaSearchRequest.from(request)` 只组装 `query`、`type`、`numResults` 和 `contents.highlights`，不要把 Exa SDK 或完整响应结构泄漏到 Agent 层。

Exa 请求默认策略：

```json
{
  "query": "Spring AI tool calling latest docs",
  "type": "auto",
  "numResults": 5,
  "contents": {
    "highlights": true
  }
}
```

实现约束：

- `highlights` 必须放在 `contents` 内；不要使用顶层 `highlights/text/summary`。
- MVP 默认只开 `highlights`，不拉全文 `text`，不生成 `summary`，避免 token 和费用失控。
- `type` 默认 `auto`；如果流式体验需要更低延迟，可以在设置中允许切到 `fast`。
- Java 侧优先用现有 HTTP 客户端封装 REST 调用，不引入 Exa Python/JS SDK。
- 工具返回对象只给模型必要信息：标题、URL、发布时间、highlights 合并出的 snippet、provider、score。

## ToolExecutionCollector

不要使用静态全局 `ToolResultHolder`。第 35 阶段新增请求级收集器：

```text
ToolExecutionCollector
  requestId
  maxCalls
  calls
  webSources
```

职责：

- 记录工具调用次数，超过上限时拒绝继续搜索。
- 保存 `query`、provider、耗时、结果数和错误信息。
- 将搜索结果转换为前端可展示的网页来源。
- 在流式结束、停止或异常时提供本轮完整工具来源快照。

生命周期：

- `AbstractChatAgent.stream(...)` 创建。
- 通过 `toolContext` 传给 `WebSearchTools`。
- SSE 层在工具事件或 done 阶段读取。
- 消息落库后释放引用，不进入静态缓存。

## Source Contract

现有 `RagSourceResponse` 主要表达本地 chunk 来源。第 35 阶段需要兼容网页来源，推荐收敛为通用来源结构，或者在不破坏旧字段的前提下扩展字段。

推荐新增字段：

```text
sourceType: LOCAL | WEB
provider: MVP 固定 EXA
url: 网页 URL
title: 网页标题
publishedAt: 可选发布时间
```

兼容策略：

- 本地来源继续保留 `chunkId/documentId/fileName/sourcePath/heading/pageNumber`。
- 网页来源的 `chunkId` 使用稳定伪 ID，例如 `web:{sha256(url)}`，避免前端列表 key 冲突。
- 前端老逻辑看到 `sources` 仍可渲染；新逻辑按 `sourceType` 区分本地文档和网页来源。

## SSE Contract

保留现有事件：

```text
meta
delta
done
error
```

新增：

```text
tool
```

`tool` 事件用于工具调用完成后增量推送网页来源和工具调用状态。示例：

```json
{
  "requestId": "request-1",
  "toolName": "searchWeb",
  "query": "Spring AI tool calling",
  "status": "COMPLETED",
  "durationMs": 820,
  "sources": [
    {
      "sourceType": "WEB",
      "index": 1,
      "title": "Spring AI Reference",
      "url": "https://docs.spring.io/spring-ai/...",
      "provider": "EXA",
      "preview": "..."
    }
  ]
}
```

前端处理规则：

- 收到 `meta`：初始化本轮消息和本地知识库来源。
- 收到 `tool`：把网页来源合并到当前 assistant message 的 sources。
- 收到 `delta`：追加模型文本。
- 收到 `done`：刷新会话详情，确保后端落库来源与前端一致。

## Settings

新增联网搜索全局设置，建议复用 `app_settings` 保存 JSON 快照，后续复杂化后再拆独立表。

设置字段：

```text
enabled
provider
apiKey
apiKeyConfigured
maxResults
maxCallsPerTurn
timeoutMs
searchMode
```

API 建议：

```text
GET  /api/web-search/settings
PUT  /api/web-search/settings
POST /api/web-search/test
```

响应永远不返回明文 API Key，只返回 `apiKeyConfigured`。MVP `EXA` 需要用户配置 API Key，用户留空保存时沿用旧 Key，传入新 Key 时覆盖旧 Key。

## Providers

第 35 阶段 provider 选择：

MVP 不同时封装多个联网服务。第 35 阶段只做一套 `WebSearchTools.searchWeb(...)` 工具入口和一个 `WebSearchClient` 抽象，首个可用实现选择 `EXA`。其它 provider 只作为后续扩展候选，不进入第一版代码实现。

Provider 对比：

| Provider | 免费/成本 | 接入门槛 | 适配 Tool Calling | 主要问题 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `EXA` | 有免费额度，适合开发验证和个人使用 | 只需用户配置 API Key | 结果结构适合转 sources | 非国内服务，中文生态不是最强 | MVP 选择 |
| `SEARXNG` | 自托管免费 | 需要用户自己部署或找可用实例 | 可适配 | 公共实例不稳定，JSON 常被禁 | 后续自托管选项 |
| `BAIDU_QIANFAN` | 有国内免费额度 | 需要百度智能云账号和鉴权配置 | 可适配 | 平台配置较重，免费额度较小 | 后续国内选项 |
| `METASO` | 有试用额度信息 | 需要进一步确认规则 | 可能适配 | 文档、额度、商用条款需确认 | 暂不进 MVP |
| `BOCHA` | 按量付费 | API Key 配置简单 | 适配度好 | 免费优先阶段成本不占优 | 付费增强 |
| `TAVILY` | 有免费额度 | API Key 配置简单 | 适配度好 | 英文/海外生态更强 | 国际增强 |
| `BRAVE` | 按量计费 | 需要注册订阅 | 适配度好 | 免费门槛和响应结构不如 Exa 简单 | 后续增强 |

最终选择 `EXA` 的好处：

- 免费额度更适合 MVP 验证，不需要用户自托管。
- 接入路径简单，设置页只需要收 API Key。
- 结果天然适合转换为网页来源，方便前端展示引用和后端落库。
- 延迟和返回内容都适合 Agent 工具调用，不需要先做复杂抓取链路。
- 后端仍能保持单一 `WebSearchClient` 抽象，后续替换或新增国内 provider 不影响 Agent 主流程。

MVP 默认参数建议：

```text
provider = EXA
maxResults = 5
maxCallsPerTurn = 2
timeoutMs = 10000
searchMode = auto
includeFullText = false
includeSummary = false
```

## Frontend Changes

前端不新增顶层页面，放入现有设置中心：

- 导航位置：`/settings?item=web-search`。
- 左侧设置导航：在 `SETTINGS_NAV_GROUPS` 的 `policy` 分组下，放在“聊天与检索”后面，名称为“联网搜索”。
- 页面组件：新增 `components/web-search-settings-panel.vue`，由 `settings-view.vue` 在 `activeItem === 'web-search'` 时渲染。
- 状态管理：新增 `stores/web-search-settings.js` 和 `api/web-search-settings-api.js`，不要塞进现有 `chat-settings` store，避免全局 provider 配置和聊天检索策略混在一起。
- 样式落点：优先复用 `styles/settings-center.css`、`styles/controls.css` 里的设置面板和表单样式；如果需要联网专属样式，新增 `styles/web-search-settings.css` 并在 `main.js` 统一引入，不在组件里堆大量一次性样式。

设置页布局：

```text
settings-view.vue
  settings-center-header: 策略 / 联网搜索
  settings-center-content--single
    WebSearchSettingsPanel
      header: 联网搜索
      status strip: 当前状态 / Provider: EXA / Key 是否已配置 / 最近测试结果
      settings card: 基础配置
        enabled switch
        provider readonly select: EXA
        API Key password input
        searchMode segmented: auto / fast
        maxResults stepper: 1-10, default 5
        maxCallsPerTurn stepper: 1-3, default 2
        timeoutMs input: default 10000
      action row
        Test connection
        Save
      inline alert
        quota/privacy notice and last error
```

交互规则：

- `enabled` 是全局能力开关；关闭时聊天弹层不能打开本轮联网。
- `enabled` 必须和 Key 状态一致：没有已保存 Key 且当前输入框也没有待保存 Key 时，启用开关保持禁用并给出提示；后端保存时也会把无 Key 的 `enabled=true` 归一化为 `false`。
- API Key 输入框永远不回显明文；已配置时显示“已配置”，留空保存沿用旧 Key。
- API Key 输入框必须允许粘贴。前端 store 不能在每次 normalize 远端设置时清空用户正在编辑的 `apiKey` 草稿，只有保存成功或重新加载远端设置时才清空。
- `Test connection` 使用固定测试 query，例如 `latest AI search API news`，只验证 provider 可用，不写入聊天记录。
- 表单错误展示在对应字段下方；保存和测试按钮必须有 loading/disabled 状态。
- 页面提供“配置说明”和“调用策略”两个说明入口，用弹窗解释 Exa Key 获取、免费额度/计费风险、调用上限、超时和 `auto/fast` 的差异，并链接 Exa 官方文档。
- 页面用现有 `settings-panel/settings-card` 风格，保持安静、工具型布局，不做营销式 hero 或说明长文。
- 使用 Element Plus 表单控件和 lucide 图标；图标按钮要有 `aria-label/title`。

聊天输入设置弹层增加“联网搜索”开关：

- 位置：`chat-settings-popover.vue` 的“使用知识库”开关下方，RAG 检索模式上方。
- 形态：与“使用知识库”一致的 switch 行，文案为“联网搜索”。
- 摘要区：新增一枚短状态 `联网开` / `联网关`；未配置时显示 `联网未配置`。
- 禁用规则：全局联网未启用或 API Key 未配置时，switch 禁用，并显示一个到 `/settings?item=web-search` 的设置入口。
- 布局规则：弹层第一行只放知识库和联网两个 switch，检索模式和 Top K 放到下一行；窄宽度下控件必须换行，不能撑出弹层。
- 提交载荷：`streamChatAnswer` payload 增加 `useWebSearch: chatStore.useWebSearch`。
- SSE：收到 `tool` 事件时，把网页 sources 合并到当前 assistant message 的 `sources`，不要等 `done` 才显示。

消息来源展示：

- 本地知识库来源和网页来源同一个 sources 区域展示，但按 `sourceType` 分组。
- 本地来源标题保持文件/片段信息；网页来源标题显示网页标题、域名、provider 和可点击 URL。
- 网页来源列表使用紧凑列表，不做大卡片；长 URL 省略，完整 URL 放 tooltip 或链接 title。
- 没有联网来源时不展示空区块，避免给用户制造“工具没工作”的误解。

前端实现顺序：

1. `settings-navigation.js` 增加 `{ id: 'web-search', label: '联网搜索' }`，放在 `chat-retrieval` 后。
2. `settings-view.vue` 引入 `WebSearchSettingsPanel`，在 `loadActiveItemData('web-search')` 读取联网配置。
3. `web-search-settings-api.js` 封装 `GET /api/web-search/settings`、`PUT /api/web-search/settings`、`POST /api/web-search/test`。
4. `web-search-settings.js` 维护 `enabled/provider/apiKeyConfigured/searchMode/maxResults/maxCallsPerTurn/timeoutMs/loading/saving/testing/error`。
5. `chat-settings-popover.vue` 增加 `useWebSearch`、`webSearchAvailable`、`webSearchStatusLabel` props 和 `update:useWebSearch` 事件。
6. `chat.js` 增加 `useWebSearch` 本轮状态，发送时把 `useWebSearch` 写入 `streamChatAnswer` payload；收到 `tool` SSE 时合并网页 sources。
7. `source-list.vue` 按 `sourceType` 分组渲染，本地来源继续支持“追问”，网页来源只提供打开链接。

## Persistence

聊天消息仍由 SQLite 保存事实源。第 35 阶段需要确保 assistant 消息保存本轮所有来源：

- 本地 RAG sources。
- Web tool sources。
- 工具调用错误摘要，必要时作为内部 metadata 保存，不一定展示为来源。

如果继续使用 `sources_json`，需要更新 `RagSourcesJsonCodec` 兼容新增字段。旧消息缺少 `sourceType` 时按 `LOCAL` 读取。

## Prompt Policy

系统提示词需要补充工具使用边界：

- 只有确实需要最新信息、公开网页证据或本地知识库没有覆盖时，才调用联网搜索。
- 不要把联网结果当成用户本地资料。
- 使用网页信息回答时，优先引用可核验 URL。
- 不要搜索或输出用户 API Key、Token、隐私数据。
- 搜索失败时说明联网搜索不可用，不要编造网页来源。

该提示只在本轮挂载联网工具时生效，避免未开启联网时模型看到无关规则。

## Security And Privacy

- API Key 不写入日志，不回传前端，不进入 SSE。
- 搜索 query 可能包含用户隐私；默认只记录截断和脱敏后的 query hash、provider、耗时和结果数。
- 搜索工具不访问本地文件系统。
- 不允许用户通过 query 控制任意请求头。
- 本阶段 Exa endpoint 固定在后端代码中，前端不接受用户自定义搜索 Base URL，减少 SSRF 面。
- 单轮工具调用次数和超时必须硬限制，防止模型循环调用造成费用失控。

## Logging

新增结构化日志：

```text
web_search_tool_called requestId={} conversationId={} provider={} resultCount={} durationMs={}
web_search_tool_failed requestId={} conversationId={} provider={} errorType={} durationMs={}
web_search_tool_skipped requestId={} reason={}
```

日志不得包含 API Key、完整 URL query 中的敏感参数或完整用户问题。

## Test Plan

后端测试：

- `useWebSearch=false/null` 时不挂 `WebSearchTools`，不读取搜索配置，不调用 provider。
- `useWebSearch=true` 且设置未启用时返回配置错误。
- `useWebSearch=true` 且 EXA API Key 未配置时返回配置错误。
- `useWebSearch=true` 且配置可用时，`SpringAiChatRuntime` 调用 `.tools(...)` 和 `.toolContext(...)`。
- `WebSearchTools.searchWeb` 能从 `ToolContext` 读取 `requestId` 和 `ToolExecutionCollector`。
- 单轮超过最大工具调用次数时拒绝继续搜索。
- provider 超时或失败时返回工具错误结果，SSE 不崩溃。
- `tool` SSE 事件能推送网页来源。
- assistant done/stopped/error 落库时包含网页来源。
- 旧 `sources_json` 缺少 `sourceType` 时按本地来源兼容读取。

前端测试：

- 未配置联网时聊天设置开关不可用或提示去设置。
- 开启联网发送请求时包含 `useWebSearch=true`。
- 关闭联网发送请求时包含 `useWebSearch=false` 或省略字段，后端不挂工具。
- 收到 `tool` 事件后当前 assistant message 增加网页来源。
- 刷新会话后网页来源仍能恢复展示。
- 本地来源和网页来源能在同一条 assistant 消息中区分展示。

集成验证：

```powershell
mvn -Dtest=ChatAgentRouterTests,SpringAiChatRuntimeTests test
npm --prefix cogniNote-agent-front run build
```

完整实现后再执行：

```powershell
mvn test
```

当前验证记录：

- `npm --prefix cogniNote-agent-front run build` 已通过；仍会出现项目既有的 Vite/Rolldown annotation、chunk size 和动态导入提示。
- Playwright 已覆盖联网搜索设置页空 Key 状态、粘贴 Key 后启用、配置说明弹窗、调用策略弹窗、夜间模式可读性和聊天设置弹层响应式布局。
- `mvn -Dtest=WebSearchSettingsServiceTests test` 在当前环境被 Maven Enforcer 阻止，原因是运行 JDK 不满足项目要求的 JDK 25；修复环境后再跑后端测试。

## Rollout

第 35 阶段建议按以下顺序实现：

1. 后端 DTO、设置 API 和 provider 配置保存。
2. `AiChatRuntime` / `SpringAiChatRuntime` 工具挂载能力。
3. `WebSearchTools`、`WebSearchClient` 和 `ToolExecutionCollector`。
4. SSE `tool` 事件和 sources 落库兼容。
5. 前端设置页和聊天开关。
6. 来源展示和会话恢复。
7. 安全收敛：SSRF 防护、日志脱敏、调用次数和超时硬限制。

## Acceptance Criteria

- 用户未开启联网时，模型没有可调用的联网工具。
- 用户开启联网但配置不可用时，后端给出明确错误，不发起模型工具调用。
- 用户开启联网且配置可用时，模型可以调用 `searchWeb`，并能基于工具结果生成回答。
- 网页来源在流式过程中可见，回答完成后可持久化恢复。
- 本地知识库来源和网页来源不混淆，前端能明确区分。
- 工具调用有次数、结果数和超时限制。
- 日志和 API 响应不泄露 API Key，搜索请求日志不记录完整用户 query。
- 旧会话、旧 sources JSON 和未升级前端请求保持兼容。
