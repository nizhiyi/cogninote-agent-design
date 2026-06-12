<script setup>
/**
 * 系统信息设置卡片。
 *
 * <p>descriptions 由设置页聚合系统状态和索引状态后传入，本组件只负责展示和刷新事件转发。</p>
 */
defineProps({
  descriptions: {
    type: Array,
    required: true
  },
  isSystemLoading: {
    type: Boolean,
    default: false
  },
  isIndexLoading: {
    type: Boolean,
    default: false
  },
  githubUrl: {
    type: String,
    required: true
  }
})

defineEmits(['refresh-system', 'refresh-index'])
</script>

<template>
  <section class="system-status-card">
    <el-descriptions class="settings-descriptions" :column="2" border>
      <el-descriptions-item
        v-for="item in descriptions"
        :key="item.label"
        :label="item.label"
      >
        <span :class="{ 'path-text': item.mono }">{{ item.value }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="GitHub仓库">
        <el-link :href="githubUrl" target="_blank" type="primary">
          ItQianChen/cogninote-agent-design
        </el-link>
      </el-descriptions-item>
    </el-descriptions>

    <div class="button-row">
      <el-button :loading="isSystemLoading" @click="$emit('refresh-system')">刷新系统状态</el-button>
      <el-button :loading="isIndexLoading" @click="$emit('refresh-index')">刷新索引状态</el-button>
    </div>
  </section>
</template>
