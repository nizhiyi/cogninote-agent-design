<script setup>
// markdown-renderer 负责 业务 页面或组件的状态组织、用户交互和后端同步。
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  emptyText: {
    type: String,
    default: ''
  }
})

const markdown = new MarkdownIt({
  // 模型输出来自外部服务，原始 HTML 必须禁用；只允许 Markdown 语法生成受控标签。
  html: false,
  linkify: true,
  breaks: true,
  typographer: false
})

const renderLinkOpen = markdown.renderer.rules.link_open

markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  setTokenAttr(tokens[index], 'target', '_blank')
  setTokenAttr(tokens[index], 'rel', 'noopener noreferrer')
  return renderLinkOpen ? renderLinkOpen(tokens, index, options, env, self) : self.renderToken(tokens, index, options)
}

const renderedHtml = computed(() => markdown.render(props.content || props.emptyText || ''))

/**
 * 更新 set Token Attr 对应的状态。
 * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
 */
function setTokenAttr(token, name, value) {
  const attrIndex = token.attrIndex(name)
  if (attrIndex < 0) {
    token.attrPush([name, value])
    return
  }
  token.attrs[attrIndex][1] = value
}
</script>

<template>
  <div class="markdown-content" v-html="renderedHtml"></div>
</template>
