import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  DESKTOP_UPDATE_UNAVAILABLE_MESSAGE,
  UPDATE_CHANNELS,
  checkDesktopUpdate,
  installDesktopUpdate,
  listenDesktopUpdateProgress
} from '../api/desktop-update-api'
import { isTauriRuntime } from '../api/desktop-api'

const UPDATE_CHANNEL_STORAGE_KEY = 'cogninote-update-channel'
const DISMISSED_STABLE_UPDATE_STORAGE_KEY = 'cogninote-dismissed-stable-update-version'
const DEFAULT_UPDATE_CHANNEL = 'stable'

/**
 * 管理桌面自动更新状态。
 *
 * <p>更新检查只在 Tauri 桌面环境可用；浏览器开发模式会降级为未配置状态，不影响普通前端调试。</p>
 */
export const useDesktopUpdateStore = defineStore('desktop-update', () => {
  const channel = ref(readInitialChannel())
  const currentVersion = ref('-')
  const updateInfo = ref(null)
  const isChecking = ref(false)
  const isInstalling = ref(false)
  const error = ref('')
  const message = ref('')
  const progress = ref(null)
  const listenerReady = ref(false)
  let unlistenProgress = null

  const isDesktopRuntime = computed(() => isTauriRuntime())
  const channelLabel = computed(() =>
    UPDATE_CHANNELS.find((item) => item.value === channel.value)?.label || '正式版'
  )
  const availableVersionPublishedAt = computed(() => formatUpdateDate(updateInfo.value?.date))
  const progressPercent = computed(() => {
    const downloaded = progress.value?.downloaded
    const contentLength = progress.value?.contentLength
    if (!downloaded || !contentLength) {
      return 0
    }
    return Math.min(100, Math.round((downloaded / contentLength) * 100))
  })

  function setChannel(nextChannel) {
    channel.value = normalizeChannel(nextChannel)
    updateInfo.value = null
    message.value = ''
    error.value = ''
    window.localStorage.setItem(UPDATE_CHANNEL_STORAGE_KEY, channel.value)
  }

  async function initializeUpdateListener() {
    if (listenerReady.value) {
      return
    }
    if (!isDesktopRuntime.value) {
      listenerReady.value = true
      return
    }
    try {
      unlistenProgress = await listenDesktopUpdateProgress((payload) => {
        progress.value = payload
        if (payload?.event === 'Error') {
          error.value = payload.message || '更新失败'
          isInstalling.value = false
        }
      })
      listenerReady.value = true
    } catch (err) {
      if (isTauriRuntime()) {
        console.warn('[DesktopUpdate] 进度监听注册失败:', err)
      }
      listenerReady.value = false
    }
  }

  async function loadCurrentVersion() {
    if (!isDesktopRuntime.value) {
      currentVersion.value = '-'
      return currentVersion.value
    }
    try {
      const { getVersion } = await import('@tauri-apps/api/app')
      currentVersion.value = await getVersion()
    } catch (err) {
      console.warn('[DesktopUpdate] 获取当前版本失败:', err)
      currentVersion.value = '-'
    }
    return currentVersion.value
  }

  async function checkForUpdates(options = {}) {
    if (!isDesktopRuntime.value) {
      updateInfo.value = null
      if (!options.silent) {
        message.value = ''
        error.value = DESKTOP_UPDATE_UNAVAILABLE_MESSAGE
      }
      return null
    }
    const requestedChannel = normalizeChannel(options.channel || channel.value)
    const shouldUpdateState = options.updateState !== false
    isChecking.value = true
    error.value = ''
    if (!options.silent) {
      message.value = ''
    }
    try {
      const update = await checkDesktopUpdate(requestedChannel)
      if (shouldUpdateState) {
        updateInfo.value = update
      }
      if (update?.currentVersion) {
        currentVersion.value = update.currentVersion
      }
      if (!options.silent) {
        message.value = update ? `发现新版本 ${update.version}` : '当前已是最新版本'
      }
      return update
    } catch (err) {
      if (shouldUpdateState) {
        updateInfo.value = null
      }
      if (!options.silent) {
        error.value = normalizeUpdateError(err)
      }
      return null
    } finally {
      isChecking.value = false
    }
  }

  async function checkStartupStableUpdate() {
    const update = await checkForUpdates({
      channel: DEFAULT_UPDATE_CHANNEL,
      silent: true,
      updateState: false
    })
    if (!update || isStableUpdateDismissed(update.version)) {
      return null
    }
    return update
  }

  function dismissStableUpdate(version) {
    const normalizedVersion = String(version || '').trim()
    if (!normalizedVersion) {
      return
    }
    window.localStorage.setItem(DISMISSED_STABLE_UPDATE_STORAGE_KEY, normalizedVersion)
  }

  function isStableUpdateDismissed(version) {
    return window.localStorage.getItem(DISMISSED_STABLE_UPDATE_STORAGE_KEY) === String(version || '').trim()
  }

  async function installUpdate(options = {}) {
    if (!isDesktopRuntime.value) {
      error.value = DESKTOP_UPDATE_UNAVAILABLE_MESSAGE
      return null
    }
    const installChannel = normalizeChannel(options.channel || updateInfo.value?.channel || channel.value)
    isInstalling.value = true
    error.value = ''
    message.value = ''
    progress.value = null
    await initializeUpdateListener()
    try {
      const result = await installDesktopUpdate(installChannel)
      if (!result?.installed) {
        isInstalling.value = false
        message.value = '当前已是最新版本'
      }
      return result
    } catch (err) {
      isInstalling.value = false
      error.value = normalizeUpdateError(err)
      throw err
    }
  }

  function cleanupUpdateListener() {
    if (unlistenProgress) {
      unlistenProgress()
      unlistenProgress = null
    }
    listenerReady.value = false
  }

  return {
    channels: UPDATE_CHANNELS,
    channel,
    channelLabel,
    currentVersion,
    availableVersionPublishedAt,
    isDesktopRuntime,
    updateInfo,
    isChecking,
    isInstalling,
    error,
    message,
    progress,
    progressPercent,
    setChannel,
    initializeUpdateListener,
    loadCurrentVersion,
    checkForUpdates,
    checkStartupStableUpdate,
    dismissStableUpdate,
    installUpdate,
    cleanupUpdateListener
  }
})

function normalizeChannel(value) {
  return UPDATE_CHANNELS.some((item) => item.value === value) ? value : DEFAULT_UPDATE_CHANNEL
}

function readInitialChannel() {
  if (typeof window === 'undefined') {
    return DEFAULT_UPDATE_CHANNEL
  }
  return normalizeChannel(window.localStorage.getItem(UPDATE_CHANNEL_STORAGE_KEY))
}

function normalizeUpdateError(error) {
  const message = error?.message || String(error || '自动更新不可用')
  return message.includes("reading 'invoke'") ? DESKTOP_UPDATE_UNAVAILABLE_MESSAGE : message
}

function formatUpdateDate(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}
