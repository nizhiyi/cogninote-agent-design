# CogniNote Agent 第一阶段任务计划

## Summary

第一阶段按 `cogninote-agent-design.md` 里的 Milestone 1：基础工程闭环执行，目标是先让项目稳定、可启动、可联调、可打包静态前端，不进入文档解析、SQLite、Lucene、Embedding、RAG。

当前仓库还是新建骨架：Spring Boot 只有启动类和空配置，Vue 还是默认欢迎页；`pom.xml` 里初始存在 Redis、MySQL、MyBatis、Spring AI、Java 25、Spring Boot Snapshot 等与第一阶段目标不匹配的内容，需要先收敛。

## Key Changes

- 构建基线：统一为 Java 25 + Spring Boot 3.5.14 稳定版，移除 Snapshot 仓库。
- 后端依赖：第一阶段只保留 Web、Validation、Test 和必要构建插件。
- 后端基础能力：固定监听 `127.0.0.1`，开发端口默认 `18080`；创建 `%APPDATA%/CogniNote/` 下的 `config/`、`data/`、`index/lucene/`、`logs/`。
- 前后端联调：新增 `GET /api/system/status`，返回应用名、版本、状态和数据目录。
- 前端基础壳：替换默认 Vue 页面，展示项目名、四个入口位和后端连接状态。
- 静态托管闭环：开发时 Vite 代理 `/api` 到 `http://127.0.0.1:18080`；打包时 Maven `with-frontend` profile 调用 `npm ci && npm run build`，再把 `dist` 复制进 Spring Boot 最终 Jar 的 `static/`。
- 工程清理：补充前端 `package-lock.json`、`.gitignore` 和简短启动说明。

## Implementation Steps

1. 调整 `pom.xml`：Spring Boot parent 改为 `3.5.14`，`java.version` 改为 `25`，移除 Snapshot repositories 和第一阶段不用的依赖。
2. 加 Maven Enforcer：要求运行 JDK 为 `[25,26)`，避免 JDK 8、17 等非目标环境参与构建。
3. 配置 `application.yaml`：`server.address=127.0.0.1`，`server.port=${COGNINOTE_PORT:18080}`，`app.storage.base-dir=${COGNINOTE_DATA_DIR:}`。
4. 新增后端 `storage` 与 `system` 基础包：负责解析数据目录、启动时创建目录、提供 `/api/system/status`。
5. 修改 Vue：增加基础布局、系统状态请求、错误态；配置 Vite dev proxy。
6. 增加 Maven `with-frontend` profile：使用 `frontend-maven-plugin` `1.15.4` 和 Node `v20.19.6` 构建前端，再复制到 Jar 静态资源目录。
7. 更新 README：写清楚开发启动、前后端联调、整包构建三条命令。

## Test Plan

- 后端：在 JDK 25 下运行 `mvn test`，确认 Spring context 能启动。
- 前端：运行 `npm ci` 和 `npm run build`，确认 Vue 能正常构建。
- 联调：启动后端和 Vite，访问前端页面，确认 `/api/system/status` 返回并显示连接成功。
- 静态托管：运行 `mvn -Pwith-frontend package`，再启动 Jar，访问 `http://127.0.0.1:18080/`，确认由 Spring Boot 返回 Vue 页面。
- 数据目录：首次启动后确认 `%APPDATA%/CogniNote/config`、`data`、`index/lucene`、`logs` 都被创建。

## Assumptions

- 第一阶段只做基础工程闭环，不做文档摄入、SQLite 表、Lucene、模型配置、RAG 对话。
- 开发阶段先用固定端口 `18080`；自动探测端口和自动打开浏览器放到 EXE 启动器阶段。
- UI 第一阶段不引入组件库，先用原生 Vue + CSS 保持依赖轻。
