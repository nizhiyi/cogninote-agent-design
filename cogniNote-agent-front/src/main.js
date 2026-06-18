import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
// ElMessage / ElMessageBox 是服务式组件，模板层不会自动补齐它们的样式入口。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import './styles/base.css'

// Pinia 必须在 router 前注册，路由视图加载时各页面才能安全读取 store。
createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app')
