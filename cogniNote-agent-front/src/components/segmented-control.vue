<script setup>
/**
 * 字符串枚举的分段选择控件。
 *
 * <p>组件只通过 update:modelValue 抛出 option.value，调用方负责保证 value 与后端枚举一致。</p>
 */
defineProps({
  modelValue: {
    type: String,
    required: true
  },
  options: {
    type: Array,
    required: true
  },
  label: {
    type: String,
    default: '选项'
  }
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <div class="segmented-control" role="group" :aria-label="label">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="{ active: modelValue === option.value }"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
