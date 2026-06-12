<script setup>
/**
 * 设置页和知识库页复用的统计网格。
 *
 * <p>columns 会写入 CSS 变量，调用方需要传入稳定列数，避免响应式布局在刷新数据时跳动。</p>
 */
defineProps({
  items: {
    type: Array,
    required: true
  },
  columns: {
    type: Number,
    default: 4
  }
})
</script>

<template>
  <section class="stat-grid" :style="{ '--stat-columns': columns }">
    <div v-for="item in items" :key="item.label">
      <span>{{ item.label }}</span>
      <strong :class="{ 'path-text': item.mono }">{{ item.value }}</strong>
    </div>
  </section>
</template>
