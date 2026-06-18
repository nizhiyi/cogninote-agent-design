# 第 30 阶段计划：桌面安装包减重与 JDK Runtime 裁剪

## Summary

第 30 阶段优先解决桌面分发包体积偏大的真实问题。当前 Windows 和 macOS 桌面包都把 `jpackage` 生成的完整后端 app-image 作为 Tauri 资源打入最终产物，其中 JDK runtime 是最大、最确定的减重目标。

本阶段第一版只做 JDK runtime 裁剪：通过 `jdeps` 分析 Spring Boot 后端运行所需 JDK 模块，用 `jlink` 生成 custom runtime，再让 `jpackage` 通过 `--runtime-image` 使用该运行时。业务功能、数据库结构、REST API、前端交互、Tauri resource 路径和 updater 资产命名保持不变。

本地探针基线：

| 项 | 当前体积 | custom runtime 探针体积 |
| --- | ---: | ---: |
| 后端 app-image | 207.46 MB | 141.59 MB |
| 后端 runtime | 118.68 MB | 52.82 MB |
| Spring Boot fat jar | 88.28 MB | 不变 |

custom runtime 探针后端已验证可启动，`/api/system/status` 返回 `200`。这只证明基础启动链路可行，不能替代完整桌面功能回归。

## Goals

- 将 Windows 和 macOS 后端 app-image 改为使用自定义 JDK runtime，目标至少减少 40 MB。
- 保持现有桌面启动链路：Tauri 仍定位 `backend/CogniNoteBackend` 或 `backend/CogniNoteBackend.app` 中的 `jpackage` 启动器。
- 保持 Windows NSIS、macOS app/dmg、自动更新 manifest 和发布资产命名兼容。
- 用可重复的脚本步骤生成 runtime，避免手工维护裁剪后的 JDK 目录。
- 在文档和构建日志中记录裁剪前后体积，方便后续继续优化 fat jar 和前端静态资源。

## Non-goals

- 不在第一版移除或替换业务依赖，例如 Spring Boot、POI、PDFBox、Lucene、SQLite JDBC、Spring AI。
- 不拆分“基础版/完整版”功能包，不把 PDF、Office、Lucene 或模型 SDK 做成可选插件。
- 不改变用户数据目录、SQLite schema、Lucene 索引格式或模型配置。
- 不改变安装器类型；Windows 继续使用 NSIS，macOS 继续使用 app/dmg。
- 不追求极限模块裁剪。第一版宁可多保留几个模块，也不牺牲桌面功能稳定性。

## Runtime 裁剪方案

后端打包脚本在 Maven package 后、`jpackage` 前新增 custom runtime 生成步骤。

Windows 输出目录：

```text
target/desktop/runtime
```

macOS 输出目录：

```text
target/desktop-macos/runtime
```

第一版保守模块清单：

```text
java.base,java.compiler,java.desktop,java.instrument,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql.rowset,java.xml.crypto,jdk.attach,jdk.incubator.vector,jdk.jdi,jdk.jfr,jdk.management,jdk.unsupported
```

`jlink` 参数固定为：

```text
--module-path <JDK_HOME>/jmods
--add-modules <模块清单>
--strip-debug
--strip-java-debug-attributes
--strip-native-commands
--no-header-files
--no-man-pages
--compress zip-6
--output <custom-runtime-dir>
```

`--strip-native-commands` 会移除 runtime 中的 `java` 等通用命令。桌面后端通过 `jpackage` 启动器运行，不要求用户或 Tauri 直接调用 runtime 里的 `java.exe`。

`jpackage` 增加：

```text
--runtime-image <custom-runtime-dir>
```

并继续保留已有 JVM 参数：

```text
--java-options --enable-native-access=ALL-UNNAMED
```

SQLite JDBC 在 JDK 25 下仍需要 native access，不能因为 runtime 裁剪而移除这条参数。

## 模块清单生成与维护

`jdeps` 不能直接只分析 Spring Boot fat jar 外层。后端依赖位于 `BOOT-INF/lib`，应用类位于 `BOOT-INF/classes`，因此模块分析应先解开 fat jar，再用 `BOOT-INF/lib/*.jar` 作为 classpath 分析 `BOOT-INF/classes`。

参考命令形态：

```powershell
jar xf target/cogninote-agent-design.jar
jdeps --multi-release 25 `
  --ignore-missing-deps `
  --print-module-deps `
  --class-path "<BOOT-INF/lib/*.jar 拼成的 classpath>" `
  BOOT-INF/classes
```

`jdeps` 输出只能作为模块清单起点，不能自动等同于最终可运行集合。当前项目存在多类静态分析容易漏判的路径：

- Spring Boot 自动配置和反射。
- HTTPS 模型调用和安全 provider。
- SQLite JDBC native library 加载。
- PDFBox、POI 对字体、图像、XML、压缩包的运行时访问。
- Lucene 中文分词和 `jdk.incubator.vector`。
- Spring AI、OpenAI compatible、DashScope SDK 的可选代码路径。

