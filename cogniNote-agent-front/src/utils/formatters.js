const RELATION_TYPE_LABELS = {
  RELATED: '相关',
  STRUCTURAL: '结构',
  FUNCTIONAL: '功能',
  CAUSAL: '因果',
  SEQUENCE: '顺序',
  OWNERSHIP: '归属',
  COMPARISON: '对比',
  CONSTRAINT: '约束'
}

/**
 * 将后端返回的字节数转换为文件体积文案。
 *
 * <p>这里不做本地化单位切换，保持知识库列表和系统信息页的展示口径一致。</p>
 */
export function formatFileSize(size) {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 格式化后端毫秒时间戳。
 *
 * <p>空值统一展示为 -，避免列表中未索引或未生成图谱的状态被误读成当前时间。</p>
 */
export function formatTime(timestamp) {
  if (!timestamp) {
    return '-'
  }
  return new Date(timestamp).toLocaleString()
}

/**
 * 格式化检索分数。
 *
 * <p>BM25、向量和 RRF 分数来源不同，这里只固定小数位，具体含义由调用组件标注。</p>
 */
export function formatScore(score) {
  return typeof score === 'number' ? score.toFixed(3) : '-'
}

/**
 * 将图谱关系粗分类转换为中文展示名。
 *
 * <p>关系边的具体中文谓词来自后端 displayLabel；这里只服务粗分类筛选和旧缓存兜底。</p>
 */
export function formatRelationType(type) {
  const rawValue = String(type || 'RELATED').trim()
  if (!rawValue) {
    return '相关'
  }
  if (/[\u4e00-\u9fff]/.test(rawValue)) {
    return rawValue
  }
  const normalized = rawValue.replace(/[-\s]+/g, '_').toUpperCase()
  return RELATION_TYPE_LABELS[normalized] || '相关'
}
