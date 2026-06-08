import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

const THEME_STORAGE_KEY = 'cogninote-theme'
const DEFAULT_THEME = 'dark'

export const THEME_OPTIONS = [
    {label: '夜间', value: 'dark'},
    {label: '日间', value: 'light'}
]

/**
 * 定义 业务 的 Pinia Store。
 * <p>集中维护响应式状态、派生值和异步动作，组件只消费 Store 暴露的接口。</p>
 */
export const useThemeStore = defineStore('theme', () => {
    const theme = ref(readInitialTheme())
    const isDark = computed(() => theme.value === 'dark')

    /**
     * 更新 set Theme 对应的状态。
     * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
     */
    function setTheme(nextTheme) {
        theme.value = THEME_OPTIONS.some((option) => option.value === nextTheme) ? nextTheme : DEFAULT_THEME
        applyTheme()
        window.localStorage.setItem(THEME_STORAGE_KEY, theme.value)
    }

    /**
     * 更新 apply Theme 对应的状态。
     * <p>状态写入后需要保持控件、Store 和后端快照一致。</p>
     */
    function applyTheme() {
        // 主题类挂在 html 上，避免页面切换时丢失；CSS 模块只需要按 theme-light 做覆盖。
        document.documentElement.classList.toggle('theme-light', theme.value === 'light')
        document.documentElement.classList.toggle('theme-dark', theme.value === 'dark')
        document.documentElement.classList.toggle('dark', theme.value === 'dark')
    }

    applyTheme()

    return {
        theme,
        isDark,
        setTheme,
        applyTheme
    }
})

/**
 * 执行 业务 中的 read Initial Theme 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
function readInitialTheme() {
    if (typeof window === 'undefined') {
        return DEFAULT_THEME
    }
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return THEME_OPTIONS.some((option) => option.value === storedTheme) ? storedTheme : DEFAULT_THEME
}
