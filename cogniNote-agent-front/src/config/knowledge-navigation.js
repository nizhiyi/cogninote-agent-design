export const DEFAULT_KNOWLEDGE_PANEL = 'folders'

export const KNOWLEDGE_PANEL_OPTIONS = [
  { id: 'folders', label: '资料总览' },
  { id: 'health', label: '可信状态' },
  { id: 'directories', label: '目录管理' },
  { id: 'search', label: '检索测试' },
  { id: 'graph', label: '知识图谱' }
]

const KNOWLEDGE_PANEL_IDS = new Set(KNOWLEDGE_PANEL_OPTIONS.map((option) => option.id))

/**
 * 标准化路由里的知识库面板参数。
 *
 * Vue Router query 可能是数组或非法字符串；这里统一兜底，保证侧栏和主面板始终选中同一个视图。
 */
export function normalizeKnowledgePanel(value) {
  const panelId = Array.isArray(value) ? value[0] : value
  return KNOWLEDGE_PANEL_IDS.has(panelId) ? panelId : DEFAULT_KNOWLEDGE_PANEL
}
