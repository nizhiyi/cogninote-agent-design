<script setup>
import WorkspaceRail from './workspace-rail.vue'
import ContextSidebar from './context-sidebar.vue'
import { useLayoutStore } from '../stores/layout'

const layoutStore = useLayoutStore()

/**
 * 桌面工作区布局壳。
 *
 * <p>全局导航、上下文侧栏和主工作区在这里组合，避免各业务页面重复处理侧栏折叠类名。</p>
 */
</script>

<template>
  <main
    class="workspace-shell"
    :class="{ 'workspace-shell--context-collapsed': layoutStore.isContextSidebarCollapsed }"
  >
    <WorkspaceRail />
    <ContextSidebar />
    <section class="workspace-main" aria-label="主工作区">
      <slot />
    </section>
  </main>
</template>
