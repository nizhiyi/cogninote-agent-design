/**
 * 格式化 format File Size 展示文本。
 * <p>统一页面上的数字、时间或语言标签展示口径。</p>
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
 * 格式化 format Time 展示文本。
 * <p>统一页面上的数字、时间或语言标签展示口径。</p>
 */
export function formatTime(timestamp) {
  if (!timestamp) {
    return '-'
  }
  return new Date(timestamp).toLocaleString()
}

/**
 * 格式化 format Score 展示文本。
 * <p>统一页面上的数字、时间或语言标签展示口径。</p>
 */
export function formatScore(score) {
  return typeof score === 'number' ? score.toFixed(3) : '-'
}
