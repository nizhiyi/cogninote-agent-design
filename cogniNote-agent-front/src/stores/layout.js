import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 定义应用壳层布局的 Pinia Store。
 * <p>侧栏折叠状态被标题栏按钮和外层布局共同使用，独立出来能避免组件间事件绕线。</p>
 */
export const useLayoutStore = defineStore('layout', () => {
  const isSidebarCollapsed = ref(false)
  const sidebarToggleTitle = computed(() =>
    isSidebarCollapsed.value ? '展开聊天记录栏' : '隐藏聊天记录栏'
  )

  /**
   * 切换聊天记录栏显示状态。
   * <p>这里只影响布局，不改变当前草稿、会话或消息数据。</p>
   */
  function toggleSidebar() {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
  }

  return {
    isSidebarCollapsed,
    sidebarToggleTitle,
    toggleSidebar
  }
})
