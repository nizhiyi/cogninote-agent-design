# CogniNote Agent 第十四阶段计划：macOS 独立桌面打包链路

## Summary

第十四阶段新增 macOS Apple Silicon 桌面打包链路，并明确和 Windows 打包链路分开维护。Windows 继续使用现有 `tauri.conf.json`、PowerShell 脚本、`target/desktop/backend/CogniNoteBackend/` 后端 app-image 和 NSIS 安装包；macOS 使用独立 `tauri.macos.conf.json`、Shell 脚本、`target/desktop-macos/backend/CogniNoteBackend.app/` 后端 app-image，并产出 `.app` / `.dmg`。

第一版 macOS 只支持 Apple Silicon arm64，不做签名、公证、自动更新、Universal Binary、Intel Mac 和 App Store 分发。

## Key Changes

- 新增 macOS 独立 Tauri 配置：
  - `cogniNote-agent-front/src-tauri/tauri.macos.conf.json`
  - macOS bundle targets 为 `app,dmg`
  - macOS 资源路径为 `../../target/desktop-macos/backend/CogniNoteBackend.app/`
  - macOS 图标为 `icons/icon.icns`
- 新增 macOS 独立脚本：
  - `scripts/verify-desktop-toolchain-macos.sh`
  - `scripts/build-desktop-backend-macos.sh`
  - `scripts/build-desktop-app-macos.sh`
- Tauri 主进程按平台定位后端启动器：
  - Windows：`backend/CogniNoteBackend/CogniNoteBackend.exe`
  - macOS：`backend/CogniNoteBackend.app/Contents/MacOS/CogniNoteBackend`
- macOS 桌面运行时显式注入：
  - `COGNINOTE_DATA_DIR=~/Library/Application Support/CogniNote`
  - `COGNINOTE_LOG_FILE=~/Library/Application Support/CogniNote/logs/app.log`
- 新增 macOS CI workflow：`.github/workflows/desktop-macos.yml`，手动触发，上传 `.app` 和 `.dmg` artifacts；`.app` 在上传前会压缩为 `CogniNote.app.zip`，zip 内保留完整 `.app` 包结构。

## Build Commands

macOS Apple Silicon 本机构建：

```bash
bash ./scripts/verify-desktop-toolchain-macos.sh
bash ./scripts/build-desktop-backend-macos.sh --skip-tests
bash ./scripts/build-desktop-app-macos.sh --skip-tests
```

Windows 构建命令保持不变：

```powershell
.\scripts\build-desktop-app.ps1 -SkipTests
```

## Test Plan

- Windows 回归：
  - `.\scripts\build-desktop-app.ps1 -SkipTests`
  - Windows 继续生成 release exe 和 NSIS 安装包。
  - Windows 日志仍写入 `%APPDATA%\CogniNote\logs\`。
- macOS 真机：
  - 在 Apple Silicon Mac 上执行 macOS 三个脚本。
  - 双击 `.app` 能打开桌面窗口，后端不弹 Terminal。
  - 日志写入 `~/Library/Application Support/CogniNote/logs/desktop-backend.log` 和 `app.log`。
  - 文档导入、搜索、模型配置、RAG 对话和纯模型对话可用。
  - 关闭窗口后 `CogniNoteBackend` 进程退出。
- CI：
  - 手动触发 `Desktop macOS` workflow。
  - workflow artifacts 包含 `CogniNote.app.zip` 和 `.dmg`。

## Assumptions

- macOS 第一版只支持 Apple Silicon arm64。
- Windows 与 macOS 打包配置、脚本和后端 app-image 输出目录必须分离。
- Java/Spring Boot 业务、Vue 前端、REST API、SQLite/Lucene 数据结构不因 macOS 打包改变。
- 未签名/未公证导致的 Gatekeeper 提示在第一版内部验证阶段可接受。
