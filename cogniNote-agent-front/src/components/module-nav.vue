<script setup>
// module-nav 负责 业务 页面或组件的状态组织、用户交互和后端同步。
defineProps({
  items: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <nav class="module-nav" aria-label="功能入口">
    <RouterLink
      v-for="item in items"
      :key="item.id"
      :to="item.to"
      class="module-tab"
    >
      <span class="module-tab__top">
        <strong>{{ item.name }}</strong>
        <em>{{ item.state }}</em>
      </span>
      <span>{{ item.description }}</span>
    </RouterLink>
  </nav>
</template>
