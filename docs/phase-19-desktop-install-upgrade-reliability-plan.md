# 第十九阶段计划：桌面安装、卸载与升级可靠性修复

实施状态：已落地。第十九阶段把桌面分发从“产物可生成”推进到“安装、卸载、升级可验证”。macOS unsigned 包只保留为技术测试 artifact，普通用户分发以 signed、notarized、stapled DMG 为准；Windows NSIS 安装器增加安装/卸载钩子，处理旧进程、旧安装目录资源和升级覆盖。

## Summary

本阶段解决两个真实安装问题：macOS 从 GitHub Release 下载 unsigned DMG 后仍可能被 Gatekeeper 判定“已损坏”；Windows 安装新版或卸载后可能因为旧进程、旧安装目录资源或快捷方式残留而继续打开旧版本。

不做自动更新，不改业务数据结构，不删除用户知识库数据。`%APPDATA%\CogniNote` 和 `~/Library/Application Support/CogniNote` 默认保留，因为它们保存 SQLite、Lucene、模型配置、聊天记录和日志。

## Key Changes

- macOS signed CI 验证主 `CogniNote.app`、嵌套 `CogniNoteBackend.app` 和 DMG 的 `codesign`、`notarytool`、`stapler`、`spctl`。
- macOS 文档明确：GitHub 下载同样会带 quarantine；只清理 DMG 不保证可运行，最终被检查的是 `.app`。升级或降级必须先退出旧实例并替换整个 `/Applications/CogniNote.app`，不能用 `cp -R` 合并覆盖 `.app` 目录包。
- macOS 构建脚本在 Tauri 打包前清理旧 `.app` / `.dmg` 输出，避免本地连续打包时读取旧包。
- Tauri 桌面壳启动日志写入桌面壳版本、包版本、实际启动路径、后端资源路径和端口，便于确认用户是否打开了新版本。
- Tauri 增加 single-instance 插件，第二次启动会聚焦现有主窗口；macOS 上会提示先退出旧版本再打开新版本，避免升级或降级后仍由旧实例接管。
- Tauri 在窗口关闭和应用退出事件中都关闭 `CogniNoteBackend`，减少后端残留进程。
- Windows NSIS 增加 `installerHooks`：安装前/卸载前关闭 `CogniNote.exe`、`cogninote-agent.exe`、`CogniNoteBackend.exe`，安装前清理旧主程序和 `backend/` 资源；清理失败时中止安装，避免覆盖安装或降级安装后混用旧版本资源。卸载后清理安装目录和常见快捷方式残留。
- 设置中心系统信息显示后端版本、前端版本、桌面壳版本和桌面模式；后端 `/api/system/status` 保留 `version` 并新增 `desktopMode`。

## Public Interfaces

- `/api/system/status` 响应新增 `desktopMode`，原有 `appName`、`version`、`status`、`dataDir` 保持兼容。
- Windows 新增 Tauri NSIS hook 文件：`cogniNote-agent-front/src-tauri/windows/hooks.nsh`。
- 前端构建时通过 Vite 注入 `__COGNINOTE_FRONTEND_VERSION__`，并在桌面环境通过 Tauri app API 读取桌面壳版本，只用于系统信息展示。

## Test Plan

- 后端：`mvn test`
- 前端：`npm --prefix cogniNote-agent-front run build`
- Tauri/Rust：`cargo metadata --verbose --format-version 1 --all-features --filter-platform x86_64-pc-windows-msvc`
- Windows：先安装并运行旧版，再直接安装新版，确认安装器关闭旧进程、清理旧主程序和 `backend/`、打开后显示新版本；再验证新版本降级到旧版本时必须走“先卸载再安装”或显式卸载路径，不能留下新旧资源混装。卸载后安装目录和常见快捷方式清理，`%APPDATA%\CogniNote` 保留。
- macOS：signed workflow 中验证主 app、嵌套后端 app 和 DMG；从 GitHub Release 下载 signed DMG，先退出旧版，再拖入 `/Applications` 替换整个旧版 `.app`，系统信息和日志显示新版本；补充验证命令行安装时必须 `rm -rf /Applications/CogniNote.app` 后再 `ditto`，不能 `cp -R` 合并覆盖。

## Assumptions

- macOS unsigned 包只服务开发者技术测试，不承诺普通用户可安装。
- Windows 卸载默认不删除用户数据目录，避免误删知识库、模型配置和聊天记录。
- 本阶段不引入自动更新、MSIX/MSI 或 macOS `.pkg`；如果 DMG 覆盖安装仍频繁出问题，后续再评估 `.pkg`。
- 版本升级继续使用 `scripts/update-release-version.ps1`，禁止手动全局替换版本号。
