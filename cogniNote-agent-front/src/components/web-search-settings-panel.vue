<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { BookOpen, ExternalLink, FlaskConical, Gauge, KeyRound, Save, Search } from 'lucide-vue-next'
import { useWebSearchSettingsStore } from '../stores/web-search-settings'

const webSearchSettingsStore = useWebSearchSettingsStore()
const helpDialogVisible = ref(false)
const strategyHelpDialogVisible = ref(false)
const exaDocsLinks = [
  {
    label: 'API Keys',
    description: '进入 Exa Dashboard 创建或复制 API Key。',
    url: 'https://dashboard.exa.ai/api-keys'
  },
  {
    label: 'Search API',
    description: '查看 /search 的鉴权、参数和响应结构。',
    url: 'https://exa.ai/docs/reference/search'
  },
  {
    label: 'Agent 指南',
    description: '面向 coding agents 的搜索示例和常见避坑。',
    url: 'https://exa.ai/docs/reference/search-api-guide-for-coding-agents'
  },
  {
    label: '价格与额度',
    description: '免费额度和实际计费以 Exa 官网为准。',
    url: 'https://exa.ai/pricing'
  }
]
const strategyHelpItems = [
  {
    label: '搜索模式',
    value: 'auto / fast',
    description: 'auto 平衡速度和质量，适合默认使用；fast 延迟更低，适合更看重响应速度的交互场景。'
  },
  {
    label: '单次结果数',
    value: '1 - 10',
    description: '一次搜索返回的网页来源数量。结果越多，引用更充分，但延迟、费用和模型上下文占用也会增加。'
  },
  {
    label: '单轮调用上限',
    value: '1 - 3',
    description: '限制模型在一轮回复里最多调用 searchWeb 的次数，防止反复搜索导致等待时间和额度消耗失控。'
  },
  {
    label: '超时毫秒',
    value: '1000 - 30000',
    description: '后端等待 Exa 搜索返回的最长时间。网络较慢时可以调高，日常使用建议保持 10000 左右。'
  }
]
const apiKeyStatusLabel = computed(() => {
  const hasDraftApiKey = Boolean(webSearchSettingsStore.settings.apiKey.trim())
  if (webSearchSettingsStore.settings.apiKeyConfigured && hasDraftApiKey) {
    return '待更新'
  }
  if (webSearchSettingsStore.settings.apiKeyConfigured) {
    return '已配置'
  }
  return hasDraftApiKey ? '待保存' : '未配置'
})
const webSearchStatusLabel = computed(() => {
  if (!webSearchSettingsStore.settings.enabled) {
    return '联网未启用'
  }
  if (webSearchSettingsStore.settings.apiKeyConfigured) {
    return '联网可用'
  }
  return webSearchSettingsStore.settings.apiKey.trim() ? '待保存' : '联网未配置'
})

onMounted(() => {
  webSearchSettingsStore.fetchSettings({ force: true })
})

async function handleSave() {
  await webSearchSettingsStore.saveSettings()
  if (webSearchSettingsStore.error) {
    ElMessage.error(webSearchSettingsStore.error)
    return
  }
  ElMessage.success(webSearchSettingsStore.message || '联网搜索设置已保存')
}

async function handleTest() {
  await webSearchSettingsStore.testSettings()
  if (webSearchSettingsStore.error) {
    ElMessage.error(webSearchSettingsStore.error)
    return
  }
  ElMessage.success(webSearchSettingsStore.message || '联网搜索测试通过')
}

function handleEnabledChange(value) {
  if (value && !webSearchSettingsStore.canEnable) {
    ElMessage.warning('请先粘贴 Exa API Key，再启用联网搜索')
    webSearchSettingsStore.patchSettings({ enabled: false })
    return
  }
  webSearchSettingsStore.patchSettings({ enabled: value })
}
</script>

