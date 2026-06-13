const RELATION_LABELS = {
  RELATED_TO: '相关',
  USES: '使用',
  USED_BY: '被使用',
  DEPENDS_ON: '依赖',
  DEPENDS: '依赖',
  REQUIRES: '需要',
  CAUSES: '导致',
  CAUSED_BY: '由其导致',
  ENABLES: '使能',
  SUPPORTS: '支持',
  PART_OF: '属于',
  HAS_PART: '包含',
  CONTAINS: '包含',
  INCLUDES: '包含',
  EXAMPLE: '示例',
  INSTANCE_OF: '实例',
  IMPLEMENTS: '实现',
  EXTENDS: '扩展',
  CONFIGURES: '配置',
  CONNECTS_TO: '连接到',
  STORES: '存储',
  QUERIES: '查询',
  PRODUCES: '产生',
  CONSUMES: '消费',
  REFERENCES: '引用',
  CONTRASTS_WITH: '对比',
  PRECEDES: '先于',
  FOLLOWS: '后续',
  TRIGGERS: '触发',
  SOLVES: '解决',
  DESCRIBES: '描述',
  DEFINES: '定义',
  AFFECTS: '影响',
  MANAGES: '管理',
  OWNS: '拥有',
  CREATED_BY: '由其创建',
  CREATES: '创建',
  BELONGS_TO: '归属',
  SIMILAR_TO: '相似',
  LOCATED_IN: '位于',
  RUNS_ON: '运行于',
  BUILT_WITH: '基于',
  TESTS: '测试',
  VALIDATES: '验证',
  OPTIMIZES: '优化',
  SECURES: '保护'
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
 * 将图谱关系类型转换为中文展示名。
 *
 * <p>后端关系枚举仍作为筛选和证据查询 key 使用，这里只处理前端展示文案。</p>
 */
export function formatRelationType(type) {
  const rawValue = String(type || 'RELATED_TO').trim()
  if (!rawValue) {
    return '相关'
  }
  if (/[\u4e00-\u9fff]/.test(rawValue)) {
    return rawValue
  }
  const normalized = rawValue.replace(/[-\s]+/g, '_').toUpperCase()
  return RELATION_LABELS[normalized] || `自定义关系：${normalized.replace(/_/g, ' ')}`
}
