# CogniNote Agent

CogniNote Agent 是一个 Java + Vue 实现的本地个人知识库智能体。当前项目处于第一阶段：基础工程闭环。

## 当前阶段目标

- Spring Boot 后端稳定启动。
- Vue 3 前端可以独立开发。
- 前端 `/api` 请求可以代理到本地后端。
- Spring Boot 可以托管 Vue 打包后的静态页面。
- 启动后初始化本地数据目录。

## 环境要求

- JDK 25。
- Maven 3.9+。
- Node.js 20.19.6 或兼容版本。
- npm 10.8.2 或兼容版本。

当前 Maven Enforcer 会拒绝非 JDK 25 的运行环境。Spring Boot 3.5.14 官方兼容到 Java 25，项目统一使用本机 JDK 25 构建。

## 后端开发

```powershell
mvn test
mvn spring-boot:run
```

后端默认监听：

```text
http://127.0.0.1:18080
```

系统状态接口：

```text
GET http://127.0.0.1:18080/api/system/status
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

## 整包构建

```powershell
mvn -Pwith-frontend package
java -jar target/cogninote-agent-design-0.0.1-SNAPSHOT.jar
```

`with-frontend` profile 会执行前端构建，并把 `cogniNote-agent-front/dist` 复制到 Spring Boot Jar 的静态资源目录。