因此第一版模块清单采用保守策略。后续如果继续删除模块，必须每次只删一小组，并跑完整桌面功能验收。

## 构建脚本改造点

Windows：

- `scripts/build-desktop-backend.ps1` 增加 `$customRuntimeDir`。
- 在清理旧 `CogniNoteBackend` 和 `jpackage-input` 时同步清理旧 custom runtime。
- 新增 `Invoke-JlinkRuntime` 或等价函数，统一生成 runtime。
- `$jpackageArgs` 增加 `--runtime-image`, `$customRuntimeDir`。
- 生成后打印 runtime、app-image 和 jar 体积。

macOS：

- `scripts/build-desktop-backend-macos.sh` 增加 `CUSTOM_RUNTIME_DIR="$PROJECT_ROOT/target/desktop-macos/runtime"`。
- 在 `rm -rf "$BACKEND_IMAGE_DIR" "$JPACKAGE_INPUT_DIR"` 时同步清理旧 custom runtime。
- 调用 `"$JAVA_HOME/bin/jlink"` 生成 runtime。
- `jpackage` 增加 `--runtime-image "$CUSTOM_RUNTIME_DIR"`。
- 生成后用 `du -sh` 打印 runtime、app-image 和 jar 体积。

两个平台必须保持模块清单一致，除非某个平台出现明确的 JDK 或签名差异。若需要平台差异，必须在脚本和本计划文档中写明原因。

## Verification

基础构建验证：

```powershell
.\scripts\build-desktop-backend.ps1 -SkipTests
.\scripts\build-desktop-app.ps1 -SkipTests
```

```bash
bash ./scripts/build-desktop-backend-macos.sh --skip-tests
bash ./scripts/build-desktop-app-macos.sh --skip-tests
```

运行验收：

- 桌面启动后打开主窗口，关闭窗口后后端进程退出。
- `/api/system/status` 返回 `200`，桌面 token 保护仍生效。
- SQLite 初始化、模型配置读写、聊天会话读写正常。
- 导入 `.txt`、`.pdf`、`.docx` 文档正常。
- Lucene 建索引、中文搜索、RAG 检索正常。
- OpenAI compatible 和 DashScope 连接测试正常。
- 普通聊天、RAG 聊天、知识图谱重建链路正常。
- Windows 覆盖安装和卸载保留第 19 阶段的旧进程、旧资源、WebView2 缓存处理。
- macOS signed 模式继续验证外层 `CogniNote.app`、嵌套 `CogniNoteBackend.app` 和发布用 DMG 的签名、公证、staple。

体积验收：

- 记录 custom runtime 目录体积。
- 记录后端 app-image 体积。
- 记录 Windows NSIS installer 体积。
- 记录 macOS `.app`、`.dmg`、updater `.app.tar.gz` 体积。
- custom runtime app-image 相比当前后端 app-image 至少减少 40 MB，否则本阶段视为未达预期。

## Rollback

如果 custom runtime 在完整验收中暴露难以快速定位的运行期缺模块问题，回滚方式是移除 `jpackage --runtime-image` 参数，并跳过 `jlink` 生成步骤，让 `jpackage` 恢复使用默认 runtime。

回滚不需要数据库迁移，不影响用户数据，不影响前端资源和 updater manifest 结构。发布前如果需要临时保守分发，应优先回滚打包脚本，而不是继续猜测缺失模块。

## 后续减重方向

JDK runtime 裁剪完成后，再评估第二阶段减重：

- fat jar 依赖体积：重点关注 `sqlite-jdbc`、`poi-ooxml-lite`、Lucene、POI、PDFBox、Spring AI 相关依赖。
- 前端静态资源：重点关注 Shiki 语言包、Mermaid、图谱组件和大 chunk 的按需加载。
- 文档解析能力分层：如果体积压力仍明显，再评估 Office/PDF 解析是否做成可选能力。

第二阶段必须单独立项，不能混入本阶段第一版。否则运行时裁剪和业务依赖裁剪同时变化，会让回归问题难以定位。

## References

- [Oracle jpackage Image and Runtime Modifications](https://docs.oracle.com/en/java/javase/25/jpackage/image-and-runtime-modifications.html)：`jpackage` 支持通过 `--runtime-image` 使用 `jlink` 生成的 custom runtime。
- [Oracle jlink Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html)：`jlink` 用于按模块生成 runtime，并支持压缩、去 debug、去 header/man page 等选项。
- [Oracle jdeps Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html)：`jdeps --print-module-deps` 输出可作为 `jlink --add-modules` 的输入起点。

## Assumptions

- 项目继续以 JDK 25 作为构建和运行基线。
- 第 30 阶段优先保障桌面分发稳定性，而不是追求最小理论体积。
- 当前本地探针数据只作为基线参考，最终验收以实施后的 Windows/macOS 构建产物为准。
