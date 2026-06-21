# 第 31 阶段计划：知识库健康诊断与维护闭环

> 当前实现已在第 33 阶段把 `knowledge_folder_runs` 升级为维护任务队列与历史记录的统一事实源。本文保留第 31 阶段的原始设计背景；查看当前队列、SSE 和取消规则请以 [第 33 阶段维护队列计划](phase-33-knowledge-maintenance-queue-plan.md) 为准。

## Summary

第 31 阶段优先完善知识库维护体验，让用户能判断“当前知识库是否可信”。现有知识库工作台已经包含资料管理、检索测试和知识图谱三个面板，后端也已经保存文档解析状态、目录统计、索引时间和失败摘要。本阶段不新增新的资料来源，也不自动在后台持续监听文件，而是在现有 SQLite 事实源和 Lucene 可重建索引之上补齐健康诊断、运行记录、失败聚合和修复入口。

## Implementation Status

已完成第一版落地：

- 后端新增 `knowledge_folder_runs` 表、Mapper、Repository 和 `KnowledgeFolderRunService`，导入、同步、目录重建、启停、删除和全库索引重建完成后都会写入运行记录。
- 后端新增 `KnowledgeHealthService` 和 `KnowledgeHealthController`，暴露 `GET /api/knowledge-health`、`GET /api/knowledge-health/folders/{id}`、`GET /api/knowledge-health/runs`。
- 前端新增 `knowledge-health-api.js`、`knowledge-health` Pinia store 和 `knowledge-health-drawer.vue`，资料管理页展示健康概览、目录健康 badge、问题数量、最近维护和问题抽屉。
- 资料管理页已拆成“知识库总览”和“目录管理”两个面板：总览页保留健康摘要、最近目录和轻量入口；目录管理面板提供模糊搜索、启停/问题筛选、分页查找、目录文档展开和批量维护操作。
- 导入目录已抽取为 `knowledge-folder-import-dialog.vue` 弹窗；“查看问题”先弹出有问题目录列表，再进入目录问题详情抽屉。
- 停用目录被视为用户主动维护状态，返回 `DISABLED` 且不计入问题数量、问题目录弹窗或全库 `WARNING/ERROR`。
- Element Plus 在应用根部配置中文 locale，目录管理分页控件显示中文文案。
- `docs/api-reference.md`、`README.md`、`docs/cogninote-agent-design.md` 和前端 README 已同步健康诊断、运行记录和维护边界。

已验证：

- `mvn -q test` 通过，需使用 JDK 25。
- `npm --prefix cogniNote-agent-front run build` 通过；构建仍有既有 `@vueuse/core` pure annotation 与 chunk size 警告，不影响产物。
- 本地 `GET /api/knowledge-health`、`GET /api/knowledge-health/runs` 已手动访问成功。

未纳入第一版：

- 不做后台 watcher、自动同步或周期性向量化。
- 不做重建进度流和失败一键重试，这两个适合作为下一阶段继续推进。
- 不把图谱过期和 Embedding 未配置纳入阻断性健康错误。

目标是把用户现在需要自己推断的状态变成清晰答案：

- 哪些目录正常、哪些目录有风险。
- 哪些文件解析失败、为什么失败、是否可重试。
- 哪些文档已经解析但没有进入 Lucene 索引。
- 本地目录是否已经不可访问，或本地文件是否可能已变化。
- 最近一次导入、同步、重建到底做了什么。
- 出问题后应该点“同步”“重建索引”“查看失败文件”还是重新导入。

本阶段第一版不追求全自动同步。AnythingLLM 的自动文档同步仍以预览形态提示 embedder 成本和本地数据库/向量库损坏风险；对 CogniNote 这种本地桌面应用，更稳妥的第一步是可解释、可恢复、用户显式触发的健康闭环。Khoj、LlamaIndex 和 LangChain 的资料管理思路也都强调本地/增量索引、稳定的 document id、refresh/update/delete 和 record manager。CogniNote 现有 `documentId + contentHash + indexedAt` 模型已经具备这个基础。

## Goals

