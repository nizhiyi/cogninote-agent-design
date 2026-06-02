# CogniNote Agent 第五阶段任务计划：模型配置增强

## Summary

第五阶段先不做本地 EXE 交付，改为优先完善“模型配置”能力。目标是把 Phase 4 里固定 DashScope 表单升级为更可用的模型配置中心：用户输入自定义 `Base URL` 和 `API Key` 后，后端自动拉取可用模型列表，前端让用户选择默认 Chat 模型和 Embedding 模型。

本阶段不做文档摄入、Lucene、RAG Prompt、会话历史、Agent 工具调用、EXE 打包。已完成的 Phase 4 RAG 对话继续复用新的 active 模型配置。

本阶段计划文件落地路径：`docs/phase-5-model-configuration-plan.md`。

## Key Changes

- 模型配置从“固定 DashScope 参数”升级为“OpenAI-compatible 风格配置”：
  - `provider`
  - `displayName`
  - `baseUrl`
  - `apiKey`
  - `chatModel`
  - `embeddingModel`
  - `embeddingDimensions`
  - `temperature`
  - `topK`
- 默认 provider 仍为 `DASHSCOPE`，但配置形态按 OpenAI-compatible 方式设计，方便后续接入百炼兼容接口、OpenAI、DeepSeek、Ollama、LM Studio。
- 新增“自动获取模型列表”能力：后端根据 `baseUrl + apiKey` 调用模型列表接口，返回模型 ID、名称、能力类型。
- 前端模型配置页升级：
  - 输入 Base URL 和 API Key。
  - 点击“获取模型”拉取模型列表。
  - 用下拉框选择 Chat 模型和 Embedding 模型。
  - 保留手动输入模型 ID 的兜底能力。
  - 保存配置后立即影响 RAG 对话和 Embedding 网关。
- API Key 仍按开发态明文保存到 SQLite，README 保留风险说明；本阶段只改善配置可用性，不做 Windows 凭据加密。

## API Changes

- 保留已有接口：
  ```text
  GET  /api/model-config
  PUT  /api/model-config
  POST /api/model-config/test
  ```
- 新增模型列表接口：
  ```text
  POST /api/model-config/models
  ```
- `POST /api/model-config/models` 请求体复用配置草稿：
  ```json
  {
    "provider": "DASHSCOPE",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "apiKey": "sk-...",
    "chatModel": "qwen-plus",
    "embeddingModel": "text-embedding-v4",
    "embeddingDimensions": 1024,
    "temperature": 0.7,
    "topK": 8
  }
  ```
- 响应使用统一 `ApiResponse<T>` 包装，`data` 示例：
  ```json
  {
    "models": [
      {
        "id": "qwen-plus",
        "name": "qwen-plus",
        "capability": "CHAT"
      },
      {
        "id": "text-embedding-v4",
        "name": "text-embedding-v4",
        "capability": "EMBEDDING"
      }
    ],
    "fetchedAt": 1780000000000
  }
  ```

## Implementation Changes

- 后端：
  - 扩展 `model_config` 表，新增 `display_name`、`base_url` 字段；保留旧字段兼容已有数据库。
  - `ModelConfigRequest/Response` 增加 `provider`、`displayName`、`baseUrl`。
  - 新增 `ModelCatalogService`，负责按配置草稿获取模型列表，并把返回结果规整为 `CHAT`、`EMBEDDING`、`UNKNOWN`。
  - 对 DashScope 默认 Base URL 使用 `https://dashscope.aliyuncs.com/compatible-mode/v1`。
  - 模型列表获取优先调用 OpenAI-compatible `/models`；若返回模型不带能力信息，则用模型 ID 规则做轻量分类：包含 `embedding` 的归为 `EMBEDDING`，其余默认 `CHAT`。
  - `DashScopeModelFactory` 和现有 LLM/Embedding 网关改为读取 `baseUrl`，不再把 DashScope API 地址写死。
  - 实现时需注意：Spring AI Alibaba `DashScopeApi` 支持自定义 `baseUrl`，但 Chat/Embedding 请求仍使用 DashScope 原生 path；因此配置页默认保存 compatible Base URL 用于 `/models`，模型工厂在调用 Chat/Embedding 时将 `/compatible-mode/v1` 转换为同一主机的 DashScope 原生 API 根路径。
  - `POST /api/model-config/test` 同时验证 Chat 模型可调用；Embedding 模型验证放到模型列表和索引流程，不在测试连接里强制调用。
- 前端：
  - `model-config-api.js` 增加 `fetchModelOptions`。
  - `model-config` store 增加模型列表、加载状态、模型分类、手动输入兜底状态。
  - `model-config-view.vue` 增加 Base URL、显示名称、获取模型按钮、Chat 模型下拉框、Embedding 模型下拉框。
  - API Key 留空仍表示复用已保存 key；获取模型和测试连接也遵循这个规则。
  - 页面用清晰错误态区分：未填 Base URL、未填 API Key、模型列表获取失败、连接测试失败。
- 文档：
  - README 更新第五阶段状态、Base URL 配置方式、自动获取模型列表说明。
  - 主规划文档更新里程碑顺序：Milestone 5 先做模型配置增强，本地交付顺延到后续阶段。

## Test Plan

- 后端单元测试：
  - `ModelConfigService` 默认值填充包含 `baseUrl`。
  - 保存配置时空 API Key 复用旧 key。
  - Base URL 必须是合法 HTTP/HTTPS URL。
  - 模型列表返回能正确分类 Chat / Embedding / Unknown。
  - `/api/model-config/models` 在 API Key 缺失时返回统一 400 错误。
- 后端集成测试：
  - 使用 fake 模型列表服务验证 `POST /api/model-config/models`。
  - `PUT /api/model-config` 后，RAG 对话能读取新的 `baseUrl` 和模型名。
  - 旧 SQLite 中没有 `base_url/display_name` 字段时，schema 初始化能补齐字段。
- 前端验证：
  - 模型配置页能填写 Base URL 和 API Key。
  - 点击“获取模型”后能展示下拉选项。
  - 用户能选择 Chat 模型和 Embedding 模型并保存。
  - 获取模型失败时保留手动输入模型 ID 的路径。
- 构建验证：
  ```powershell
  $env:JAVA_HOME='D:\CodeApps\Java-JDK\jdk-25.0.2'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  mvn test
  npm --prefix cogniNote-agent-front run build
  mvn -Pwith-frontend package
  ```

## Assumptions

- 后端继续统一使用 JDK 25。
- 第五阶段优先支持 DashScope 的 OpenAI-compatible 模式，默认 Base URL 为 `https://dashscope.aliyuncs.com/compatible-mode/v1`。
- `/models` 使用 OpenAI-compatible 接口；Chat/Embedding 仍通过 Spring AI Alibaba DashScope 原生请求体调用。
- 本阶段只维护一个 active 模型配置，不做多 provider 列表管理和 provider 切换历史。
- API Key 继续明文保存到 SQLite，仅作为开发态取舍；本地加密放到安全加固阶段。
- 自动获取模型列表失败时，用户仍可手动输入模型 ID 并保存。
- 不新增 UI 组件库，继续使用原生 Vue + CSS。
