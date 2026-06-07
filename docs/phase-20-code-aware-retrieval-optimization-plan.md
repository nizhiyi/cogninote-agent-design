# 第二十阶段计划：代码友好的知识库检索准确率优化

## Summary

第 20 阶段把知识库检索从“基础 BM25 + Vector 混合检索”升级为更适合中文正文、代码笔记和流程图笔记的检索方案。核心原则是：SQLite 中的 chunk 继续保存可展示、可喂给模型的原文；Lucene 可以使用派生索引文本来提升召回；Embedding 调用在内部区分文档向量化和查询向量化。

本阶段不引入 reranker，不更换向量库，不改变 REST API，不改变 SQLite schema。

## Key Changes

- `TextChunker` 保护 Markdown fenced code block、Mermaid、PlantUML 和常见代码块，不再压缩代码块内部缩进、tab 和换行。
- chunk 切分按普通文本块和 protected block 分层处理，普通正文保留重叠窗口，代码/流程图块尽量不切断；超大 protected block 拆分时补齐 fence 标记。
- Lucene 使用 `SmartChineseAnalyzer` 处理正文，使用 `StandardAnalyzer` 处理代码/标识符字段。
- 新增派生索引文本生成器，从代码块和流程图中提取语言名、类名、函数名、变量名、路径、异常名、图类型和节点文本。
- Lucene 存储字段仍不作为展示事实来源；RAG 展示和模型上下文仍从 SQLite 的 `chunks.content` 回读原文。
- HYBRID 候选集改为 `max(topK * 8, 60)`。
- 混合排序从 min-max 归一化加权改为加权 RRF，默认 `bm25Weight=0.45`、`vectorWeight=0.55`、`rrfK=60`。
- `EmbeddingGateway` / `AiEmbeddingRuntime` 拆分为 `embedDocuments` 和 `embedQuery`。
- DashScope Embedding 通过 Spring AI Alibaba 的 `DashScopeEmbeddingOptions.textType` 分别传递 `document` / `query`，并按完整 key 缓存两个模型实例。
- OpenAI-compatible 继续使用 Spring AI OpenAI runtime 的标准 `/embeddings` 请求，不发送非标准 `text_type` 字段。

## Public Interfaces

REST API 保持兼容：

- `POST /api/search`
- `GET /api/index/status`
- `POST /api/index/rebuild`
- 聊天 SSE 与 RAG sources 结构

新增配置项：

```yaml
app:
  search:
    bm25-k1: 1.2
    bm25-b: 0.65
    hybrid-candidate-multiplier: 8
    hybrid-min-candidates: 60
    rrf-k: 60
```

`COGNINOTE_BM25_WEIGHT` 和 `COGNINOTE_VECTOR_WEIGHT` 保留，但语义从 min-max 归一化加权变为 RRF 加权。

## Rebuild Rules

升级到本阶段后应全量重建 Lucene 索引。以下变化也需要重建索引：

- Analyzer 变化
- BM25 参数变化
- 派生索引文本策略变化
- Embedding 模型变化
- Embedding 维度变化

如果旧版本导入时已经把代码块缩进清洗丢失，只重建 Lucene 无法恢复格式，需要重新导入原始文件。

## Test Plan

- `mvn test`
- 中文正文查询能命中中文笔记。
- 代码标识符查询能命中 `ChatAgentRouter`、`snake_case`、`fooBar` 等代码 chunk。
- Mermaid / PlantUML / 流程图节点文本可被搜索命中。
- Java / SQL / Shell fenced code block 的缩进、换行和 fence 标记不被清洗破坏。
- HYBRID 使用扩大后的候选集和 RRF 排序。
- DashScope 分别使用 `document` / `query` text type。
- OpenAI-compatible 保持标准 Spring AI OpenAI `/embeddings` 调用。

## Assumptions

- 中文知识库是主要优化目标，但代码笔记和流程图笔记是一等场景。
- `text-embedding-v4 + 1024` 继续作为默认推荐配置。
- 本阶段不做 reranker、外部向量库、SQLite schema 变更或前端 API 破坏性改动。
