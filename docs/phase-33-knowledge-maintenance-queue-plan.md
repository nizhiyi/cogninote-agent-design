# 第 33 阶段计划：知识库维护任务队列与统一状态模型

第 33 阶段把知识库维护动作从“前端局部 busy 状态 + 完成后运行记录”收敛为统一任务队列。`knowledge_folder_runs` 同时承担本地 FIFO 队列恢复事实源和完成后的维护历史，健康页、目录管理、问题抽屉和检索状态都以维护队列快照为准，不再各自推断“当前任务”。

## 范围

- 新增 `KnowledgeMaintenanceQueueService`，统一入队、串行调度、执行、失败收敛和应用启动清理。
- `knowledge_folder_runs.status` 扩展为 `QUEUED`、`RUNNING`、`CANCELLING`、`CANCELLED`、`COMPLETED`、`COMPLETED_WITH_WARNINGS`、`FAILED`。
- `knowledge_folder_runs` 新增 `phase`、`progress_current`、`progress_total`、`current_item`、`queued_at`、`updated_at`，并允许 `started_at`、`completed_at`、`duration_ms` 为空。
- 导入目录、同步目录、重建目录索引、重建全部索引、启用、停用和删除目录全部接入维护队列。
- 新增 `/api/knowledge-maintenance/runs/**` 维护任务 API，并使用 SSE 推送任务和队列状态。
- 前端新增 `knowledge-maintenance` Pinia store，作为维护任务唯一状态源。
- 维护动作执行完成后统一刷新维护队列、目录列表、健康快照和索引状态，避免可信状态页与资料管理页数据分叉。

## 后端设计

队列采用单机 FIFO，任意时刻只运行一个知识库维护任务。这样可以避免 SQLite、Lucene 和知识图谱派生数据在多个长操作中并发写入，降低本地桌面应用里最容易出现的锁等待和状态交叉风险。

应用启动时会清理遗留任务：旧 `QUEUED` 任务标记为 `CANCELLED`，旧 `RUNNING` 或 `CANCELLING` 任务标记为 `FAILED`。清理后用户可以重新发起维护动作，避免重启后界面一直显示卡住的运行任务。

取消规则只允许取消等待中的 `QUEUED` 任务。正在运行的任务已经开始修改 SQLite、Lucene 或派生数据，贸然中断会让用户误以为操作可以随时安全回滚；因此运行任务会执行到安全完成点，前端只展示阶段、当前项和耗时提示。

进度字段保留为任务状态契约，但当前不渲染精确百分比。导入、同步和重建涉及文件扫描、解析、索引写入和派生清理，不同任务很难共享真实逐项进度；界面改为展示任务阶段、当前目录或路径，以及“文件较多时可能需要更长时间”的提示，避免制造虚假的精确感。

删除目录仍只删除应用内数据，不删除用户本机原始文件。删除任务执行时会清理该目录 scope 下的维护记录，因此完成后不会留下该目录的孤儿可信状态数据；`ALL` scope 和其他目录运行记录不受影响。

## API

维护任务 API：

```text
POST /api/knowledge-maintenance/runs/rebuild-index
POST /api/knowledge-maintenance/runs/import-folder
POST /api/knowledge-maintenance/runs/folders/{id}/sync
POST /api/knowledge-maintenance/runs/folders/{id}/rebuild
POST /api/knowledge-maintenance/runs/folders/{id}/enabled
POST /api/knowledge-maintenance/runs/folders/{id}/delete
GET  /api/knowledge-maintenance/runs/queue
GET  /api/knowledge-maintenance/runs/{runId}
GET  /api/knowledge-maintenance/runs/{runId}/events
POST /api/knowledge-maintenance/runs/{runId}/cancel
```

SSE 事件：

```text
maintenance-run-snapshot
maintenance-run-queued
maintenance-run-started
maintenance-run-progress
maintenance-run-cancelling
maintenance-run-cancelled
maintenance-run-completed
maintenance-run-failed
maintenance-queue-updated
```

`GET /api/knowledge-health` 增加 `currentRuns`、`queuedRuns`、`latestRun`，并在 `summary` 中返回 `runningRunCount` 和 `queuedRunCount`。`GET /api/knowledge-health/runs/page` 为维护记录弹窗提供分页数据，旧 `/runs` 列表接口保留兼容。

## 前端设计

可信状态页展示“维护队列”：当前运行任务置顶，等待任务按队列顺序展示。等待任务可取消；运行任务不提供取消入口，只展示运行中状态、阶段、当前目录或路径，以及长任务提示。

资料总览、目录管理、问题抽屉和可信状态页的维护按钮统一调用 `knowledge-maintenance` store。任务完成后，store 会统一刷新：

- 维护队列；
- 知识库目录列表；
- 健康快照和已打开的目录健康详情；
- Lucene 索引状态。

导入目录、重建全部索引、同步目录、重建目录索引、停用目录和删除目录都会先显示结构化二次确认弹窗。确认内容按摘要、影响、目录路径分段展示；删除确认明确说明只删除应用内目录、文档、chunks、索引、图谱派生数据和维护记录，不删除本地原始文件。

导入目录、重建全部索引和重建目录索引完成后，会进入需要用户确认的完成提示队列。完成提示按任务逐条展示扫描、解析、跳过、失败、索引文档和索引 chunk 等结果；用户点击“知道了”或“查看维护记录”后才关闭当前提示。若 SSE 终态事件丢失，前端会通过 `GET /api/knowledge-maintenance/runs/{runId}` 兜底查询被跟踪任务的终态。

维护记录不再堆在页面下方，而是通过“查看维护记录”弹窗分页加载。这样可信状态首页保持可扫读，历史数据按需查询。

## SQLite 与连接约束

维护任务串行执行，但健康页、队列页和 SSE 快照会并发读取 SQLite。第 33 阶段将 SQLite Hikari 连接池调整为 4 个连接，启用 WAL 和 `busy_timeout=30000`，并移除知识库目录长维护方法外层事务。文档和 chunk 的写入仍由短事务保护，避免长时间占住唯一连接导致页面、SSE 或健康接口等待。

## 验收

- 连续触发多个维护动作后，数据库中最多只有一个 `RUNNING`，其余为 `QUEUED`。
- 当前任务完成后，下一条等待任务自动开始。
- `QUEUED` 任务可以取消并变为 `CANCELLED`；`RUNNING` 任务取消接口返回业务错误。
- 任务失败后写入 `FAILED` 和 `errorMessage`，队列继续执行下一条。
- 应用重启后遗留 `QUEUED` 被取消，遗留 `RUNNING/CANCELLING` 被标记失败。
- SSE 能收到任务快照、开始、进度、终态和队列变化事件。
- 删除目录后，该目录 scope 下没有维护记录残留，本地原始文件不受影响。
- 前端任务完成后自动刷新可信状态、资料总览、目录列表和索引状态。
- 维护记录弹窗能分页查询历史记录。

