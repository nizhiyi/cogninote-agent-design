/**
 * 执行 业务 中的 pick Knowledge Folder 步骤。
 * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
 */
export async function pickKnowledgeFolder() {
  try {
    const { invoke } = await import('@tauri-apps/api/core')
    return invoke('pick_knowledge_folder')
  } catch {
    return null
  }
}
