# 第十七阶段计划：Element Plus 引入与设置中心重构

## Summary

第十七阶段引入 Element Plus 作为前端基础交互组件库，并把设置页重构为设置中心。设置中心使用左侧分组导航，包含系统、知识库、模型三组，右侧按子功能展示内容。

本阶段不重做聊天主界面，Element Plus 只用于设置中心、表单、提示、确认和基础导航控件。

## Key Changes

- 新增 Element Plus、Element Plus Icons、自动导入插件，并在 Vite 中配置按需导入。
- `/settings` 改为设置中心主入口，左侧分组为系统、知识库、模型。
- 系统分组包含主题设置、系统相关信息和 GitHub 地址。
- 知识库分组拆分为知识库目录管理和检索测试。
- 模型分组拆分为对话模型和 Embedding 模型入口。
- 未配置可用 Embedding 模型时，知识库分组显示配置提示；配置并启用后隐藏。
- `/knowledge` 和 `/model-config` 路由保留兼容，继续复用拆分后的组件。

## Test Plan

- 运行 `npm --prefix cogniNote-agent-front run build`。
- 打开 `/settings`，确认默认进入系统主题设置。
- 检查系统、知识库、模型三组以及所有子菜单切换。
- 检查亮色/暗色主题下 Element Plus 控件可读。
- 检查知识库导入、启停、重建、删除、检索测试。
- 检查对话模型和 Embedding 模型的创建、保存、启用、测试连接和获取模型。

## Assumptions

- 桌面实机回归由用户自行测试。
- 本阶段不改变后端 API、Pinia store 对外语义和数据库结构。
- Element Plus 作为基础交互组件库使用，不替代 CogniNote 的产品级自定义视觉体系。
