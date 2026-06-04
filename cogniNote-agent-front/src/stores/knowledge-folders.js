import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { pickKnowledgeFolder } from '../api/desktop-api'
import {
  deleteKnowledgeFolder,
  importKnowledgeFolder,
  listKnowledgeFolders,
  rebuildKnowledgeFolder,
  setKnowledgeFolderEnabled
} from '../api/knowledge-folders-api'
import { useSearchStore } from './search'

export const useKnowledgeFoldersStore = defineStore('knowledgeFolders', () => {
  const folders = ref([])
  const unassignedDocuments = ref([])
  const folderPath = ref('')
  const recursive = ref(true)
  const ingestResult = ref(null)
  const rebuildResult = ref(null)
  const error = ref('')
  const isLoading = ref(false)
  const isImporting = ref(false)
  const busyFolderIds = ref(new Set())
  const expandedFolderIds = ref(new Set())

  const allDocuments = computed(() => [
    ...folders.value.flatMap((folder) => folder.documents || []),
    ...unassignedDocuments.value
  ])

  const stats = computed(() => {
    const parsed = allDocuments.value.filter((document) => document.status === 'PARSED').length
    const failed = allDocuments.value.filter((document) => document.status === 'FAILED').length
    const chunks = allDocuments.value.reduce((total, document) => total + document.chunkCount, 0)
    return {
      folderCount: folders.value.length,
      documentCount: allDocuments.value.length,
      parsed,
      failed,
      chunks
    }
  })

  async function fetchFolders() {
    isLoading.value = true
    error.value = ''

    try {
      const response = await listKnowledgeFolders()
      folders.value = response?.folders || []
      unassignedDocuments.value = response?.unassignedDocuments || []
    } catch (err) {
      folders.value = []
      unassignedDocuments.value = []
      error.value = `知识库目录读取失败：${err.message}`
    } finally {
      isLoading.value = false
    }
  }

  function ensureFoldersLoaded() {
    if (folders.value.length || unassignedDocuments.value.length || isLoading.value) {
      return Promise.resolve()
    }
    return fetchFolders()
  }

  async function chooseFolder() {
    error.value = ''
    try {
      const selected = await pickKnowledgeFolder()
      if (selected) {
        folderPath.value = selected
      }
    } catch (err) {
      error.value = `打开系统文件夹选择器失败，请手动输入路径：${err.message}`
    }
  }

  async function importFolder() {
    const trimmedFolderPath = folderPath.value.trim()
    if (!trimmedFolderPath) {
      error.value = '请选择或输入要导入的本地目录路径'
      return
    }

    const searchStore = useSearchStore()
    isImporting.value = true
    ingestResult.value = null
    rebuildResult.value = null
    error.value = ''

    try {
      ingestResult.value = await importKnowledgeFolder({
        folderPath: trimmedFolderPath,
        recursive: recursive.value
      })
      await refreshKnowledgeState(searchStore)
    } catch (err) {
      error.value = `导入知识库目录失败：${err.message}`
    } finally {
      isImporting.value = false
    }
  }

  async function rebuildFolder(id) {
    const searchStore = useSearchStore()
    setFolderBusy(id, true)
    rebuildResult.value = null
    error.value = ''

    try {
      rebuildResult.value = await rebuildKnowledgeFolder(id)
      await refreshKnowledgeState(searchStore)
    } catch (err) {
      error.value = `重建目录索引失败：${err.message}`
    } finally {
      setFolderBusy(id, false)
    }
  }

  async function toggleFolderEnabled(folder) {
    const searchStore = useSearchStore()
    setFolderBusy(folder.id, true)
    error.value = ''

    try {
      await setKnowledgeFolderEnabled(folder.id, !folder.enabled)
      await refreshKnowledgeState(searchStore)
      if (searchStore.searchResult?.hits?.length) {
        await searchStore.searchKnowledge()
      }
    } catch (err) {
      error.value = `${folder.enabled ? '停用' : '启用'}知识库目录失败：${err.message}`
    } finally {
      setFolderBusy(folder.id, false)
    }
  }

  async function deleteFolder(id) {
    const searchStore = useSearchStore()
    setFolderBusy(id, true)
    error.value = ''

    try {
      await deleteKnowledgeFolder(id)
      expandedFolderIds.value.delete(id)
      await refreshKnowledgeState(searchStore)
      if (searchStore.searchResult?.hits?.length) {
        await searchStore.searchKnowledge()
      }
    } catch (err) {
      error.value = `删除知识库目录失败：${err.message}`
    } finally {
      setFolderBusy(id, false)
    }
  }

  function toggleExpanded(id) {
    if (expandedFolderIds.value.has(id)) {
      expandedFolderIds.value.delete(id)
    } else {
      expandedFolderIds.value.add(id)
    }
  }

  function isExpanded(id) {
    return expandedFolderIds.value.has(id)
  }

  function isFolderBusy(id) {
    return busyFolderIds.value.has(id)
  }

  function setFolderBusy(id, busy) {
    const next = new Set(busyFolderIds.value)
    if (busy) {
      next.add(id)
    } else {
      next.delete(id)
    }
    busyFolderIds.value = next
  }

  async function refreshKnowledgeState(searchStore) {
    await fetchFolders()
    await searchStore.fetchIndexStatus()
  }

  return {
    folders,
    unassignedDocuments,
    folderPath,
    recursive,
    ingestResult,
    rebuildResult,
    error,
    isLoading,
    isImporting,
    stats,
    fetchFolders,
    ensureFoldersLoaded,
    chooseFolder,
    importFolder,
    rebuildFolder,
    toggleFolderEnabled,
    deleteFolder,
    toggleExpanded,
    isExpanded,
    isFolderBusy
  }
})
