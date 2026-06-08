<script setup>
// status-pill 负责 业务 页面或组件的状态组织、用户交互和后端同步。
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
