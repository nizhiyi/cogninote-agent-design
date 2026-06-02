# CogniNote Agent

CogniNote Agent 是一个 Java + Vue 实现的本地个人知识库智能体。当前项目处于第四阶段：RAG 对话闭环。

## 当前阶段目标

- Spring Boot 后端稳定启动，统一使用 JDK 25。
- Vue 3 前端可以独立开发，并通过 Vite 代理访问后端 `/api`。
- Spring Boot 可以托管 Vue 打包后的静态页面。
- 启动后初始化本地数据目录。
- 导入 Markdown、TXT、DOCX、文本型 PDF 到 SQLite。
- 使用 Lucene 建立关键词索引，并提供索引状态、重建和搜索 API。
- 通过 Spring AI Alibaba DashScope 提供 Embedding 和 Chat 能力。
- 支持“配置模型 -> 提问 -> 混合检索 -> 构造 Prompt -> SSE 流式回答 -> 展示引用来源”的 RAG 对话闭环。

## 环境要求

- JDK 25。
- Maven 3.9+。
- Node.js 20.19.6 或兼容版本。
- npm 10.8.2 或兼容版本。

当前 Maven Enforcer 会拒绝非 JDK 25 的运行环境。

```powershell
$env:JAVA_HOME='D:\CodeApps\Java-JDK\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 后端开发

```powershell
mvn test
mvn spring-boot:run
```

后端默认监听：

```text
http://127.0.0.1:18080
```

首次启动会创建本地数据目录：

```text
%APPDATA%/CogniNote/
  config/
  data/
  index/lucene/
  logs/
```

也可以用环境变量覆盖：

```powershell
$env:COGNINOTE_PORT="18081"
$env:COGNINOTE_DATA_DIR="D:\CogniNoteData"
```

## 前端开发

```powershell
cd cogniNote-agent-front
npm ci
npm run dev
```

Vite 开发服务器会把 `/api` 代理到 `http://127.0.0.1:18080`。

## DashScope 配置

前端“模型配置”页支持保存和测试 Spring AI Alibaba DashScope 配置：

- Chat 模型默认 `qwen-plus`。
- Embedding 模型默认 `text-embedding-v4`。
- Embedding 维度默认 `1024`。
- Temperature 默认 `0.7`。
- Top K 默认 `8`。

API Key 当前以开发态明文保存到本机 SQLite：`%APPDATA%/CogniNote/data/cogninote.db`。这只是第四阶段为了打通闭环的取舍，不适合作为最终交付安全方案；后续应改为 Windows 本地加密或凭据管理。

Phase 3 的环境变量 fallback 仍然保留：

```powershell
$env:COGNINOTE_AI_EMBEDDING_PROVIDER="dashscope"
$env:COGNINOTE_DASHSCOPE_API_KEY="your-api-key"
$env:COGNINOTE_EMBEDDING_MODEL="text-embedding-v4"
```

有 SQLite 模型配置时优先使用 SQLite；没有配置时，Embedding 会回退到上述环境变量。

## API

系统状态：

```text
GET /api/system/status
```

文档导入：

```text
GET    /api/documents
POST   /api/documents/ingest
DELETE /api/documents/{id}
```

检索与索引：

```text
GET  /api/index/status
POST /api/index/rebuild
POST /api/search
```

模型配置：

```text
GET  /api/model-config
PUT  /api/model-config
POST /api/model-config/test
```

RAG 流式对话：

```text
POST /api/chat/stream
```

请求示例：

```json
{
  "question": "这个项目如何打包？",
  "topK": 8,
  "mode": "HYBRID"
}
```

SSE 事件顺序：

```text
event: meta
event: delta
event: done
event: error
```

若 `HYBRID` 或 `VECTOR` 因 Embedding 不可用失败，RAG 服务会自动降级到 `KEYWORD`，并在 `meta.retrievalMode` 中返回实际检索模式。

## 整包构建

```powershell
mvn -Pwith-frontend package
java -jar target/cogninote-agent-design-0.0.1-SNAPSHOT.jar
```

`with-frontend` profile 会执行前端构建，并把 `cogniNote-agent-front/dist` 复制到 Spring Boot Jar 的静态资源目录。