<template>
  <section class="settings-panel web-search-settings-panel" aria-labelledby="web-search-title">
    <header class="settings-panel__header">
      <p class="eyebrow">策略</p>
      <h3 id="web-search-title">联网搜索</h3>
    </header>

    <article class="settings-card web-search-status-card" aria-label="联网搜索状态">
      <div class="web-search-status-card__item">
        <Search aria-hidden="true" />
        <span>
          <small>当前状态</small>
          <strong>{{ webSearchStatusLabel }}</strong>
        </span>
      </div>
      <div class="web-search-status-card__item">
        <KeyRound aria-hidden="true" />
        <span>
          <small>API Key</small>
          <strong>{{ apiKeyStatusLabel }}</strong>
        </span>
      </div>
      <div class="web-search-status-card__item">
        <FlaskConical aria-hidden="true" />
        <span>
          <small>Provider</small>
          <strong>EXA</strong>
        </span>
      </div>
    </article>

    <article class="settings-card web-search-form-card" v-loading="webSearchSettingsStore.loading">
      <div class="web-search-toggle-row">
        <div>
          <p class="eyebrow">基础配置</p>
          <h4>启用联网搜索</h4>
          <p class="hint-message">用户在聊天输入区开启联网后，本轮才会把 searchWeb 工具挂给模型。</p>
        </div>
        <div class="web-search-toggle-control">
          <el-switch
            :model-value="webSearchSettingsStore.settings.enabled"
            :disabled="!webSearchSettingsStore.canEnable"
            :title="webSearchSettingsStore.canEnable ? '' : '先粘贴 Exa API Key 后才能启用联网搜索'"
            active-text="启用"
            inactive-text="关闭"
            @update:model-value="handleEnabledChange"
          />
          <p v-if="!webSearchSettingsStore.canEnable" class="warning-message web-search-toggle-warning">
            检测到 API Key 后才能启用联网搜索。
          </p>
        </div>
      </div>

      <el-form label-position="top" class="web-search-form">
        <section class="web-search-form-section">
          <div class="web-search-section-heading web-search-section-heading--with-action">
            <div>
              <h4>连接配置</h4>
              <p>只保存配置状态到前端，API Key 明文不会回显。</p>
            </div>
            <el-button class="web-search-help-button" @click="helpDialogVisible = true">
              <BookOpen aria-hidden="true" />
              配置说明
            </el-button>
          </div>

          <div class="web-search-form__grid web-search-form__grid--connection">
            <el-form-item label="Provider">
              <el-input model-value="EXA" disabled />
            </el-form-item>

            <el-form-item label="API Key">
              <el-input
                :model-value="webSearchSettingsStore.settings.apiKey"
                type="password"
                show-password
                autocomplete="off"
                :placeholder="webSearchSettingsStore.settings.apiKeyConfigured ? '已配置，留空保存则沿用旧 Key' : '粘贴 Exa API Key'"
                @update:model-value="webSearchSettingsStore.patchSettings({ apiKey: $event })"
              />
            </el-form-item>
          </div>
        </section>

        <section class="web-search-form-section">
          <div class="web-search-section-heading web-search-section-heading--with-action">
            <div>
              <h4>调用策略</h4>
              <p>限制单轮搜索次数和超时，避免模型反复调用工具。</p>
            </div>
            <el-button class="web-search-help-button" @click="strategyHelpDialogVisible = true">
              <Gauge aria-hidden="true" />
              策略说明
            </el-button>
          </div>

          <div class="web-search-form__grid web-search-form__grid--runtime">
            <el-form-item label="搜索模式">
              <el-segmented
                :model-value="webSearchSettingsStore.settings.searchMode"
                :options="[
                  { label: 'auto', value: 'auto' },
                  { label: 'fast', value: 'fast' }
                ]"
                @update:model-value="webSearchSettingsStore.patchSettings({ searchMode: $event })"
              />
            </el-form-item>

            <el-form-item label="单次结果数">
              <el-input-number
                :model-value="webSearchSettingsStore.settings.maxResults"
                :min="1"
                :max="10"
                @update:model-value="webSearchSettingsStore.patchSettings({ maxResults: $event })"
              />
            </el-form-item>
            <el-form-item label="单轮调用上限">
              <el-input-number
                :model-value="webSearchSettingsStore.settings.maxCallsPerTurn"
                :min="1"
                :max="3"
                @update:model-value="webSearchSettingsStore.patchSettings({ maxCallsPerTurn: $event })"
              />
            </el-form-item>
            <el-form-item label="超时毫秒">
              <el-input-number
                :model-value="webSearchSettingsStore.settings.timeoutMs"
                :min="1000"
                :max="30000"
                :step="1000"
                @update:model-value="webSearchSettingsStore.patchSettings({ timeoutMs: $event })"
              />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <div class="web-search-footer">
        <p v-if="webSearchSettingsStore.error" class="error-message web-search-feedback">
          {{ webSearchSettingsStore.error }}
        </p>
        <p v-else-if="webSearchSettingsStore.message" class="hint-message web-search-feedback">
          {{ webSearchSettingsStore.message }}
        </p>
        <span v-else class="web-search-feedback" aria-hidden="true"></span>

        <div class="web-search-footer__actions">
          <el-button
            :loading="webSearchSettingsStore.testing"
            :disabled="webSearchSettingsStore.saving || webSearchSettingsStore.loading"
            @click="handleTest"
          >
            <FlaskConical aria-hidden="true" />
            测试连接
          </el-button>
          <el-button
            type="primary"
            :loading="webSearchSettingsStore.saving"
            :disabled="webSearchSettingsStore.testing || webSearchSettingsStore.loading"
            @click="handleSave"
          >
            <Save aria-hidden="true" />
            保存
          </el-button>
        </div>
      </div>
    </article>

    <el-dialog
      v-model="helpDialogVisible"
      class="web-search-help-dialog"
      title="Exa 配置说明"
      width="min(720px, calc(100vw - 32px))"
      align-center
    >
      <section class="web-search-help">
        <div class="web-search-help__intro">
          <BookOpen aria-hidden="true" />
          <div>
            <h4>接入前准备</h4>
            <p>当前联网搜索 Provider 固定为 EXA。你只需要在 Exa Dashboard 创建 API Key，粘贴到本页后保存，再到聊天输入区开启联网搜索。</p>
          </div>
        </div>

        <ol class="web-search-help__steps">
          <li>
            <strong>创建 API Key</strong>
            <span>打开 Exa Dashboard 的 API Keys 页面，登录后创建并复制一个可用 Key。</span>
          </li>
          <li>
            <strong>保存到 CogniNote</strong>
            <span>把 Key 粘贴到本页 API Key 输入框，点击保存。保存成功后页面只显示“已配置”，不会回显明文。</span>
          </li>
          <li>
            <strong>测试连接</strong>
            <span>点击测试连接确认 Key 和网络可用。聊天时仍需要在输入区手动打开联网搜索，本轮才会把 searchWeb 工具挂给模型。</span>
          </li>
          <li>
            <strong>选择调用策略</strong>
            <span>默认使用 auto；如果更看重响应速度，可以切到 fast。免费额度和价格可能调整，请以官网为准。</span>
          </li>
        </ol>

        <section class="web-search-help__docs" aria-label="Exa 官方文档">
          <h4>Exa 官方文档</h4>
          <div class="web-search-help__links">
            <a
              v-for="link in exaDocsLinks"
              :key="link.url"
              :href="link.url"
              target="_blank"
              rel="noreferrer"
            >
              <span>
                <strong>{{ link.label }}</strong>
                <small>{{ link.description }}</small>
              </span>
              <ExternalLink aria-hidden="true" />
            </a>
          </div>
        </section>
      </section>

      <template #footer>
        <div class="web-search-help-dialog__footer">
          <el-button type="primary" @click="helpDialogVisible = false">我知道了</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="strategyHelpDialogVisible"
      class="web-search-help-dialog"
      title="调用策略说明"
      width="min(720px, calc(100vw - 32px))"
      align-center
    >
      <section class="web-search-help">
        <div class="web-search-help__intro">
          <Gauge aria-hidden="true" />
          <div>
            <h4>控制联网工具的成本和稳定性</h4>
            <p>这些参数只影响模型拿到 searchWeb 工具后的搜索行为；未开启联网搜索时，后端不会挂载工具，也不会调用 Exa。</p>
          </div>
        </div>

        <div class="web-search-strategy-list">
          <article v-for="item in strategyHelpItems" :key="item.label">
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.value }}</small>
            </div>
            <p>{{ item.description }}</p>
          </article>
        </div>

        <section class="web-search-help__docs" aria-label="Exa 策略文档">
          <h4>相关文档</h4>
          <div class="web-search-help__links">
            <a href="https://exa.ai/docs/reference/search" target="_blank" rel="noreferrer">
              <span>
                <strong>Search API</strong>
                <small>查看 type、numResults 和 contents 参数。</small>
              </span>
              <ExternalLink aria-hidden="true" />
            </a>
            <a href="https://exa.ai/pricing" target="_blank" rel="noreferrer">
              <span>
                <strong>价格与额度</strong>
                <small>不同模式和结果数量的费用以官网为准。</small>
              </span>
              <ExternalLink aria-hidden="true" />
            </a>
          </div>
        </section>
      </section>

      <template #footer>
        <div class="web-search-help-dialog__footer">
          <el-button type="primary" @click="strategyHelpDialogVisible = false">我知道了</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.settings-panel.web-search-settings-panel {
  max-width: 980px;
  gap: 16px;
}