- 在知识库工作台新增“健康概览”，展示全库和目录级健康状态。
- 为知识库目录提供诊断接口，返回可执行的问题清单和修复动作。
- 按目录和全库聚合解析失败、索引缺失、本地路径不可访问和可能过期等状态。
- 增加知识库运行记录，保存最近一次导入、同步、重建索引、启停和删除的统计与失败摘要。
- 前端在资料管理页中显示健康状态、最近操作、失败文件抽屉和修复按钮。
- 所有状态继续以 SQLite 为事实来源，Lucene 仍是可重建索引。
- 不破坏现有 `/api/knowledge-folders`、`/api/index`、`/api/search`、`/api/knowledge-graphs` 接口行为。

## Non-goals

- 不做后台文件系统 watcher。
- 不自动周期性调用 Embedding 模型重新向量化。
- 不自动重建知识图谱；图谱仍由用户显式触发。
- 不引入 Qdrant、外部任务队列或新的后台服务。
- 不新增 OCR、HTML、Obsidian 双链或其它文件类型。
- 不把健康状态做成强一致实时监控；第一版以“打开页面刷新 + 用户手动诊断/同步”为主。
- 不删除用户本机原始文件。

## 现有基础

当前代码已经有一半数据可直接复用：

- `documents.status` 标记 `PARSED` / `FAILED`。
- `documents.indexed_at` 为空表示已解析但未成功写入 Lucene。
- `documents.file_size`、`last_modified`、`content_hash` 可判断记录与当前本地文件是否一致。
- `knowledge_folders.last_ingested_at`、`last_indexed_at` 可展示目录级最近导入/索引时间。
- `KnowledgeFolderSummaryRow` 已经聚合 `document_count`、`parsed_count`、`failed_count`、`chunk_count`、`unindexed_count`。
- `DocumentIngestionService` 已区分主动导入失败记录和重建时保护旧解析结果。
- `KnowledgeFolderService.syncFolder` 已支持新增、修改、缺失索引和已删除文件的同步。
- `KnowledgeFolderService.rebuildFolder` 已支持目录级重新扫描和 Lucene 重建。
- `/api/index/status` 已有全库索引状态。

因此本阶段不需要重写导入管线。核心是把这些状态系统化、解释清楚、保存最近操作，并给用户明确恢复路径。

## 核心设计

本阶段新增两个概念：

1. **健康快照**：按全库或目录即时计算，不长期缓存。它回答“现在看起来是否可信”。
2. **运行记录**：每次导入、同步、重建、启停等操作完成后写入 SQLite。它回答“刚才发生了什么”。

不要把健康快照也落成长期事实。健康状态由 documents、knowledge_folders、Lucene 统计和文件系统探针派生，保存副本容易腐烂。运行记录则是历史事件，必须持久化，便于前端展示最近操作和排查问题。

## 健康状态模型

健康等级建议：

| 状态 | 含义 | 用户感知 |
| --- | --- | --- |
| `HEALTHY` | 没有解析失败、索引缺失、路径不可访问或疑似过期。 | 可放心用于搜索和 RAG。 |
| `WARNING` | 有非致命问题，例如部分文件失败或少量文档疑似变化。 | 可以使用，但结果可能不完整。 |
| `ERROR` | 目录不可访问、大量解析失败、已解析文档大面积未索引。 | 搜索/RAG 可信度低，需要修复。 |
| `DISABLED` | 目录已停用，不参与搜索/RAG。 | 用户主动排除该目录；数据保留，但当前不可检索。 |
| `EMPTY` | 目录没有可用文档或全库为空。 | 需要导入资料。 |

问题类型建议：

