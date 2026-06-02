<script setup>
import { formatScore } from '../utils/formatters'

defineProps({
  result: {
    type: Object,
    default: null
  }
})
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
        <span>{{ formatScore(hit.score) }}</span>
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
