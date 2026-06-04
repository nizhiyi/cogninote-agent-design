export async function pickKnowledgeFolder() {
  try {
    const { invoke } = await import('@tauri-apps/api/core')
    return invoke('pick_knowledge_folder')
  } catch {
    return null
  }
}
