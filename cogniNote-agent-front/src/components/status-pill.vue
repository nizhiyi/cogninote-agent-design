<script setup>
/**
 * 小型状态标签。
 *
 * <p>state 直接参与 CSS modifier 命名，新增状态时需要同步补充样式。</p>
 */
defineProps({
  label: {
    type: String,
    required: true
  },
  state: {
    type: String,
    default: 'ok'
  }
})
</script>

<template>
  <span :class="['status-pill', `status-pill--${state}`]">{{ label }}</span>
</template>
