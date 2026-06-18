import { ElNotification } from 'element-plus'
import { isTauriRuntime } from '../api/desktop-api'
import { APP_DISPLAY_NAME } from '../config/brand'

/**
 * 发送长任务完成提醒。
 *
 * <p>桌面端优先使用系统通知；浏览器调试、权限拒绝或系统通知不可用时，退回到站内通知，
 * 避免提醒失败影响原本已经完成的业务流程。</p>
 */
export async function notifyTaskCompleted({ title, body }) {
  const sentSystemNotification = await trySendSystemNotification({ title, body })
  if (sentSystemNotification) {
    return
  }

  ElNotification({
    title: title || APP_DISPLAY_NAME,
    message: body || '任务已完成。',
    type: 'success',
    duration: 5000
  })
}

export async function ensureSystemNotificationPermission() {
  return await requestSystemNotificationPermission()
}

async function trySendSystemNotification({ title, body }) {
  if (!isTauriRuntime()) {
    return false
  }

  try {
    const {
      sendNotification
    } = await import('@tauri-apps/plugin-notification')
    const permissionGranted = await requestSystemNotificationPermission()
    if (!permissionGranted) {
      return false
    }
    sendNotification({
      title: title || APP_DISPLAY_NAME,
      body
    })
    return true
  } catch {
    return false
  }
}

async function requestSystemNotificationPermission() {
  if (!isTauriRuntime()) {
    return false
  }

  try {
    const {
      isPermissionGranted,
      requestPermission
    } = await import('@tauri-apps/plugin-notification')
    if (await isPermissionGranted()) {
      return true
    }
    return await requestPermission() === 'granted'
  } catch {
    return false
  }
}
