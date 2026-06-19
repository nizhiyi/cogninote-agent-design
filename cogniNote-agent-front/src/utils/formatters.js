const RELATION_LABELS = {
  RELATED_TO: '相关',
  RELATES_TO: '相关',
  HAS: '包含',
  USES: '使用',
  USED_BY: '使用方是',
  USED_FOR: '用于',
  DEPENDS_ON: '依赖',
  DEPENDS: '依赖',
  DEPENDENT_ON: '依赖',
  REQUIRES: '需要',
  CAUSES: '导致',
  CAUSED_BY: '原因是',
  RESULTS_IN: '导致',
  LEADS_TO: '导致',
  ENABLES: '使能',
  ENABLED_BY: '启用方是',
  SUPPORTS: '支持',
  SUPPORTED_BY: '支持方是',
  PART_OF: '属于',
  HAS_PART: '包含',
  HAS_TYPE: '类型是',
  HAS_SCOPE: '作用域是',
  HAS_PROPERTY: '属性是',
  HAS_VALUE: '值为',
  HAS_PARENT: '上级是',
  HAS_CHILD: '下级是',
  CONTAINS: '包含',
  INCLUDES: '包含',
  COMPOSED_OF: '由其组成',
  SCOPE: '作用域是',
  EXAMPLE: '示例',
  EXAMPLES: '示例',
  EXEMPLIFIES: '举例说明',
  INSTANCE_OF: '实例',
  IS_A: '是一种',
  IS_AN: '是一种',
  TYPE_OF: '类型是',
  IMPLEMENTS: '实现',
  IMPLEMENTED_BY: '实现者是',
  EXTENDS: '扩展',
  CONFIGURES: '配置',
  CONFIGURED_BY: '配置方是',
  CONTROLS: '控制',
  CONTROLLED_BY: '控制方是',
  CONNECTS_TO: '连接到',
  CONNECTED_TO: '连接到',
  LINKS_TO: '连接到',
  LINKED_TO: '连接到',
  STORES: '存储',
  STORED_IN: '存储于',
  QUERIES: '查询',
  READS: '读取',
  WRITES: '写入',
  CALLS: '调用',
  CALLED_BY: '调用方是',
  EXPOSES: '暴露',
  PROVIDES: '提供',
  PROVIDED_BY: '提供方是',
  PRODUCES: '产生',
  PRODUCED_BY: '产生方是',
  CONSUMES: '消费',
  CONSUMED_BY: '消费方是',
  REFERENCES: '引用',
  REFERENCED_BY: '引用方是',
  CONTRASTS_WITH: '对比',
  COMPARED_WITH: '对比',
  ALTERNATIVE_TO: '可替代',
  ANALOGOUS_TO: '类似于',
  PRECEDES: '先于',
  FOLLOWS: '后续',
  TRIGGERS: '触发',
  TRIGGERED_BY: '触发方是',
  SOLVES: '解决',
  DESCRIBES: '描述',
  DESCRIBED_BY: '描述方是',
  DEFINES: '定义',
  DEFINED_BY: '定义方是',
  AFFECTS: '影响',
  MANAGES: '管理',
  MANAGED_BY: '管理方是',
  OWNS: '拥有',
  OWNED_BY: '拥有方是',
  CREATED_BY: '创建者是',
  CREATES: '创建',
  BELONGS_TO: '归属',
  SIMILAR_TO: '相似',
  LOCATED_IN: '位于',
  RUNS_ON: '运行于',
  BUILT_WITH: '基于',
  CLEARS: '清除',
  CLEARED_BY: '清除方是',
  TESTS: '测试',
  VALIDATES: '验证',
  OPTIMIZES: '优化',
  SECURES: '保护'
}

