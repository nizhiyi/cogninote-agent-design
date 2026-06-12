<script setup>
import WorkspaceShell from './workspace-shell.vue'

/**
 * 应用外壳入口。
 *
 * <p>保留这一层是为了让未来非工作区页面也能复用同一 layout 包装，而不直接依赖 workspace-shell。</p>
 */
</script>

<template>
  <WorkspaceShell>
    <slot />
  </WorkspaceShell>
</template>