.web-search-status-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  padding: 0;
}

.web-search-status-card__item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--color-border);
}

.web-search-status-card__item:last-child {
  border-right: 0;
}

.web-search-status-card__item svg {
  width: 18px;
  height: 18px;
  color: var(--color-text-muted);
}

.web-search-status-card__item small,
.web-search-status-card__item strong {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.web-search-status-card__item small {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.web-search-status-card__item strong {
  margin-top: 4px;
  color: var(--color-text-strong);
  font-size: 14px;
  font-weight: 850;
  line-height: 1.35;
}

.web-search-form-card {
  display: grid;
  gap: 0;
  overflow: hidden;
  padding: 0;
}

.web-search-toggle-row {
  display: flex;
  min-width: 0;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-surface) 92%, transparent);
}

.web-search-toggle-row > div {
  min-width: 0;
}

.web-search-toggle-row h4,
.web-search-section-heading h4 {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 16px;
  font-weight: 850;
  line-height: 1.35;
}

.web-search-toggle-row .eyebrow {
  margin-bottom: 6px;
}

.web-search-toggle-row .hint-message {
  max-width: 660px;
  margin-top: 6px;
}

.web-search-toggle-control {
  display: grid;
  flex: 0 0 auto;
  gap: 6px;
  justify-items: end;
}

