import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles/base.css'

// Pinia 必须在 router 前注册，路由视图加载时各页面才能安全读取 store。
createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app')
