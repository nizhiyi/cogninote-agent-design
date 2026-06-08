<script setup>
// stat-grid 负责 业务 页面或组件的状态组织、用户交互和后端同步。
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
