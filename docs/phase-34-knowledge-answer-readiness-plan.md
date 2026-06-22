# 第 34 阶段计划：问答可用性诊断与资料变化/冲突提示

## Summary

第 34 阶段将知识库健康页从“可信认证”语义收敛为“问答可用性”诊断。系统不要求用户确认资料权威性，也不新增来源可信表；健康状态继续由 SQLite 文档事实、目录文件探针、Lucene 索引、Embedding 配置、知识图谱派生时间和维护任务即时计算。

本阶段重点回答三个问题：资料是否已同步进知识库、资料是否能被搜索命中、资料是否可能过期或互相冲突。

## Key Changes

- `GET /api/knowledge-health` 保持兼容，summary 新增 `answerReady`、`searchableDocumentCount`、`syncIssueCount`、`retrievalIssueCount`、`conflictIssueCount`、`graphStaleCount`。
- 健康问题新增 `GRAPH_STALE`、`DUPLICATE_DOCUMENT_CONTENT`、`POSSIBLE_VERSION_CONFLICT`，并允许 issue 附带少量 `examples` 供前端解释诊断结果。
- `GRAPH_STALE` 通过已生成图谱的 `generatedAt` 与当前资料同步/索引时间比较得出，只提示重建图谱，不阻塞基础问答。
- `DUPLICATE_DOCUMENT_CONTENT` 基于已解析资料的 `contentHash` 识别重复内容；`POSSIBLE_VERSION_CONFLICT` 基于保守的文件名归一化规则识别疑似多版本资料。
- 前端将“可信状态”入口改为“问答可用性”，总览按同步状态、检索状态、检索能力、资料风险和图谱新鲜度展示。
- 诊断抽屉按问答影响分组：需要同步、可能搜不到、检索能力降级、辅助图谱过期、可能干扰回答。

## Non-Goals

- 不新增 trusted、certified、sourceType、reviewCycle、lastVerifiedAt 等资料治理字段。
- 不提供批量确认来源可信、权威依据认证或复核周期配置。
- 不自动删除、合并或忽略重复/冲突资料；健康页只提示风险，最终处理由用户显式决定。
- 不引入 RAG 回归评测集；问答证据评估留给后续独立阶段。

## Test Plan

- 新增本地文件后，健康页显示需要同步，`syncIssueCount` 增加。
- 已解析未索引资料显示为检索问题，`searchableDocumentCount` 不包含该文档。
- Lucene 与 SQLite 统计不一致时返回 `INDEX_INCONSISTENT`，`answerReady=false`。
- Embedding 不可用时返回 `EMBEDDING_UNCONFIGURED`，前端展示检索能力降级。
- 两个启用目录下已解析文档 `contentHash` 相同时返回 `DUPLICATE_DOCUMENT_CONTENT`。
- 疑似同一文件名族但内容 hash 不同时返回 `POSSIBLE_VERSION_CONFLICT`。
- 已生成图谱早于当前资料更新时间时返回 `GRAPH_STALE`。
- 前端不出现来源可信、权威依据、复核周期或认证类文案。
