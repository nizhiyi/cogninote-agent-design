<script setup>
import { formatScore } from '../utils/formatters'

/**
 * 知识库检索结果列表。
 *
 * <p>不同检索模式的 score 含义不同，展示时必须标注 BM25、Vector 或 RRF，避免用户横向误比。</p>
 */
defineProps({
  result: {
    type: Object,
    default: null
  }
})

function scoreLabel(mode) {
  if (mode === 'HYBRID') {
    return 'RRF'
  }
  if (mode === 'KEYWORD') {
    return 'BM25'
  }
  if (mode === 'VECTOR') {
    return 'Vector'
  }
  return 'Score'
}
</script>

<template>
  <section v-if="result" class="search-results">
    <div class="section-title-line">
      <h3>检索结果</h3>
      <span>{{ result.mode }} / {{ result.hits.length }} hits</span>
    </div>

    <p v-if="result.hits.length === 0" class="panel-message">没有命中文档片段。</p>

    <article v-for="hit in result.hits" v-else :key="hit.chunkId" class="search-hit">
      <div class="search-hit__top">
        <h4>{{ hit.fileName }}</h4>
        <span>{{ scoreLabel(result.mode) }} {{ formatScore(hit.score) }}</span>
      </div>
      <p class="path-text">{{ hit.sourcePath }}</p>
      <p class="hit-preview">{{ hit.preview }}</p>
      <div class="document-meta">
        <span v-if="hit.heading">标题：{{ hit.heading }}</span>
        <span v-if="hit.pageNumber">页码：{{ hit.pageNumber }}</span>
        <span v-if="hit.keywordScore !== null && hit.keywordScore !== undefined">
          BM25 {{ formatScore(hit.keywordScore) }}
        </span>
        <span v-if="hit.vectorScore !== null && hit.vectorScore !== undefined">
          Vector {{ formatScore(hit.vectorScore) }}
        </span>
      </div>
    </article>
  </section>
</template>
