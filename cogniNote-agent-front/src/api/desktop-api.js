/**
 * 调用 Tauri 桌面端文件夹选择器。
 *
 * <p>Web 开发模式没有 @tauri-apps/api，返回 null 让调用方回退到手动输入路径。</p>
 */
export async function pickKnowledgeFolder() {
  try {
    const { invoke } = await import('@tauri-apps/api/core')
    return invoke('pick_knowledge_folder')
  } catch {
    return null
  }
}
