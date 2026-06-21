# 第 32 阶段计划：知识库可信状态 P0 加固与前端控制台

第 32 阶段在第 31 阶段健康诊断基础上补齐 P0 加固项：Lucene 一致性检查、Embedding 降级提示、目录删除后的维护记录清理，以及更明确的前端诊断与修复入口。本阶段仍不引入后台 watcher、自动同步或单文件任务队列，继续坚持“SQLite 是事实源，Lucene 是可重建索引，健康状态即时派生”的模型。

## 范围

- `GET /api/knowledge-health` 新增 Lucene reader 实际计数、Embedding 可用性和索引一致性字段。
- 新增健康问题 `INDEX_INCONSISTENT` 和 `EMBEDDING_UNCONFIGURED`，分别映射到“重建索引”和“配置向量模型”。
- `knowledge_folder_runs.status` 支持 `RUNNING`、`FAILED` 枚举值，接口查询保持向后兼容。
- 删除知识库目录时清理该目录 scope 下的 `knowledge_folder_runs`，且不再写入新的目录删除 run，避免删除后重新产生孤儿可信状态数据。
- 前端不新增一级路由，在知识库总览、目录管理列表和健康抽屉中嵌入“可信状态控制台”能力。

## 后端设计

健康服务读取启用目录的文档、chunk、文件系统探针和最近维护记录，并额外读取 Lucene `DirectoryReader` 的实际文档数与 chunk 数。文档数按 Lucene stored `documentId` 去重计算，chunk 数使用 reader `numDocs()`；这样可以发现索引目录被手动清空、部分文档漏写或删除失败造成的真实偏移。

一致性检查只比较“应在 Lucene 中存在的已索引文档和 chunk”与 reader 事实，不把停用目录计入预期。未归属旧文档如果仍然有 `indexedAt`，会作为兼容数据纳入预期，避免旧库升级后被误报。未索引文档继续通过 `UNINDEXED_DOCUMENTS` 暴露，不参与 Lucene 一致性判断。

Embedding 可用性来自 `KnowledgeStore.status().embeddingConfigured`。当知识库已有文档但没有可用 Embedding 时，健康总览返回 `EMBEDDING_UNCONFIGURED` 警告，提示 HYBRID/VECTOR 检索需要配置向量模型或切回关键词检索。

目录删除链路保持只删除应用内数据：Lucene 条目、SQLite 文档/chunks、知识图谱派生数据、目录记录和该目录维护记录。本地原始文件不删除，`ALL` scope 和其他目录的运行记录不受影响。

## 前端设计

总览页新增“可信状态控制台”区域，采用桌面运维工作台样式：状态文字、图标和计数并列展示，避免只靠颜色表达。控制台包含总体可信状态、启用目录数、文档数、失败/未索引/缺失/变化计数、Lucene 一致性、Embedding 状态和当前运行任务。

目录管理列表新增运行状态表达：最近维护结果、运行中进度条、禁用重复操作，以及按问题类型选择“重试同步”或“重建索引”。删除确认弹窗明确说明会清理应用内目录、文档、索引、图谱派生数据和维护记录，但不会删除本地原始文件。

健康抽屉升级为“诊断与修复”：解析失败、本地缺失、疑似变化只显示同步/重试；未索引和索引不一致显示重建索引；Embedding 不可用显示配置向量模型入口。

## 验收

- 删除目录后，按该目录 scope 查询 `knowledge_folder_runs` 返回空数组。
- 删除目录不影响本地原始文件、其他目录运行记录和全库运行记录。
- 手动删除 Lucene 中已索引文档后，健康接口返回 `INDEX_INCONSISTENT` 且 `summary.indexConsistent=false`。
- 未配置 Embedding 且知识库有文档时，健康接口返回 `EMBEDDING_UNCONFIGURED`。
- smoke test 覆盖导入小目录、搜索命中、健康一致、删除目录、搜索不命中、运行记录清理；可通过 `scripts/smoke-knowledge-health.ps1` 运行对应自动化用例。
