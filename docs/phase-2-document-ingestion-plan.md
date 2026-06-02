# CogniNote Agent 第二阶段任务计划：文档摄入闭环

## Summary

第二阶段只做 Milestone 2：文档摄入，目标是把本地目录中的 `.md`、`.txt`、`.docx`、文本型 `.pdf` 扫描、解析、清洗、分块并保存到 SQLite。

不做 Lucene、Embedding、混合检索、RAG 问答，这些留到第三、四阶段。

## Key Changes

- 后端依赖新增 `spring-boot-starter-jdbc`、`sqlite-jdbc`、`poi-ooxml`、`pdfbox`。
- SQLite 数据库落在 `%APPDATA%/CogniNote/data/cogninote.db`，由现有 `AppStorage` 提供路径。
- 使用 Spring JDBC + 手写 schema 初始化，不引入 MyBatis/JPA/Flyway，保持第一版简单可控。
- 新增文档摄入 API：`GET /api/documents`、`POST /api/documents/ingest`、`DELETE /api/documents/{id}`。
- 前端知识库入口从“待实现卡片”升级为最小可用页：输入本地目录路径、触发导入、展示导入结果和文档列表。
- 目录选择第一版采用“输入/粘贴本地路径”，不做浏览器原生文件夹选择器，也不做桌面 EXE 文件选择窗口。

## Implementation Changes

- SQLite 表：
  - `documents`：保存文档 ID、源路径、文件名、类型、大小、修改时间、内容哈希、状态、时间戳。
  - `chunks`：保存 chunk ID、document ID、chunk 序号、内容、内容哈希、页码、标题、估算 token 数。
  - 状态只使用 `PARSED`、`SKIPPED`、`FAILED`；`indexed_at` 保留为 `NULL`，等第三阶段 Lucene 索引再写。
- 摄入流程：
  - 扫描目录，默认递归。
  - 只接受 `.md`、`.txt`、`.docx`、`.pdf`。
  - 以规范化绝对路径生成 `document_id`，以文件字节 SHA-256 生成 `content_hash`。
  - 若路径、大小、修改时间、hash 均未变化，则跳过并保留旧 chunks。
  - 若文件变化，则删除旧 chunks，重新解析和分块，再写入 SQLite。
- 解析策略：
  - Markdown/TXT：按 UTF-8 读取；UTF-8 失败时回退系统默认编码。
  - DOCX：用 Apache POI 提取段落文本，不支持 `.doc`。
  - PDF：用 PDFBox 按页提取文本；无文本层或提取后为空时标记 `FAILED`，不做 OCR。
- 分块策略：
  - 文本清洗统一换行、压缩多余空白、去除空段。
  - chunk 最大约 `1800` 字符，重叠 `200` 字符。
  - Markdown 标题用最近的 `#` 标题填充 `heading`；PDF chunk 填充 `page_number`。
  - `token_count` 用 `ceil(chars / 4.0)` 估算，不引入 tokenizer。
- API 行为：
  - `POST /api/documents/ingest` 请求体：
    ```json
    {
      "folderPath": "D:/notes",
      "recursive": true
    }
    ```
  - 响应返回扫描数量、解析成功数、跳过数、失败数和失败文件摘要。
  - `GET /api/documents` 返回文档列表，按 `updated_at desc` 排序。
  - `DELETE /api/documents/{id}` 只删除 SQLite 中的文档和 chunks，不删除用户原始文件。

## Test Plan

- 后端单元测试：
  - Markdown/TXT 解析。
  - DOCX 解析。
  - 文本型 PDF 解析。
  - 空 PDF / 图片 PDF 标记失败。
  - chunk 大小、重叠、hash、heading/page_number 行为。
- 后端集成测试：
  - 使用临时数据目录创建 SQLite。
  - 导入包含 `.md/.txt/.docx/.pdf` 的样例目录。
  - 重复导入未变化文件应返回 skipped。
  - 修改文件后应替换旧 chunks。
  - 删除文档只删除数据库记录，不删除源文件。
- 前端验证：
  - 后端启动时，知识库页能读取文档列表。
  - 输入有效目录可导入并显示结果。
  - 输入不存在目录显示错误。
- 构建验证：
  ```powershell
  $env:JAVA_HOME='D:\CodeApps\Java-JDK\jdk-25.0.2'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  mvn test
  npm --prefix cogniNote-agent-front run build
  mvn -Pwith-frontend package
  ```

## Assumptions

- 后端环境统一使用 JDK 25。
- 第二阶段不做异步任务队列，导入接口先同步执行；大目录性能优化放后续。
- 第二阶段不复制用户原始文件，只保存解析后的 chunk 文本和元数据。
- SQLite 先用单连接池，降低本地写入锁复杂度。
- 依赖版本参考 Maven Central：SQLite JDBC `3.53.0.0`、Apache POI `5.5.1`、PDFBox `3.0.7`。