| code | 严重性 | 触发条件 | 推荐动作 |
| --- | --- | --- | --- |
| `FOLDER_NOT_FOUND` | `ERROR` | `folder_path` 当前不是可读目录。 | 重新选择目录或删除目录记录。 |
| `NO_DOCUMENTS` | `WARNING` | 目录启用但没有文档。 | 检查目录或递归开关。 |
| `PARSE_FAILED` | `WARNING` | 存在 `status=FAILED` 文档。 | 查看失败文件、修复源文件后同步。 |
| `UNINDEXED_DOCUMENTS` | `ERROR` | 存在 `status=PARSED AND indexed_at IS NULL`。 | 重建索引或同步目录。 |
| `STALE_LOCAL_FILES` | `WARNING` | 记录的 file size / mtime 与当前文件不同。 | 同步目录。 |
| `MISSING_LOCAL_FILES` | `WARNING` | documents 中的 source_path 不存在。 | 同步目录清理应用内记录。 |
| `DISABLED_FOLDER` | `INFO` | 目录 `enabled=false`。 | 仅展示状态；不进入问题数量或全库告警。 |
| `EMBEDDING_UNCONFIGURED` | `WARNING` | active Embedding 未配置，且用户默认使用 HYBRID/VECTOR。 | 配置 Embedding 或使用关键词检索。 |
| `GRAPH_STALE` | `INFO` | 图谱生成时间早于目录最近导入/重建时间。 | 重新生成图谱。 |

第一版只把启用目录中的前六类问题纳入问题清单；`DISABLED_FOLDER` 仅保留为状态语义，`EMBEDDING_UNCONFIGURED` 和 `GRAPH_STALE` 可作为健康概览中的提示项，不阻断资料管理。

## 数据模型

新增表 `knowledge_folder_runs`，记录目录级维护动作。它不是任务队列表，只保存已发生操作的结果。

