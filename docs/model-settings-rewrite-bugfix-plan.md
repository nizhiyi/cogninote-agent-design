# 模型设置页重写与 CRUD 接口方案

## Summary

重写“设置 -> 模型”页的数据流和接口契约。前端不再靠复杂本地同步推导表单内容，而是每次进入模型页、切换 Chat/Embedding、添加、修改、删除、启用后，都从后端拿一份明确的“模型设置快照”。

本修复聚焦模型设置页的数据回显与 CRUD 稳定性，不改 RAG 调用流程、不改数据库 schema。

最终落地时，模型设置页右侧表单只保留 `model-config` store 的 `form` 作为单一状态源；`settings-view` 切到“模型”页签时显式调用 `enterModelSettings()` 加载 `CHAT` 快照，`model-config-view` 挂载时只做 `initializeEditor()` 兜底。这样可以避免“Active 卡片和列表为空、右侧表单回退到默认空白”的加载时序问题，也避免“列表有数据但表单没回显”的双表单状态问题。

## Backend API Changes

新增设置页专用接口，旧 `/api/model-configs` CRUD 暂时保留兼容，但新前端只使用新接口：

```text
GET    /api/model-configs/settings?role=CHAT|EMBEDDING
POST   /api/model-configs/settings/configs
PUT    /api/model-configs/settings/configs/{id}
DELETE /api/model-configs/settings/configs/{id}
POST   /api/model-configs/settings/configs/{id}/activate
```

新增响应 DTO：`ModelConfigSettingsResponse`。

```json
{
  "active": {
    "chat": {},
    "embedding": {}
  },
  "role": "CHAT",
  "configs": [],
  "selectedConfig": {}
}
```

- `active.chat`：当前启用的对话模型。
- `active.embedding`：当前启用的 Embedding 模型。
- `configs`：当前 role 的配置列表。
- `selectedConfig`：当前 role 默认应展示的配置，优先为启用配置，其次为第一条配置。

新增请求 DTO：`ModelConfigUpsertRequest`。

```json
{
  "role": "CHAT",
  "provider": "OPENAI_COMPATIBLE",
  "displayName": "SiliconFlow",
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "",
  "modelName": "model-id",
  "temperature": 0.7,
  "defaultTopK": 8,
  "embeddingDimensions": 1024
}
```

字段规则：

- `CHAT` 使用 `temperature/defaultTopK`。
- `EMBEDDING` 使用 `embeddingDimensions`。
- `apiKey` 为空时，修改接口复用旧 key；新增接口为空表示未配置 key。
- `DASHSCOPE` 使用默认百炼地址。
- `OPENAI_COMPATIBLE` 使用用户 Base URL。

删除规则：

- 删除非启用配置：直接删除，返回当前 role 快照。
- 删除启用配置且同 role 还有其他配置：删除后自动启用剩余配置。
- 删除该 role 唯一配置：自动创建一条默认 active 草稿，避免页面进入无 active 状态。

## Frontend Changes

`model-config` store 重写为设置页快照模型：

```js
activeRole
activeSummary
roleState = {
  CHAT: { configs, selectedConfig, form, loaded, loading, saving, deleting, activating, error },
  EMBEDDING: { configs, selectedConfig, form, loaded, loading, saving, deleting, activating, error }
}
```

页面流程：

- 进入“设置 -> 模型”：默认加载 `CHAT` 快照，顶部显示 Active Chat / Active Embedding，右侧表单显示启用 Chat 配置。
- 点击“Embedding 模型”：加载 `EMBEDDING` 快照，右侧表单显示启用 Embedding 配置。
- 点击左侧配置：不请求后端，直接显示该配置。
- 点击新建：当前 role 表单切为默认空白配置。
- 保存新增/修改、启用、删除：调用 settings 接口，并用返回快照刷新顶部 active、左侧列表、右侧表单。
- 请求过程中不显示整块遮罩，避免设置页切换时闪烁刺眼；请求失败不清空已有表单，只显示错误信息并允许用户重新读取。

状态约束：

- 模型编辑表单不得再在组件内维护第二份 `editorForm`。
- 模型页模板直接绑定 `modelConfigStore.form`。
- 后端快照落到 `activeSummary`、当前 role 的 `configs/selectedConfig/form` 后再渲染。
- `CHAT` 表单只显示 Temperature 和默认 Top K；`EMBEDDING` 表单只显示 Embedding 维度。

## Test Plan

- 后端：`mvn -Dtest=ModelConfigServiceTests,ModelConfigControllerTests test`
- 前端：`npm run build`
- 浏览器验证：
  - 进入模型设置页，默认显示启用 Chat 的完整字段。
  - Temperature、默认 Top K 首次进入即回显。
  - 点击 Embedding 后，Embedding 维度首次切换即回显。
  - 添加、修改、删除、启用后，顶部 Active 卡片、左侧列表、右侧表单同步刷新。
  - 请求失败时保留旧数据并显示错误。

不做桌面打包验证。

## Validation Status

- 2026-06-04：用户已在 Vue dev server + Spring Boot 后端的浏览器环境中验证模型设置页数据回显正常。
- 已确认进入“设置 -> 模型”后 Active 卡片、左侧配置列表和右侧表单能正确渲染；切换 `对话模型` / `Embedding 模型` 后对应字段能回显。
- 已去掉模型页整块加载遮罩，避免页签切换时闪烁刺眼。
- 本轮不再重复执行程序验证；后续若继续修改模型设置相关代码，再按 Test Plan 重新验证。

## Assumptions

- 后端现有模型配置数据可信，原 bug 是前端状态同步和接口返回语义不够明确导致。
- 旧 `/api/model-configs` 接口保留一个阶段，避免影响其他调用方。
- API Key 仍按当前阶段规则明文保存 SQLite。