.web-search-toggle-warning {
  max-width: 240px;
  margin: 0;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.45;
  text-align: right;
}

.web-search-form {
  display: grid;
  min-width: 0;
}

.web-search-form-section {
  display: grid;
  min-width: 0;
  gap: 14px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-border);
}

.web-search-section-heading {
  display: grid;
  gap: 6px;
}

.web-search-section-heading--with-action {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
}

.web-search-section-heading p {
  max-width: 680px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.6;
}

.web-search-help-button {
  align-self: start;
}

.web-search-help-button svg {
  width: 16px;
  height: 16px;
}

.web-search-form__grid {
  display: grid;
  min-width: 0;
  gap: 14px;
  align-items: end;
}

.web-search-form__grid--connection {
  grid-template-columns: minmax(160px, 210px) minmax(280px, 1fr);
}

.web-search-form__grid--runtime {
  grid-template-columns: minmax(130px, 0.9fr) repeat(3, minmax(130px, 1fr));
}

.web-search-form :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
}

.web-search-form :deep(.el-input-number) {
  width: 100%;
}

.web-search-footer {
  display: flex;
  min-width: 0;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}

.web-search-feedback {
  min-width: 0;
  margin: 0;
}

.web-search-footer__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.web-search-footer__actions svg {
  width: 16px;
  height: 16px;
}

