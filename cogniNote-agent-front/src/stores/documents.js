import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deleteDocumentRecord, ingestDocuments as requestIngest, listDocuments } from '../api/documents-api'
import { useSearchStore } from './search'

/**
 * 定义 文档管理 的 Pinia Store。
 * <p>集中维护响应式状态、派生值和异步动作，组件只消费 Store 暴露的接口。</p>
 */
export const useDocumentsStore = defineStore('documents', () => {
  const documents = ref([])
  const ingestResult = ref(null)
  const isLoadingDocuments = ref(false)
  const isIngesting = ref(false)
  const documentError = ref('')
  const folderPath = ref('')
  const recursive = ref(true)

  const stats = computed(() => {
    const parsed = documents.value.filter((document) => document.status === 'PARSED').length
    const failed = documents.value.filter((document) => document.status === 'FAILED').length
    const chunks = documents.value.reduce((total, document) => total + document.chunkCount, 0)
    return { parsed, failed, chunks }
  })

  /**
   * 加载 fetch Documents 对应的数据。
   * <p>接口结果会被转换为页面或 Store 可直接消费的结构。</p>
   */
  async function fetchDocuments() {
    isLoadingDocuments.value = true
    documentError.value = ''

    try {
      documents.value = await listDocuments()
    } catch (err) {
      documents.value = []
      documentError.value = `文档列表读取失败：${err.message}`
    } finally {
      isLoadingDocuments.value = false
    }
  }

  /**
   * 执行 文档管理 中的 ensure Documents Loaded 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  function ensureDocumentsLoaded() {
    if (documents.value.length || isLoadingDocuments.value) {
      return Promise.resolve()
    }
    return fetchDocuments()
  }

  /**
   * 执行 文档管理 中的 ingest Documents 步骤。
   * <p>该函数是当前组件或模块中的一个明确维护边界。</p>
   */
  async function ingestDocuments() {
    const trimmedFolderPath = folderPath.value.trim()
    if (!trimmedFolderPath) {
      documentError.value = '请输入要导入的本地目录路径'
      return
    }

    const searchStore = useSearchStore()
    isIngesting.value = true
    ingestResult.value = null
    documentError.value = ''

    try {
      ingestResult.value = await requestIngest({
        folderPath: trimmedFolderPath,
        recursive: recursive.value
      })
      await fetchDocuments()
      await searchStore.fetchIndexStatus()
    } catch (err) {
      documentError.value = `导入失败：${err.message}`
    } finally {
      isIngesting.value = false
    }
  }

  /**
   * 删除或清理 delete Document 对应的数据。
   * <p>清理时同步处理本地缓存，避免界面保留过期状态。</p>
   */
  async function deleteDocument(id) {
    const searchStore = useSearchStore()
    documentError.value = ''

    try {
      await deleteDocumentRecord(id)
      await fetchDocuments()
      await searchStore.fetchIndexStatus()
      if (searchStore.searchResult?.hits?.length) {
        await searchStore.searchKnowledge()
      }
    } catch (err) {
      documentError.value = `删除索引记录失败：${err.message}`
    }
  }

  return {
    documents,
    ingestResult,
    isLoadingDocuments,
    isIngesting,
    documentError,
    folderPath,
    recursive,
    stats,
    fetchDocuments,
    ensureDocumentsLoaded,
    ingestDocuments,
    deleteDocument
  }
})