const RELATION_ACTION_LABELS = {
  USES: '使用',
  USED: '使用',
  DEPENDS: '依赖',
  REQUIRES: '需要',
  CAUSES: '导致',
  ENABLES: '使能',
  ENABLED: '启用',
  SUPPORTS: '支持',
  SUPPORTED: '支持',
  IMPLEMENTS: '实现',
  IMPLEMENTED: '实现',
  CONFIGURES: '配置',
  CONFIGURED: '配置',
  CONTROLS: '控制',
  CONTROLLED: '控制',
  CONNECTS: '连接',
  CONNECTED: '连接',
  LINKS: '连接',
  LINKED: '连接',
  STORES: '存储',
  STORED: '存储',
  QUERIES: '查询',
  READS: '读取',
  WRITES: '写入',
  CALLS: '调用',
  CALLED: '调用',
  EXPOSES: '暴露',
  PROVIDES: '提供',
  PROVIDED: '提供',
  PRODUCES: '产生',
  PRODUCED: '产生',
  CONSUMES: '消费',
  CONSUMED: '消费',
  REFERENCES: '引用',
  REFERENCED: '引用',
  COMPARES: '对比',
  COMPARED: '对比',
  CONTRASTS: '对比',
  TRIGGERS: '触发',
  TRIGGERED: '触发',
  DESCRIBES: '描述',
  DESCRIBED: '描述',
  DEFINES: '定义',
  DEFINED: '定义',
  AFFECTS: '影响',
  MANAGES: '管理',
  MANAGED: '管理',
  OWNS: '拥有',
  OWNED: '拥有',
  CREATES: '创建',
  CREATED: '创建',
  CLEARS: '清除',
  CLEARED: '清除',
  TESTS: '测试',
  VALIDATES: '验证',
  OPTIMIZES: '优化',
  SECURES: '保护'
}

const RELATION_OBJECT_LABELS = {
  TYPE: '类型',
  SCOPE: '作用域',
  PART: '组成部分',
  PROPERTY: '属性',
  VALUE: '值',
  PARENT: '上级',
  CHILD: '下级',
  SOURCE: '来源',
  TARGET: '目标',
  RESULT: '结果',
  CAUSE: '原因',
  EFFECT: '影响',
  EXAMPLE: '示例',
  INSTANCE: '实例',
  ROLE: '角色',
  OWNER: '拥有方',
  CONFIG: '配置',
  SETTING: '配置项',
  STATE: '状态',
  STATUS: '状态',
  CACHE: '缓存',
  CLASS: '类',
  COMPONENT: '组件',
  CONCEPT: '概念',
  FRAMEWORK: '框架',
  METHOD: '方法',
  MODULE: '模块',
  PRODUCT: '产品',
  SERVICE: '服务'
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
  return RELATION_LABELS[normalized] || formatUnknownRelationType(normalized)
}

function formatUnknownRelationType(normalized) {
  const words = normalized.split('_').filter(Boolean)
  if (!words.length) {
    return '相关'
  }

  // LLM 可能生成新的关系码；兜底文案要解释关系语义，而不是把枚举名原样暴露给用户。
  if (words[0] === 'HAS') {
    const objectLabel = getRelationObjectLabel(words.slice(1))
    if (!objectLabel.text) {
      return '包含'
    }
    return objectLabel.translated ? `${objectLabel.text}是` : `包含 ${objectLabel.text}`
  }

  if (words[0] === 'IS') {
    const objectLabel = getRelationObjectLabel(words.slice(1).filter((word) => word !== 'A' && word !== 'AN'))
    return objectLabel.text ? `是${objectLabel.text}` : '是一种'
  }

  if (words.at(-1) === 'BY') {
    const actionLabel = formatRelationAction(words.slice(0, -1))
    return actionLabel ? `${actionLabel}方是` : toReadableRelationCode(words)
  }

  if (words.at(-1) === 'TO') {
    const actionLabel = formatRelationAction(words.slice(0, -1))
    return actionLabel ? `${actionLabel}到` : toReadableRelationCode(words)
  }

  if (words.at(-1) === 'WITH') {
    const actionLabel = formatRelationAction(words.slice(0, -1))
    return actionLabel || toReadableRelationCode(words)
  }

  return toReadableRelationCode(words)
}

function formatRelationAction(words) {
  const labels = words.map((word) => RELATION_ACTION_LABELS[word])
  return labels.every(Boolean) ? labels.join('') : ''
}

function getRelationObjectLabel(words) {
  const labels = words.map((word) => RELATION_OBJECT_LABELS[word])
  if (labels.every(Boolean)) {
    return {
      text: labels.join(''),
      translated: true
    }
  }
  return {
    text: words.length ? toReadableRelationCode(words) : '',
    translated: false
  }
}

function toReadableRelationCode(words) {
  return words.map((word) => word.charAt(0) + word.slice(1).toLowerCase()).join(' ')
}
