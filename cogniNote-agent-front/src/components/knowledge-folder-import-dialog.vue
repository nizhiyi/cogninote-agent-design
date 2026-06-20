<script setup>
import { computed } from 'vue'
import { FolderInput } from 'lucide-vue-next'
import { isTauriRuntime } from '../api/desktop-api'
import { useKnowledgeFoldersStore } from '../stores/knowledge-folders'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const knowledgeStore = useKnowledgeFoldersStore()

const isOpen = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const canPickKnowledgeFolder = computed(() => isTauriRuntime())

async function submitImportFolder() {
  await knowledgeStore.importFolder()
  if (!knowledgeStore.error) {
    isOpen.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="isOpen"
    class="knowledge-import-dialog"
    title="导入本地目录"
    width="min(560px, calc(100vw - 32px))"
    align-center
  >
    <form class="knowledge-folder-import" @submit.prevent="submitImportFolder">
      <label class="field field--full">
        <span>本地目录</span>
        <el-input
          v-model="knowledgeStore.folderPath"
          placeholder="点击选择文件夹，或手动输入 D:/notes"
          autocomplete="off"
        />
      </label>

      <div class="knowledge-folder-import__controls">
        <el-button
          :disabled="!canPickKnowledgeFolder"
          title="系统文件夹选择器仅在桌面版可用，浏览器开发模式请手动输入路径"
          @click="knowledgeStore.chooseFolder"
        >
          <FolderInput aria-hidden="true" />
          <span>选择文件夹</span>
        </el-button>

        <el-checkbox v-model="knowledgeStore.recursive">递归扫描</el-checkbox>
      </div>

      <el-alert
        v-if="knowledgeStore.error"
        class="settings-inline-alert"
        type="error"
        :title="knowledgeStore.error"
        :closable="false"
        show-icon
      />
    </form>

    <template #footer>
      <div class="knowledge-import-dialog__footer">
        <el-button @click="isOpen = false">取消</el-button>
        <el-button type="primary" :loading="knowledgeStore.isImporting" @click="submitImportFolder">
          导入目录
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
