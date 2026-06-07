<script setup>
import { computed } from 'vue'
import MarkdownRender, { enableMermaid } from 'markstream-vue'
import 'markstream-vue/index.css'
import { useThemeStore } from '../stores/theme'

// markstream-vue 把 Mermaid 作为可选 peer，必须显式启用 loader 才会渲染 ```mermaid 代码块。
enableMermaid()

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
const isStreaming = computed(() => !props.final)
const mermaidProps = {
  maxHeight: '70vh',
  showHeader: true,
  showModeToggle: true,
  showCopyButton: true,
  showExportButton: true,
  showFullscreenButton: true,
  showCollapseButton: true,
  showZoomControls: true,
  enableWheelZoom: true,
  isStrict: true,
  enableMermaidInteractions: false
}
</script>

<template>
  <div class="ai-markdown-content">
    <MarkdownRender
      custom-id="cogninote-chat"
      :content="props.content || props.emptyText || ''"
      :final="props.final"
      :is-dark="themeStore.isDark"
      :mermaid-props="mermaidProps"
      html-policy="escape"
      :max-live-nodes="isStreaming ? 0 : 200"
      :batch-rendering="isStreaming"
      :render-batch-size="16"
      :render-batch-delay="8"
      :render-batch-budget-ms="4"
      :fade="false"
      :typewriter="isStreaming"
      :smooth-streaming="isStreaming"
    />
  </div>
</template>
