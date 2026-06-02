<script setup>
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
