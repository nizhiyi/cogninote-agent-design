<script setup>
import { computed, onMounted } from 'vue'
import { Download, RefreshCw } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownRenderer from './markdown-renderer.vue'
import { useDesktopUpdateStore } from '../stores/desktop-update'
import { APP_DISPLAY_NAME } from '../config/brand'

const desktopUpdateStore = useDesktopUpdateStore()

const updateStatus = computed(() => {
  if (!desktopUpdateStore.isDesktopRuntime) {
    return { type: 'info', text: '仅桌面版可用' }
  }
  if (desktopUpdateStore.error) {
    return { type: 'error', text: desktopUpdateStore.error }
  }
  if (desktopUpdateStore.updateInfo) {
    return { type: 'success', text: `可安装 ${desktopUpdateStore.updateInfo.version}` }
  }
  return { type: 'info', text: desktopUpdateStore.message || '未检查' }
})

onMounted(() => {
  desktopUpdateStore.loadCurrentVersion()
  desktopUpdateStore.initializeUpdateListener()
})

function setChannel(channel) {
  desktopUpdateStore.setChannel(channel)
}

async function checkForUpdates() {
  await desktopUpdateStore.checkForUpdates()
}

async function installUpdate() {
  if (!desktopUpdateStore.updateInfo) {
    await desktopUpdateStore.checkForUpdates()
  }
  if (!desktopUpdateStore.updateInfo) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `安装 ${desktopUpdateStore.updateInfo.version} 后会重启${APP_DISPLAY_NAME}。`,
      '安装更新',
      {
        confirmButtonText: '安装并重启',
        cancelButtonText: '稍后',
        type: 'warning'
      }
    )
    await desktopUpdateStore.installUpdate({ channel: desktopUpdateStore.updateInfo.channel })
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(desktopUpdateStore.error || err?.message || '安装更新失败')
    }
  }
}
</script>

<template>
  <section class="settings-panel desktop-update-panel">
    <header class="settings-panel__header">
      <p class="eyebrow">系统</p>
      <h3>应用更新</h3>
    </header>

    <div class="settings-card desktop-update-card">
      <div class="desktop-update-card__main">
        <div class="desktop-update-row">
          <span>更新通道</span>
          <el-radio-group :model-value="desktopUpdateStore.channel" @change="setChannel">
            <el-radio-button
              v-for="item in desktopUpdateStore.channels"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <el-descriptions class="settings-descriptions" :column="2" border>
          <el-descriptions-item label="当前通道">{{ desktopUpdateStore.channelLabel }}</el-descriptions-item>
          <el-descriptions-item label="检查状态">
            <el-tag :type="updateStatus.type">{{ updateStatus.text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前版本">
            {{ desktopUpdateStore.currentVersion }}
          </el-descriptions-item>
          <el-descriptions-item label="可用版本">
            {{ desktopUpdateStore.updateInfo?.version || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="可用版本发布时间">
            {{ desktopUpdateStore.availableVersionPublishedAt }}
          </el-descriptions-item>
          <el-descriptions-item label="下载进度">
            <span v-if="desktopUpdateStore.isInstalling">{{ desktopUpdateStore.progress?.message || '下载中' }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-progress
          v-if="desktopUpdateStore.isInstalling"
          :percentage="desktopUpdateStore.progressPercent"
          :indeterminate="desktopUpdateStore.progressPercent === 0"
        />

        <section v-if="desktopUpdateStore.updateInfo?.body" class="desktop-update-notes-block">
          <h4>更新说明</h4>
          <MarkdownRenderer
            class="desktop-update-notes"
            :content="desktopUpdateStore.updateInfo.body"
            empty-text="暂无更新说明。"
          />
        </section>
      </div>

      <div class="button-row">
        <el-button
          :disabled="!desktopUpdateStore.isDesktopRuntime"
          :loading="desktopUpdateStore.isChecking"
          @click="checkForUpdates"
        >
          <RefreshCw aria-hidden="true" />
          检查更新
        </el-button>
        <el-button
          type="primary"
          :disabled="!desktopUpdateStore.isDesktopRuntime || !desktopUpdateStore.updateInfo || desktopUpdateStore.isInstalling"
          :loading="desktopUpdateStore.isInstalling"
          @click="installUpdate"
        >
          <Download aria-hidden="true" />
          安装并重启
        </el-button>
      </div>

      <div class="desktop-update-help">
        <strong>更新检测说明</strong>
        <p>启动时只自动提醒正式版更新；测试版通道仅在本页手动检测，用于提前验证预发行包。</p>
      </div>
    </div>
  </section>
</template>
