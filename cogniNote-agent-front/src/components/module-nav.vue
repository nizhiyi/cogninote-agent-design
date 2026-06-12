<script setup>
/**
 * 模块入口导航。
 *
 * <p>items 由页面配置提供，组件只负责 RouterLink 渲染，避免在通用导航里硬编码业务路由。</p>
 */
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
