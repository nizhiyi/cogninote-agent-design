import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/chat-view.vue'
import KnowledgeView from '../views/knowledge-view.vue'
import ModelConfigView from '../views/model-config-view.vue'
import SettingsView from '../views/settings-view.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'chat' } },
    { path: '/chat', name: 'chat', component: ChatView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/model-config', name: 'model-config', component: ModelConfigView },
    { path: '/settings', name: 'settings', component: SettingsView }
  ]
})
