# 第 28 阶段修订计划：中性色应用主题方案

## Summary

第 28 阶段把 CogniNote 的应用主题从“可切换的颜色覆盖层”收敛为可维护的设计合同。当前实现保留 `system / light / dark` 模式和本地 `cogninote-theme` 偏好，不新增后端 API、数据库字段或 Tauri command；主题气质改为中性工作台、蓝色动作色、绿色状态语义。

CogniNote 是本地优先的知识库/文档型桌面生产力工具。浅色和深色主题都以 slate/gray 承担背景、文本、边框和面板层级；蓝色承担主按钮、链接、选中、焦点、Element Plus primary、checkbox/radio 和图谱高亮；绿色只用于 success、在线/完成/解析成功等状态，必要时用于 logo 或品牌小面积点缀。

本阶段已按 `ui-ux-pro-max` 与 AnySearch 外部参考完成修订：取消“默认品牌青绿色 accent”的方向，固定为 **中性工作台 + 蓝色动作色 + 绿色成功语义**。视觉验收中发现聊天页顶部“未配置模型”曾误用成功绿，已补充语义化状态 pill 规则：启用/成功用 success，检索模式用 info，未配置用 warning，普通模式用 neutral。

## Key Changes

- `tokens.css` 新增 `--color-action*`、`--color-link`、`--color-focus-ring`、`--color-selected-*`、`--color-brand*`、状态边框和图谱色板；`--color-accent*` 保留为动作色兼容别名，不再表达绿色主色。
- v1 主题固定为 neutral + blue：light action 使用 `#2563EB` / `#1D4ED8` / `#DBEAFE`，dark action 使用 `#60A5FA` / `#93C5FD` / `rgba(96,165,250,.16)`。
- Element Plus 桥接改为动作色：`--el-color-primary`、hover、focus、radio selected 和 select selected 接 `--color-action*`；`--el-color-success` 才接绿色。
- 高曝光模块已清理旧青绿主视觉来源：聊天发送按钮/用户气泡、模型配置选中态、知识库复选框、AI Markdown 工具控件、通用按钮、旧工作台导航和 `theme.css` 迁移层统一接动作色 token。
- 聊天 header 状态 pill 改为业务语义驱动：`知识库已启用` 是 success，`HYBRID` 等检索模式是 info，`未配置对话模型 / 未配置向量模型` 是 warning，`纯模型对话` 是 neutral，避免把警示态误染为成功绿。
- 模型配置中的成功态保留绿色语义，但统一接 `--color-success*`，不再散落硬编码绿色值。
- 图谱探索器不再写死旧绿色主色：Cytoscape 渲染时读取 CSS token，主题切换会重新生成节点/边颜色；默认色板顺序为蓝、紫、琥珀、玫红、青，绿色仅作为后位可选系列色。
- 文档已同步为“中性主题 + 蓝色动作色 + 绿色成功语义”，避免后续开发继续引用旧绿色主色方向。

## Public Interfaces

- `THEME_OPTIONS` 不变：仍为 `system / light / dark`。
- localStorage key 不变：仍为 `cogninote-theme`。
- DOM 主题标记不变：继续保留 `html.theme-light`、`html.theme-dark`、`html.dark`、`data-theme`、`data-resolved-theme`。
- 后端 API、SQLite schema、Tauri command 均不新增。
- 新增 CSS token 是前端样式接口；旧 `--color-accent*` 只作为迁移兼容层保留，新样式应使用 `--color-action*` 或明确的 status token。

## Acceptance Criteria

- 浅色主题不泛绿，深色主题不变成暗绿色工作台；主界面视觉由中性 surface/text/border 和蓝色动作态承担。
- 绿色只出现在 success/status/brand 小范围语义中，不用于主按钮、链接、选中、focus、checkbox/radio、未配置状态或图谱第一主色。
- 关键文本对比度达到 WCAG AA：正文不低于 4.5:1，大文本不低于 3:1；图标、边框、状态指示等重要非文本 UI 不低于 3:1。
- 色彩不作为唯一信息来源：错误、警告、成功、选中状态必须有文本、图标、形状或位置辅助。
- 交互控件保持至少 44px 可点击区域，focus ring 可见，禁用态清晰且不可误点。

## Test Plan

- 构建验证：`npm --prefix cogniNote-agent-front run build`。
- 静态检查：扫描 `src` 中旧青绿值 `#0f766e`、`#0d9488`、`#2dd4bf`、`#5eead4`、`#1f6f68` 和相关 teal rgba，确认除兼容 alias 外不再出现在主布局、按钮、选中、焦点、输入控件或图谱高亮中。
- 手工主题场景：`system` 跟随操作系统浅/深色变化；`light` / `dark` 强制模式不受系统变化影响；旧 `light/dark` 偏好继续有效，异常值回落到 `system`。
- 页面视觉验收：`/chat`、`/knowledge`、`/settings?item=appearance|system-info|app-update|model-chat|model-embedding|chat-retrieval`，以及 AI Markdown、Mermaid 弹窗、知识图谱探索器。
- 视口验收：1280x820、1440x900、1024x768 和 375px 窄屏下无水平滚动、文字不溢出。

## Verification Log

- 构建：`npm --prefix cogniNote-agent-front run build` 通过；仅保留既有 Rolldown `#__PURE__` annotation warning 和 chunk size warning。
- 静态扫描：`src` 中旧青绿实现值 `#0f766e`、`#0d9488`、`#2dd4bf`、`#5eead4`、`#1f6f68`、旧 teal rgba、旧 success 绿硬编码均无命中；`--color-accent*` 只剩 `tokens.css` 兼容 alias 和说明文档。
- 视觉截图：已用 Edge/Playwright 检查 `/chat` light/dark、`/knowledge` light、`/settings?item=appearance` dark；输出位于 `output/playwright/`。
- 视觉修正：截图发现聊天 header 未配置模型 pill 误用成功绿，已改为 warning token，并新增 `conversationMetaItems` 让状态语义先于颜色映射。
- 对比度抽样：light action/white `5.17:1`，light action/app bg `4.82:1`，dark action/app bg `7.46:1`，dark action strong/app bg `10.52:1`；success/info/warning pill 文本组合达到或接近 WCAG AA 文本要求。

## References

- `ui-ux-pro-max`：知识库/文档类产品适合 neutral gray + link blue，强调 token 化、可见 focus、4.5:1 文本对比和不要只靠颜色传达信息。
- AnySearch / Atlassian Color：neutral、brand、success、warning、danger、accent 是不同角色，不能把有语义的颜色当 accent 用。
- AnySearch / Fluent 2 Color Tokens：neutral 承担 surfaces/text，brand/status 分离，并通过 alias token 适配 light/dark。
- AnySearch / WCAG 2.2 Non-text Contrast：UI 组件和状态指示至少 3:1，对焦点和选中态尤其关键。

## Assumptions

- 本阶段只做一个 CogniNote 默认主题，不做皮肤市场。
- 固定暗色代码高亮可以保留；代码块外层容器、工具栏、边框和弹窗必须跟随应用 token。
- 如果后续继续瘦身 `theme.css`，必须保持 `--color-accent*` 兼容别名到 `--color-action*`，直到旧样式完全迁移。