```sql
CREATE TABLE IF NOT EXISTS knowledge_folder_runs (
    id TEXT PRIMARY KEY,
    scope_type TEXT NOT NULL,
    scope_id TEXT,
    operation TEXT NOT NULL,
    status TEXT NOT NULL,
    scanned_count INTEGER NOT NULL DEFAULT 0,
    parsed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    indexed_document_count INTEGER NOT NULL DEFAULT 0,
    indexed_chunk_count INTEGER NOT NULL DEFAULT 0,
    failed_document_count INTEGER NOT NULL DEFAULT 0,
    failures_json TEXT,
    started_at INTEGER NOT NULL,
    completed_at INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at INTEGER NOT NULL
);
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_knowledge_folder_runs_scope
    ON knowledge_folder_runs(scope_type, scope_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_folder_runs_operation
    ON knowledge_folder_runs(operation, created_at DESC);
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `scope_type` | `ALL`、`KNOWLEDGE_FOLDER` 或 `UNASSIGNED`。 |
| `scope_id` | 目录 ID；全库或未归属文档可为空。 |
| `operation` | `IMPORT`、`SYNC`、`REBUILD_INDEX`、`ENABLE`、`DISABLE`、`DELETE`。 |
| `status` | `COMPLETED`、`COMPLETED_WITH_WARNINGS`、`FAILED`。 |
| `failures_json` | 复用 `IngestFailureResponse` 形状，保留最近一次失败摘要。 |

为什么不新增 `document_health` 表：文档健康可由 `documents + 文件系统探针` 即时计算。保存一份健康表会引入同步问题，例如用户在应用外修改文件后，健康表立即过期。

## 后端模块

新增包和类：

```text
service.knowledge.KnowledgeHealthService
repository.knowledge.KnowledgeFolderRunRepository
mapper.knowledge.KnowledgeFolderRunMapper
controller.knowledge.KnowledgeHealthController
domain.knowledge.KnowledgeHealthStatus
domain.knowledge.KnowledgeHealthIssueCode
dto.knowledge.KnowledgeHealthResponse
dto.knowledge.KnowledgeFolderHealthResponse
dto.knowledge.KnowledgeHealthIssueResponse
dto.knowledge.KnowledgeFolderRunResponse
```

职责：

| 类 | 职责 |
| --- | --- |
| `KnowledgeHealthService` | 读取目录、文档、索引统计和文件系统，计算全库/目录健康快照。 |
| `KnowledgeFolderRunRepository` | 保存和查询最近维护操作记录。 |
| `KnowledgeHealthController` | 暴露健康快照和运行记录 API。 |
| `KnowledgeFolderService` | 在现有 import/sync/rebuild/enable/disable/delete 操作完成后写入 run 记录。 |

文件系统探针要保守：

- 只检查已导入目录和 documents 中记录的具体文件。
- 单个文件不可读不抛出全局异常，转成健康 issue。
- 大目录诊断需要限制扫描成本。第一版只检查数据库中已有文档的路径和元数据，不全量 walk 未导入的新文件；新文件发现仍由“同步目录”完成。
- 停用目录不继续展开解析、索引和本地文件探针问题，避免旧文档状态污染全库健康判断。

## API 设计

新增接口：

```text
GET /api/knowledge-health
GET /api/knowledge-health/folders/{id}
GET /api/knowledge-health/runs?scopeType=KNOWLEDGE_FOLDER&scopeId=...&limit=20
```

### 全库健康

```http
GET /api/knowledge-health
```

响应：

```json
{
  "status": "WARNING",
  "summary": {
    "folderCount": 3,
    "enabledFolderCount": 2,
    "documentCount": 128,
    "parsedCount": 120,
    "failedCount": 5,
    "unindexedCount": 3,
    "missingLocalFileCount": 1,
    "staleLocalFileCount": 4,
    "chunkCount": 2400,
    "lastIngestedAt": 1780000000000,
    "lastIndexedAt": 1780000005000
  },
  "issues": [
    {
      "code": "UNINDEXED_DOCUMENTS",
      "severity": "ERROR",
      "message": "有 3 个已解析文档尚未进入索引，搜索和 RAG 可能缺失内容。",
      "action": "REBUILD_INDEX",
      "scopeType": "ALL",
      "scopeId": null,
      "count": 3
    }
  ],
  "folders": [
    {
      "id": "folder-id",
      "displayName": "project-docs",
      "folderPath": "D:/notes/project-docs",
      "status": "WARNING",
      "documentCount": 42,
      "failedCount": 2,
      "unindexedCount": 1,
      "missingLocalFileCount": 0,
      "staleLocalFileCount": 3,
      "lastRun": {
        "operation": "SYNC",
        "status": "COMPLETED_WITH_WARNINGS",
        "completedAt": 1780000005000,
        "failedCount": 2
      }
    }
  ]
}
```

### 目录健康

```http
GET /api/knowledge-health/folders/{id}
```

响应包含目录摘要、问题列表、失败文件和疑似变化文件：

```json
{
  "folderId": "folder-id",
  "status": "WARNING",
  "issues": [],
  "failedDocuments": [
    {
      "documentId": "doc-id",
      "sourcePath": "D:/notes/broken.pdf",
      "fileName": "broken.pdf",
      "message": "Parsed document contains no usable text",
      "updatedAt": 1780000000000
    }
  ],
  "unindexedDocuments": [],
  "missingLocalFiles": [],
  "staleLocalFiles": []
}
```

### 运行记录

```http
GET /api/knowledge-health/runs?scopeType=KNOWLEDGE_FOLDER&scopeId=folder-id&limit=20
```

返回最近维护操作：

```json
[
  {
    "id": "run-id",
    "operation": "SYNC",
    "status": "COMPLETED_WITH_WARNINGS",
    "scannedCount": 42,
    "parsedCount": 3,
    "skippedCount": 37,
    "failedCount": 2,
    "indexedDocumentCount": 3,
    "indexedChunkCount": 80,
    "completedAt": 1780000005000,
    "durationMs": 1200
  }
]
```

## 前端设计

知识库工作台保持现有三个 tab，不新增一级导航。资料管理 tab 拆成“知识库总览”和“目录管理”两个面板：

1. **知识库总览**
   - 展示全库状态、文档数、失败数、未索引数、疑似变化数、最近同步时间。
   - 状态颜色使用现有语义 token：success / warning / danger / muted，不新增主题色。
   - 提供导入目录弹窗、刷新诊断、查看问题和重建全部索引入口。
   - 只展示最近目录和轻量目录管理入口，避免总览页承载完整维护列表。

2. **目录管理列表**
   - 支持按名称、路径、启停状态、健康问题做本地筛选和模糊搜索。
   - 列表分页显示，分页文案使用 Element Plus 中文 locale。
   - 每个目录行显示：状态 badge、文档数、chunks、未索引、疑似变化、最近操作。
   - 操作区：同步、重建索引、启用/停用、查看问题。
   - 若目录不可访问，主操作变为“重新选择目录”或“删除记录”（第一版可以先只给删除和提示，重新选择目录另排）。
   - 停用目录显示 `DISABLED`，同步和重建索引入口降级或禁用，不显示问题按钮。

3. **问题抽屉**
   - 点击总览“查看问题”先弹出存在问题的目录列表；点击目录“问题详情”后打开右侧抽屉。
   - 按问题类型分组：解析失败、未索引、缺失文件、疑似变化。
   - 每条展示文件名、路径、原因、更新时间。
   - 操作按钮：复制路径、同步目录、重建索引、打开文档详情。

视觉和交互原则：

- 不用大面积营销式卡片。知识库页是工作台，信息要紧凑、可扫读。
- 问题状态不能只靠颜色，必须有文字和图标。
- 修复按钮只展示当前问题真正相关的动作，避免用户面对一排“同步/重建/删除”不知道点哪个。
- 运行记录默认折叠为“最近一次操作”，需要时展开历史。
- 失败消息展示后端原始错误摘要，但要补一层友好说明，例如“这个 PDF 可能没有文本层，当前版本不做 OCR”。
- 总览页里的目录管理入口应是轻量文本链接，不和导入目录、重建索引这类真实操作抢主按钮权重。

## 修复动作映射

| 问题 | 主按钮 | 次按钮 |
| --- | --- | --- |
| `PARSE_FAILED` | 同步目录 | 复制失败文件路径 |
| `UNINDEXED_DOCUMENTS` | 重建目录索引 | 重建全部索引 |
| `FOLDER_NOT_FOUND` | 删除目录记录 | 复制目录路径 |
| `STALE_LOCAL_FILES` | 同步目录 | 查看变化文件 |
| `MISSING_LOCAL_FILES` | 同步目录 | 查看缺失文件 |
| `DISABLED_FOLDER` | 启用目录 | 删除目录；仅在目录管理列表中展示，不进入问题弹窗 |
| `NO_DOCUMENTS` | 同步目录 | 检查递归设置 |

## 与现有功能关系

- `POST /api/knowledge-folders/{id}/sync` 继续是目录同步入口，本阶段只在返回后写运行记录，并让前端展示更详细的结果。
- `POST /api/knowledge-folders/{id}/rebuild` 继续是目录重建入口，写运行记录。
- `POST /api/index/rebuild` 继续是全量索引重建入口，可补一条 `scope_type=ALL` 的运行记录。
- 健康诊断不自动修改数据。它只读 SQLite、Lucene 统计和文件系统。
- 图谱不会自动重建；若目录有新导入或同步，健康概览可以提示“图谱可能已过期”，但不把它视为搜索/RAG 错误。
- 停用目录仍显示健康，但标记为 `DISABLED`，不参与全库可检索健康评分、问题数量或问题目录弹窗。

## 实施步骤

### Step 1：后端运行记录

- 新增 `knowledge_folder_runs` 表和 Mapper。
- 在 `KnowledgeFolderService.importFolder/syncFolder/rebuildFolder/setEnabled/deleteFolder` 写入运行记录。
- 在 `IndexService.rebuild` 写入全库 `REBUILD_INDEX` 记录。
- 增加 Repository 测试覆盖运行记录写入和查询。

### Step 2：健康快照 API

- 实现 `KnowledgeHealthService`。
- 复用现有目录 summary，补文件系统探针和文档级问题列表。
- 暴露 `/api/knowledge-health`、`/api/knowledge-health/folders/{id}`、`/api/knowledge-health/runs`。
- Controller 保持薄层，只做参数校验和统一响应。

### Step 3：前端健康概览

- 新增 `knowledge-health-api.js` 和 Pinia store。
- 在资料管理 tab 顶部显示健康概览。
- 目录列表拆到独立目录管理面板，增加健康 badge、最近操作、问题数量、模糊搜索、筛选和分页。
- 保持无目录、空目录、加载、错误状态完整。

### Step 4：问题抽屉与修复入口

- 新增 `knowledge-health-drawer.vue`。
- 新增或复用问题目录弹窗，先列出有问题的目录，再进入目录级详情。
- 按 issue 类型分组展示文档。
- 接入同步、重建、启用/停用、复制路径等现有操作。
- 操作完成后刷新健康快照和目录列表。

### Step 5：文档与验收

- 更新 `docs/api-reference.md` 的知识库健康 API。
- 更新 README 开发状态。
- 增加后端测试和前端 build 验证。

## Test Plan

- `mvn test`
- `npm --prefix cogniNote-agent-front run build`
- 空知识库返回 `EMPTY`。
- 全部文档解析并索引成功时返回 `HEALTHY`。
- 存在 `FAILED` 文档时返回 `WARNING` 和 `PARSE_FAILED` issue。
- 存在 `PARSED AND indexed_at IS NULL` 时返回 `ERROR` 和 `UNINDEXED_DOCUMENTS` issue。
- 目录路径不存在时返回 `FOLDER_NOT_FOUND`。
- 文档源文件不存在时返回 `MISSING_LOCAL_FILES`。
- 文档 file size 或 mtime 与记录不一致时返回 `STALE_LOCAL_FILES`。
- 停用目录返回 `DISABLED`，但不导致全库 `ERROR/WARNING`，也不显示为问题数量。
- 导入、同步、重建、启停、删除后都能查询到运行记录。
- 同步修复缺失文件后健康快照更新。
- 重建索引后 `unindexedCount` 归零。
- 前端资料管理页在健康状态切换时无布局跳动，深色/浅色主题下状态颜色可读。
- 问题抽屉中的操作完成后自动刷新目录列表和健康概览。
- 目录管理列表可按目录名、路径和状态模糊搜索，分页控件显示中文文案。

## Risks

- **文件系统探针过慢**：如果用户导入数万文件，逐个读取 hash 会很重。第一版只比较存在性、mtime 和 size，不重新计算 content hash；真正解析变化仍交给同步目录。
- **健康状态误报**：mtime 在部分文件系统上可能被工具修改。健康提示只说“疑似变化”，不直接修改数据。
- **用户混淆同步和重建**：UI 必须把动作说明写短：同步用于新增/修改/删除文件，重建索引用于修复搜索缺失或索引损坏。
- **运行记录无限增长**：第一版可保留全部记录；后续可以增加“每个 scope 只保留最近 100 条”的清理策略。
- **图谱过期提示牵连太多**：图谱健康只做 INFO，不阻断资料管理主流程。

## References

- [AnythingLLM Automatic document sync](https://docs.anythingllm.com/beta-preview/active-features/live-document-sync)：自动同步会带来 embedder 成本和本地数据库/向量库风险，适合作为后续能力，不宜作为第一版默认行为。
- [Khoj Upload your data](https://docs.khoj.dev/data-sources/share_your_data/)：桌面应用适合处理大量本地文档并保持同步。
- [LlamaIndex Document Management](https://developers.llamaindex.ai/python/framework/module_guides/indexing/document_management/)：索引管理围绕 insertion、deletion、update、refresh 和 document id。
- [LangChain indexing API](https://reference.langchain.com/python/langchain-core/indexing/api/index)：record manager 用于跟踪哪些文档已更新、删除或跳过，cleanup 模式需要明确数据范围。

## Assumptions

- CogniNote 继续坚持本地优先，SQLite 是业务事实来源，Lucene 是可重建索引。
- 用户更需要明确可信状态和修复路径，而不是默认后台自动改动知识库。
- 第一版面向个人知识库规模，目录诊断在秒级完成即可接受。
- 后续若要做自动同步，应在本阶段健康模型稳定后再加，并提供明确开关、频率和成本提示。
