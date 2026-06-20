# 知记空间（CogniNote）Frontend

知记空间（CogniNote）的 Vue 3 前端。当前前端是桌面对话应用形态：`/chat` 显示左侧临时会话列表和主对话流，`/settings` 使用独立全屏设置中心，左侧归拢系统、知识库和模型配置，右侧展示具体设置内容。

前端生产构建由 Spring Boot 托管，桌面版由 Tauri WebView 加载后端页面。

## 工程结构

```text
src/
  ├─ api/          # 统一 JSON API client、知识库健康 API 和 SSE chat stream parser
  ├─ components/   # 应用壳、来源列表、Markdown 渲染、知识库总览、目录管理面板/健康抽屉等复用组件
  ├─ router/       # /chat、/settings、/knowledge、/model-config
  ├─ stores/       # Pinia 状态：system、documents、search、knowledgeHealth、modelConfig、chat、theme
  ├─ styles/       # 基础、控件、知识库、桌面对话、Markdown、主题、响应式样式
  └─ views/        # 页面级组件
```

维护约束：

- Assistant 消息通过 `markdown-renderer.vue` 渲染 Markdown，禁用原始 HTML。
- RAG 引用来源由 `source-list.vue` 自己维护折叠状态，避免消息组件膨胀。
- 用户引用助手回复片段由 `chat` store 的 `pendingReferences` 管理；发送区只显示独立引用标签，不把标签嵌入 textarea。发送后 user 消息的 `references` 字段负责刷新恢复和悬停预览。
- 知识库总览页只放全库健康摘要、最近目录和轻量目录管理入口；导入目录使用 `knowledge-folder-import-dialog.vue` 弹窗，目录同步、启停、删除、重建和文档展开集中在 `knowledge-directory-manager-panel.vue`。
- 目录管理面板使用前端本地搜索、筛选和分页处理当前目录快照；分页控件走全局 Element Plus 中文 locale，避免英文 `items/page` 混入中文界面。
- 知识库健康快照由 `knowledge-health` store 管理；首屏只加载全库轻量快照，目录级失败文件、缺失文件和维护记录只在打开问题抽屉时读取。停用目录是用户主动排除检索范围的状态，不计入问题数量，也不触发“查看问题”。
- 目录同步、重建、启停和删除后必须同时刷新目录列表、索引状态和健康快照，避免资料管理页出现“目录已变但健康状态没变”的分叉。
- 主题偏好由 `theme` store 写入 `localStorage`，通过 `html.theme-dark` / `html.theme-light` 控制样式。
- Element Plus 用于设置中心和知识库维护页的标准控件、提示、确认操作和分页；聊天主界面继续保持自定义 UI。
- 设置中心不显示左侧对话栏，避免非聊天能力继续占用聊天布局。

## Project Setup

```sh
npm ci
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

Vite 会把 `/api` 代理到本地 Spring Boot 后端：

```text
http://127.0.0.1:18080
```

### Compile and Minify for Production

```sh
npm run build
```

生产构建产物会输出到 `dist/`，整包构建时由根目录 Maven `with-frontend` profile 复制进 Spring Boot 静态资源目录。

### Desktop Build

桌面打包入口在项目根目录，不建议从前端子目录直接运行完整构建：

```powershell
cd D:\code\JavaCode\cogninote-agent-design
.\scripts\build-desktop-app.ps1 -SkipTests
```

如需只执行 Tauri 构建，必须先确保根目录 `target/desktop/backend/CogniNoteBackend/` 已由 `scripts/build-desktop-backend.ps1` 生成：

```sh
npm run desktop:build
```

桌面构建、`.ps1` 脚本运行方式、产物路径和常见问题见根目录文档 `docs/desktop-build-guide.md`。
