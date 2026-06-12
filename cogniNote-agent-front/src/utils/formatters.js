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