.web-search-help {
  display: grid;
  gap: 14px;
}

.web-search-help__intro {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 12px;
  border: 1px solid var(--color-action-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-action-soft) 58%, var(--color-surface));
}

.web-search-help__intro > svg {
  width: 20px;
  height: 20px;
  color: var(--color-action-strong);
}

.web-search-help h4 {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 15px;
  font-weight: 850;
  line-height: 1.4;
}

.web-search-help p {
  margin: 6px 0 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.65;
}

.web-search-help__steps {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.web-search-help__steps li {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-surface-muted) 36%, transparent);
}

.web-search-help__steps strong,
.web-search-help__links strong {
  color: var(--color-text-strong);
  font-size: 13px;
  font-weight: 800;
  line-height: 1.4;
}

.web-search-help__steps span,
.web-search-help__links small {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.55;
}

.web-search-strategy-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.web-search-strategy-list article {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-surface-muted) 36%, transparent);
}

.web-search-strategy-list article > div {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.web-search-strategy-list strong {
  color: var(--color-text-strong);
  font-size: 13px;
  font-weight: 850;
  line-height: 1.35;
}

.web-search-strategy-list small {
  color: var(--color-action-strong);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.35;
}

.web-search-strategy-list p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.6;
}

.web-search-help__docs {
  display: grid;
  gap: 10px;
}

.web-search-help__links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.web-search-help__links a {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 18px;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  background: var(--color-surface);
}

.web-search-help__links a:hover,
.web-search-help__links a:focus-visible {
  border-color: var(--color-action-border);
  background: var(--color-action-soft);
}

.web-search-help__links a > span {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.web-search-help__links svg {
  width: 16px;
  height: 16px;
  color: var(--color-action-strong);
}

.web-search-help-dialog__footer {
  display: flex;
  justify-content: flex-end;
}

:global(.web-search-help-dialog.el-dialog),
:global(.web-search-help-dialog .el-dialog) {
  border: 1px solid var(--color-border);
  color: var(--color-text);
  background: var(--color-surface);
}

:global(.web-search-help-dialog .el-dialog__header),
:global(.web-search-help-dialog .el-dialog__footer) {
  border-color: var(--color-border);
  color: var(--color-text-strong);
  background: var(--color-surface);
}

:global(.web-search-help-dialog .el-dialog__body) {
  max-height: min(72vh, 640px);
  overflow: auto;
  padding-top: 8px;
}

@media (max-width: 960px) {
  .settings-panel.web-search-settings-panel {
    max-width: none;
  }

  .web-search-form__grid--runtime {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .web-search-status-card,
  .web-search-form__grid--connection,
  .web-search-form__grid--runtime {
    grid-template-columns: 1fr;
  }

  .web-search-status-card__item {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .web-search-status-card__item:last-child {
    border-bottom: 0;
  }

  .web-search-section-heading--with-action,
  .web-search-help__steps,
  .web-search-strategy-list,
  .web-search-help__links {
    grid-template-columns: 1fr;
  }

  .web-search-help-button {
    justify-self: start;
  }

  .web-search-toggle-row,
  .web-search-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .web-search-toggle-control {
    justify-items: start;
  }

  .web-search-toggle-warning {
    max-width: none;
    text-align: left;
  }

  .web-search-footer__actions {
    justify-content: flex-start;
  }
}
</style>
