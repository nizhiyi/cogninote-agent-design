<script setup>
import MarkdownRender from 'markstream-vue'
import 'markstream-vue/index.css'
import { useThemeStore } from '../stores/theme'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  emptyText: {
    type: String,
    default: ''
  },
  final: {
    type: Boolean,
    default: true
  }
})

const themeStore = useThemeStore()
</script>

<template>
  <div class="ai-markdown-content">
    <MarkdownRender
      custom-id="cogninote-chat"
      :content="props.content || props.emptyText || ''"
      :final="props.final"
      :is-dark="themeStore.isDark"
      html-policy="escape"
      :max-live-nodes="0"
      :batch-rendering="true"
      :render-batch-size="16"
      :render-batch-delay="8"
      :render-batch-budget-ms="4"
      :fade="false"
      :typewriter="true"
      :smooth-streaming="true"
    />
  </div>
</template>
