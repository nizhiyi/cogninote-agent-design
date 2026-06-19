<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import cytoscape from 'cytoscape'
import fcose from 'cytoscape-fcose'
import {
  LocateFixed,
  Maximize2,
  Minimize2,
  PanelLeftClose,
  PanelLeftOpen,
  PanelRightClose,
  PanelRightOpen,
  RotateCcw,
  Search,
  SlidersHorizontal,
  ZoomIn,
  ZoomOut
} from 'lucide-vue-next'
import { useThemeStore } from '../stores/theme'
import { formatRelationType, formatScore } from '../utils/formatters'

let isFcoseRegistered = false
const MIN_GRAPH_ZOOM = 0.25
const MAX_GRAPH_ZOOM = 2.5
const ZOOM_STEP = 0.15

function ensureFcoseRegistered() {
  if (isFcoseRegistered) {
    return
  }
  try {
    cytoscape.use(fcose)
  } catch (error) {
    // Vite HMR 可能保留 cytoscape 全局扩展；重复注册失败时继续复用已有扩展。
    const message = error instanceof Error ? error.message : String(error)
    if (!/already|registered|extension/i.test(message)) {
      throw new Error(`fcose 布局注册失败：${message}`)
    }
  }
  isFcoseRegistered = true
}

const props = defineProps({
  payload: {
    type: Object,
    default: null
  },
  mode: {
    type: String,
    required: true
  },
  fullscreen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['open-evidence'])

const themeStore = useThemeStore()
const stageRef = ref(null)
const canvasRef = ref(null)
const searchQuery = ref('')
const selectedNodeTypes = ref([])
const selectedRelationTypes = ref([])
const minWeight = ref(0)
const selectedItem = ref(null)
const renderError = ref('')
const isStageFullscreen = ref(false)
const isFilterPanelCollapsed = ref(false)
const isInspectorPanelCollapsed = ref(false)
const zoomLevel = ref(1)
let cy = null

const isGraphMode = computed(() => props.mode === 'GRAPH')
const fullscreenButtonLabel = computed(() => isStageFullscreen.value ? '退出画布全屏' : '画布全屏')
const zoomPercent = computed(() => `${Math.round(zoomLevel.value * 100)}%`)
const canZoomOut = computed(() => hasCanvasData.value && zoomLevel.value > MIN_GRAPH_ZOOM + 0.01)
const canZoomIn = computed(() => hasCanvasData.value && zoomLevel.value < MAX_GRAPH_ZOOM - 0.01)
const rawElements = computed(() => {
  // Cytoscape 不能直接消费 CSS 变量；主题切换时必须重新生成节点/边颜色。
  void themeStore.effectiveTheme
  return isGraphMode.value ? graphElements() : mindmapElements()
})
const typeOptions = computed(() => countOptions(rawElements.value.nodes.map((node) => node.data.nodeType)))
const relationOptions = computed(() =>
  countOptions(rawElements.value.edges.map((edge) => edge.data.relationType))
    .map((option) => ({ ...option, label: formatRelationType(option.value) }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN') || left.value.localeCompare(right.value))
)
const filteredElements = computed(() => isGraphMode.value ? filteredGraphElements() : filteredMindmapElements())
const hasCanvasData = computed(() => filteredElements.value.nodes.length > 0)
const summaryText = computed(() =>
  `${filteredElements.value.nodes.length} 节点 · ${filteredElements.value.edges.length} 关系`
)

watch(typeOptions, (options) => {
  selectedNodeTypes.value = options.map((option) => option.value)
}, { immediate: true })

watch(relationOptions, (options) => {
  selectedRelationTypes.value = options.map((option) => option.value)
}, { immediate: true })

watch(
  () => [filteredElements.value, props.fullscreen],
  () => {
    renderGraphSafely()
  },
  { deep: true }
)

watch(() => themeStore.effectiveTheme, () => {
  renderGraphSafely()
})

watch(
  () => [isFilterPanelCollapsed.value, isInspectorPanelCollapsed.value],
  () => {
    resizeGraphAfterContainerChange({ fit: false })
  }
)

watch(filteredElements, () => {
  if (!selectedItem.value) {
    return
  }
  const group = selectedItem.value.group === 'edge' ? 'edges' : 'nodes'
  const exists = filteredElements.value[group].some((item) => item.data.id === selectedItem.value.id)
  if (!exists) {
    selectedItem.value = null
  }
})

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  renderGraphSafely()
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (document.fullscreenElement === stageRef.value) {
    void document.exitFullscreen?.().catch(() => {})
  }
  destroyGraph()
})

function graphElements() {
  const rawNodes = props.payload?.nodes || []
  const nodeLabelById = new Map(rawNodes.map((node) => [node.id, node.label || node.id]))
  const nodes = rawNodes.map((node) => ({
    data: {
      id: node.id,
      label: node.label || node.id,
      nodeType: node.type || 'ENTITY',
      kind: 'entity',
      evidenceId: node.id,
      mentionCount: node.mentionCount || 0,
      degree: node.degree || 0,
      confidence: node.confidence || 0,
      size: nodeSize(node),
      color: colorForType(node.type),
      shape: 'ellipse'
    }
  }))
  const edges = (props.payload?.edges || []).map((edge) => {
    const relation = edge.label || 'RELATED'
    // edge.label 是后端内部粗分类，只能用于筛选和配色；画布边文字必须使用中文 displayLabel。
    const displayLabel = edge.displayLabel || '相关'
    return {
      data: {
        id: edge.id,
        source: edge.source,
        target: edge.target,
        label: displayLabel,
        relationType: relation,
        displayLabel,
        relationLabel: displayLabel,
        description: edge.description || '',
        sourceLabel: edge.sourceLabel || nodeLabelById.get(edge.source) || edge.source,
        targetLabel: edge.targetLabel || nodeLabelById.get(edge.target) || edge.target,
        weight: edge.weight || 1,
        confidence: edge.confidence || 0,
        width: Math.min(7, 1.5 + Number(edge.weight || 1)),
        color: edgeColor(relation)
      }
    }
  })
  return { nodes, edges }
}

function mindmapElements() {
  if (Array.isArray(props.payload?.documents)) {
    return structuredMindmapElements()
  }
  return markdownMindmapElements(props.payload?.markdown || '')
}

function structuredMindmapElements() {
  const root = props.payload?.root || { id: 'scope', label: '知识图谱', type: 'SCOPE' }
  const rootId = root.id || 'scope'
  const nodes = [{
    data: {
      id: rootId,
      label: root.label || '知识图谱',
      nodeType: 'SCOPE',
      kind: 'scope',
      mentionCount: 0,
      degree: 0,
      confidence: 0,
      size: 58,
      color: structuralColor('SCOPE'),
      shape: 'round-rectangle'
    }
  }]
  const edges = []
  for (const document of props.payload.documents || []) {
    const documentId = `document:${document.id}`
    nodes.push({
      data: {
        id: documentId,
        label: document.label || document.fileName || '未命名文档',
        nodeType: 'DOCUMENT',
        kind: 'document',
        mentionCount: 0,
        degree: 0,
        confidence: 0,
        size: 44,
        color: structuralColor('DOCUMENT'),
        shape: 'round-rectangle'
      }
    })
    edges.push(mindmapEdge(`edge:${rootId}:${documentId}`, rootId, documentId))
    for (const heading of document.headings || []) {
      const headingId = `heading:${heading.id}`
      nodes.push({
        data: {
          id: headingId,
          label: heading.label || '未命名片段',
          nodeType: 'HEADING',
          kind: 'heading',
          mentionCount: 0,
          degree: 0,
          confidence: 0,
          size: 36,
          color: structuralColor('HEADING'),
          shape: 'round-rectangle'
        }
      })
      edges.push(mindmapEdge(`edge:${documentId}:${headingId}`, documentId, headingId))
      for (const entity of heading.entities || []) {
        const entityId = `entity:${heading.id}:${entity.id}`
        nodes.push({
          data: {
            id: entityId,
            label: entity.label || entity.id,
            nodeType: entity.type || 'ENTITY',
            kind: 'entity',
            evidenceId: entity.id,
            mentionCount: entity.count || 0,
            degree: 0,
            confidence: 0,
            size: Math.min(42, 24 + Number(entity.count || 0) * 3),
            color: colorForType(entity.type),
            shape: 'ellipse'
          }
        })
        edges.push(mindmapEdge(`edge:${headingId}:${entityId}`, headingId, entityId))
      }
    }
  }
  return { nodes, edges }
}

function markdownMindmapElements(markdown) {
  const lines = markdown.split(/\r?\n/)
  const nodes = []
  const edges = []
  const stack = []
  for (let index = 0; index < lines.length; index += 1) {
    const match = /^(#{1,4})\s+(.+)$/.exec(lines[index].trim())
    if (!match) {
      continue
    }
    const level = match[1].length
    const text = match[2].trim()
    const id = `markdown:${index}:${level}`
    const entityMatch = /^(.+?)\s+\[([^\]]+)]\s+x(\d+)$/i.exec(text)
    const nodeType = level === 1 ? 'SCOPE' : level === 2 ? 'DOCUMENT' : level === 3 ? 'HEADING' : entityMatch?.[2] || 'ENTITY'
    nodes.push({
      data: {
        id,
        label: entityMatch?.[1] || text,
        nodeType,
        kind: level === 4 ? 'entity' : nodeType.toLowerCase(),
        mentionCount: Number(entityMatch?.[3] || 0),
        degree: 0,
        confidence: 0,
        size: level === 1 ? 58 : level === 2 ? 44 : level === 3 ? 36 : 28,
        color: level === 4 ? colorForType(nodeType) : structuralColor(nodeType),
        shape: level === 4 ? 'ellipse' : 'round-rectangle'
      }
    })
    const parent = stack[level - 2]
    if (parent) {
      edges.push(mindmapEdge(`edge:${parent}:${id}`, parent, id))
    }
    stack[level - 1] = id
    stack.length = level
  }
  return { nodes, edges }
}

function mindmapEdge(id, source, target) {
  return {
    data: {
      id,
      source,
      target,
      label: '',
      relationType: 'CONTAINS',
      weight: 1,
      width: 1.5,
      color: cssColor('--chart-color-neutral', '#64748b')
    }
  }
}

function filteredGraphElements() {
  const query = searchQuery.value.trim().toLowerCase()
  const activeTypes = new Set(selectedNodeTypes.value)
  const activeRelations = new Set(selectedRelationTypes.value)
  const nodes = rawElements.value.nodes.filter((node) => {
    const matchesType = !activeTypes.size || activeTypes.has(node.data.nodeType)
    const matchesQuery = !query || node.data.label.toLowerCase().includes(query)
    return matchesType && matchesQuery
  })
  const nodeIds = new Set(nodes.map((node) => node.data.id))
  const edges = rawElements.value.edges.filter((edge) => {
    const matchesRelation = !activeRelations.size || activeRelations.has(edge.data.relationType)
    const matchesWeight = Number(edge.data.weight || 0) >= Number(minWeight.value || 0)
    return nodeIds.has(edge.data.source) && nodeIds.has(edge.data.target) && matchesRelation && matchesWeight
  })
  return { nodes, edges }
}

function filteredMindmapElements() {
  const query = searchQuery.value.trim().toLowerCase()
  const activeTypes = new Set(selectedNodeTypes.value)
  const included = new Set()
  const parentByNode = new Map(rawElements.value.edges.map((edge) => [edge.data.target, edge.data.source]))
  const childByNode = new Map()
  for (const edge of rawElements.value.edges) {
    childByNode.set(edge.data.source, [...(childByNode.get(edge.data.source) || []), edge.data.target])
  }

  for (const node of rawElements.value.nodes) {
    const isStructural = node.data.kind !== 'entity'
    const matchesType = isStructural || !activeTypes.size || activeTypes.has(node.data.nodeType)
    const matchesQuery = !query || node.data.label.toLowerCase().includes(query)
    if (!matchesType || !matchesQuery) {
      continue
    }
    includeNodeWithContext(node.data.id, parentByNode, childByNode, included)
  }

  const nodes = rawElements.value.nodes.filter((node) => included.has(node.data.id))
  const edges = rawElements.value.edges.filter((edge) => included.has(edge.data.source) && included.has(edge.data.target))
  return {
    nodes: nodes.length || query ? nodes : rawElements.value.nodes,
    edges: nodes.length || query ? edges : rawElements.value.edges
  }
}

function includeNodeWithContext(nodeId, parentByNode, childByNode, included) {
  let current = nodeId
  while (current && !included.has(current)) {
    included.add(current)
    current = parentByNode.get(current)
  }
  for (const child of childByNode.get(nodeId) || []) {
    included.add(child)
  }
}

async function renderGraph() {
  await nextTick()
  if (!canvasRef.value) {
    return
  }
  destroyGraph()
  renderError.value = ''
  if (!hasCanvasData.value) {
    zoomLevel.value = 1
    return
  }
  cy = cytoscape({
    container: canvasRef.value,
    elements: [...filteredElements.value.nodes, ...filteredElements.value.edges],
    minZoom: MIN_GRAPH_ZOOM,
    maxZoom: MAX_GRAPH_ZOOM,
    userZoomingEnabled: true,
    wheelSensitivity: 0.18,
    style: graphStyle()
  })
  cy.on('tap', 'node, edge', (event) => {
    selectElement(event.target)
  })
  cy.on('tap', (event) => {
    if (event.target === cy) {
      selectedItem.value = null
      clearFocus()
    }
  })
  cy.on('zoom', syncZoomLevel)
  runLayout()
}

function destroyGraph() {
  if (cy) {
    cy.destroy()
    cy = null
  }
}

function runLayout() {
  if (!cy) {
    return
  }
  if (isGraphMode.value) {
    ensureFcoseRegistered()
  }
  const layout = isGraphMode.value
    ? {
        name: 'fcose',
        animate: false,
        fit: true,
        padding: graphPadding(),
        nodeRepulsion: 6500,
        idealEdgeLength: 110,
        quality: 'default'
      }
    : {
        name: 'breadthfirst',
        directed: true,
        animate: false,
        fit: true,
        padding: graphPadding(),
        spacingFactor: 1.35
      }
  const layoutInstance = cy.layout(layout)
  layoutInstance.on('layoutstop', syncZoomLevel)
  layoutInstance.run()
}

function fitGraph() {
  if (!cy) {
    return
  }
  cy.fit(undefined, graphPadding())
  syncZoomLevel()
}

function graphPadding() {
  return props.fullscreen || isStageFullscreen.value ? 72 : 40
}

function zoomGraph(delta) {
  setGraphZoom(zoomLevel.value + delta)
}

function handleZoomSliderInput(event) {
  setGraphZoom(Number(event.target.value))
}

function setGraphZoom(value) {
  if (!cy) {
    return
  }
  const nextZoom = clampZoom(value)
  cy.zoom({
    level: nextZoom,
    renderedPosition: {
      x: cy.width() / 2,
      y: cy.height() / 2
    }
  })
  syncZoomLevel()
}

function syncZoomLevel() {
  if (!cy) {
    return
  }
  zoomLevel.value = clampZoom(cy.zoom())
}

function clampZoom(value) {
  const numericValue = Number(value)
  const safeValue = Number.isFinite(numericValue) ? numericValue : 1
  return Number(Math.min(MAX_GRAPH_ZOOM, Math.max(MIN_GRAPH_ZOOM, safeValue)).toFixed(2))
}

async function toggleStageFullscreen() {
  const stage = stageRef.value
  if (!stage || !document.fullscreenEnabled || !stage.requestFullscreen) {
    renderError.value = '当前浏览器不支持画布全屏。'
    return
  }
  try {
    renderError.value = ''
    if (document.fullscreenElement === stage) {
      await document.exitFullscreen()
      return
    }
    await stage.requestFullscreen()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    renderError.value = `画布全屏失败：${message}`
  }
}

function handleFullscreenChange() {
  isStageFullscreen.value = document.fullscreenElement === stageRef.value
  resizeGraphAfterContainerChange()
}

function resizeGraphAfterContainerChange({ fit = true } = {}) {
  // Fullscreen API 会异步改变容器尺寸，等浏览器完成布局后再同步 Cytoscape 视口。
  nextTick(() => {
    requestAnimationFrame(() => {
      cy?.resize()
      if (fit) {
        fitGraph()
      } else {
        syncZoomLevel()
      }
    })
  })
}

function resetExplorer() {
  searchQuery.value = ''
  selectedNodeTypes.value = typeOptions.value.map((option) => option.value)
  selectedRelationTypes.value = relationOptions.value.map((option) => option.value)
  minWeight.value = 0
  selectedItem.value = null
  renderGraphSafely()
}

function renderGraphSafely() {
  renderGraph().catch((error) => {
    destroyGraph()
    const message = error instanceof Error ? error.message : String(error)
    renderError.value = `图谱渲染失败：${message}`
  })
}

function selectElement(element) {
  selectedItem.value = {
    group: element.isEdge() ? 'edge' : 'node',
    ...element.data()
  }
  focusElement(element)
}

function focusElement(element) {
  if (!cy) {
    return
  }
  clearFocus()
  cy.elements().addClass('is-faded')
  if (element.isNode()) {
    element.closedNeighborhood().removeClass('is-faded').addClass('is-highlighted')
    return
  }
  element.connectedNodes().add(element).removeClass('is-faded').addClass('is-highlighted')
}

function clearFocus() {
  cy?.elements().removeClass('is-faded is-highlighted')
}

function openSelectedEvidence() {
  if (!selectedItem.value) {
    return
  }
  if (selectedItem.value.group === 'edge') {
    emit('open-evidence', {
      type: 'edge',
      id: selectedItem.value.id,
      label: selectedItem.value.displayLabel || selectedItem.value.relationLabel || selectedItem.value.label,
      meta: edgePathText(selectedItem.value),
      description: selectedItem.value.description || ''
    })
    return
  }
  if (selectedItem.value.kind === 'entity' && selectedItem.value.evidenceId) {
    emit('open-evidence', {
      type: 'node',
      id: selectedItem.value.evidenceId,
      label: selectedItem.value.label,
      meta: selectedItem.value.nodeType
    })
  }
}

function edgePathText(edge) {
  const sourceLabel = edge.sourceLabel || edge.source
  const relationLabel = edge.displayLabel || edge.relationLabel || '相关'
  const targetLabel = edge.targetLabel || edge.target
  return `${sourceLabel} -> ${relationLabel} -> ${targetLabel}`
}

function graphStyle() {
  const textColor = cssColor('--color-text-strong', '#0f172a')
  const labelColor = cssColor('--color-text', '#475569')
  const surfaceColor = cssColor('--color-surface', '#ffffff')
  const borderColor = cssColor('--color-bg-elevated', '#ffffff')
  const highlightColor = cssColor('--color-action-strong', '#1d4ed8')
  return [
    {
      selector: 'node',
      style: {
        label: 'data(label)',
        width: 'data(size)',
        height: 'data(size)',
        shape: 'data(shape)',
        'background-color': 'data(color)',
        'border-color': borderColor,
        'border-width': 2,
        color: textColor,
        'font-size': props.fullscreen ? 12 : 11,
        'font-weight': 700,
        'text-wrap': 'wrap',
        'text-max-width': props.fullscreen ? 120 : 92,
        'text-valign': 'bottom',
        'text-halign': 'center',
        'text-margin-y': 8,
        'overlay-padding': 8,
        'transition-property': 'opacity, border-width',
        'transition-duration': 120
      }
    },
    {
      selector: 'edge',
      style: {
        label: isGraphMode.value ? 'data(label)' : '',
        width: 'data(width)',
        'line-color': 'data(color)',
        'target-arrow-color': 'data(color)',
        'target-arrow-shape': isGraphMode.value ? 'triangle' : 'none',
        'curve-style': 'bezier',
        color: labelColor,
        'font-size': 10,
        'font-weight': 700,
        'text-background-color': surfaceColor,
        'text-background-opacity': 0.88,
        'text-background-padding': 3,
        'text-rotation': 'autorotate',
        opacity: 0.72
      }
    },
    {
      selector: '.is-faded',
      style: {
        opacity: 0.18
      }
    },
    {
      selector: '.is-highlighted',
      style: {
        opacity: 1,
        'border-color': highlightColor,
        'border-width': 4
      }
    },
    {
      selector: 'edge.is-highlighted',
      style: {
        'line-color': highlightColor,
        'target-arrow-color': highlightColor,
        width: 4
      }
    }
  ]
}

function nodeSize(node) {
  const degree = Number(node.degree || 0)
  const mentions = Number(node.mentionCount || 0)
  return Math.min(58, 28 + degree * 3 + mentions)
}

function colorForType(type) {
  const palette = [
    cssColor('--chart-color-1', '#2563eb'),
    cssColor('--chart-color-2', '#7c3aed'),
    cssColor('--chart-color-3', '#d97706'),
    cssColor('--chart-color-4', '#db2777'),
    cssColor('--chart-color-5', '#0891b2'),
    cssColor('--chart-color-6', '#16a34a'),
    cssColor('--chart-color-neutral', '#64748b')
  ]
  const text = String(type || 'ENTITY')
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash + text.charCodeAt(index) * (index + 1)) % palette.length
  }
  return palette[hash]
}

function structuralColor(type) {
  if (type === 'SCOPE') {
    return cssColor('--color-action-strong', '#1d4ed8')
  }
  if (type === 'DOCUMENT') {
    return cssColor('--color-action', '#2563eb')
  }
  return cssColor('--color-border', '#e2e8f0')
}

function edgeColor(type) {
  const palette = [
    cssColor('--chart-color-neutral', '#64748b'),
    cssColor('--chart-color-1', '#2563eb'),
    cssColor('--chart-color-2', '#7c3aed'),
    cssColor('--chart-color-3', '#d97706'),
    cssColor('--chart-color-5', '#0891b2')
  ]
  const text = String(type || 'RELATED')
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash + text.charCodeAt(index)) % palette.length
  }
  return palette[hash]
}

function cssColor(token, fallback) {
  if (typeof window === 'undefined') {
    return fallback
  }
  const value = window.getComputedStyle(document.documentElement).getPropertyValue(token).trim()
  return value || fallback
}

function countOptions(values) {
  const counts = new Map()
  for (const value of values) {
    const key = value || 'UNKNOWN'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return [...counts.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([value, count]) => ({ value, count }))
}
</script>

<template>
  <section
    class="graph-explorer"
    :class="{
      'graph-explorer--fullscreen': fullscreen,
      'graph-explorer--filters-collapsed': isFilterPanelCollapsed,
      'graph-explorer--inspector-collapsed': isInspectorPanelCollapsed
    }"
    aria-label="图谱探索器"
  >
    <aside
      class="graph-explorer__filters graph-explorer__side-panel"
      :class="{ 'graph-explorer__side-panel--collapsed': isFilterPanelCollapsed }"
      :aria-label="isFilterPanelCollapsed ? '图谱筛选已隐藏' : '图谱筛选'"
    >
      <button
        v-if="isFilterPanelCollapsed"
        type="button"
        class="icon-button graph-explorer__collapsed-toggle"
        aria-label="展开筛选侧栏"
        title="展开筛选侧栏"
        :aria-expanded="false"
        @click="isFilterPanelCollapsed = false"
      >
        <PanelLeftOpen aria-hidden="true" />
      </button>

      <template v-else>
        <div class="graph-explorer__filter-title">
          <span>
            <SlidersHorizontal aria-hidden="true" />
            <strong>筛选</strong>
          </span>
          <button
            type="button"
            class="icon-button graph-explorer__panel-toggle"
            aria-label="收起筛选侧栏"
            title="收起筛选侧栏"
            :aria-expanded="true"
            @click="isFilterPanelCollapsed = true"
          >
            <PanelLeftClose aria-hidden="true" />
          </button>
        </div>

        <label class="graph-explorer__search">
          <span>搜索实体</span>
          <span>
            <Search aria-hidden="true" />
            <input v-model="searchQuery" type="search" placeholder="输入节点名称" />
          </span>
        </label>

        <section v-if="typeOptions.length" class="graph-explorer__filter-group">
          <strong>节点类型</strong>
          <label v-for="option in typeOptions" :key="option.value">
            <input v-model="selectedNodeTypes" type="checkbox" :value="option.value" />
            <span>{{ option.value }}</span>
            <em>{{ option.count }}</em>
          </label>
        </section>

        <section v-if="isGraphMode && relationOptions.length" class="graph-explorer__filter-group">
          <strong>关系类型</strong>
          <label v-for="option in relationOptions" :key="option.value">
            <input v-model="selectedRelationTypes" type="checkbox" :value="option.value" />
            <span>{{ option.label }}</span>
            <em>{{ option.count }}</em>
          </label>
        </section>

        <label v-if="isGraphMode" class="graph-explorer__number-filter">
          <span>最小证据数</span>
          <input v-model.number="minWeight" type="number" min="0" />
        </label>

        <button type="button" class="secondary-button graph-explorer__reset" @click="resetExplorer">
          <RotateCcw aria-hidden="true" />
          <span>重置</span>
        </button>
      </template>
    </aside>

    <div ref="stageRef" class="graph-explorer__stage">
      <div class="graph-explorer__stage-toolbar">
        <div>
          <strong>{{ isGraphMode ? '关系图' : '思维导图' }}</strong>
          <span>{{ summaryText }}</span>
          <span v-if="isGraphMode && payload?.hiddenNodeCount">隐藏 {{ payload.hiddenNodeCount }} 节点</span>
        </div>
        <div class="graph-explorer__stage-actions">
          <div class="graph-explorer__zoom-controls" aria-label="图谱缩放">
            <button
              type="button"
              class="icon-button"
              :disabled="!canZoomOut"
              aria-label="缩小图谱"
              title="缩小图谱"
              @click="zoomGraph(-ZOOM_STEP)"
            >
              <ZoomOut aria-hidden="true" />
            </button>
            <input
              type="range"
              :min="MIN_GRAPH_ZOOM"
              :max="MAX_GRAPH_ZOOM"
              step="0.05"
              :value="zoomLevel"
              :disabled="!hasCanvasData"
              aria-label="缩放比例"
              @input="handleZoomSliderInput"
            />
            <button
              type="button"
              class="icon-button"
              :disabled="!canZoomIn"
              aria-label="放大图谱"
              title="放大图谱"
              @click="zoomGraph(ZOOM_STEP)"
            >
              <ZoomIn aria-hidden="true" />
            </button>
            <span class="graph-explorer__zoom-value">{{ zoomPercent }}</span>
          </div>
          <button type="button" class="icon-button" :aria-label="fullscreenButtonLabel" :title="fullscreenButtonLabel" @click="toggleStageFullscreen">
            <Minimize2 v-if="isStageFullscreen" aria-hidden="true" />
            <Maximize2 v-else aria-hidden="true" />
          </button>
          <button type="button" class="icon-button" aria-label="适配图谱视图" title="适配图谱视图" @click="fitGraph">
            <LocateFixed aria-hidden="true" />
          </button>
          <button type="button" class="icon-button" aria-label="重新布局图谱" title="重新布局图谱" @click="runLayout">
            <RotateCcw aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="graph-explorer__legend" aria-label="图谱图例">
        <span v-for="option in typeOptions.slice(0, 6)" :key="option.value">
          <i :style="{ background: colorForType(option.value) }"></i>
          {{ option.value }}
        </span>
        <span v-if="isGraphMode">
          <i class="graph-explorer__legend-line"></i>
          边越粗证据越多，箭头表示方向
        </span>
      </div>

      <div class="graph-explorer__canvas-wrap">
        <p v-if="renderError" class="panel-message graph-explorer__error">{{ renderError }}</p>
        <p v-else-if="!hasCanvasData" class="panel-message">当前筛选下没有可展示的图谱节点。</p>
        <div ref="canvasRef" class="graph-explorer__canvas" role="img" :aria-label="isGraphMode ? '知识图谱关系图' : '知识图谱思维导图'"></div>
      </div>
    </div>

    <aside
      class="graph-explorer__inspector graph-explorer__side-panel"
      :class="{ 'graph-explorer__side-panel--collapsed': isInspectorPanelCollapsed }"
      :aria-label="isInspectorPanelCollapsed ? '图谱详情已隐藏' : '图谱详情'"
    >
      <button
        v-if="isInspectorPanelCollapsed"
        type="button"
        class="icon-button graph-explorer__collapsed-toggle"
        aria-label="展开详情侧栏"
        title="展开详情侧栏"
        :aria-expanded="false"
        @click="isInspectorPanelCollapsed = false"
      >
        <PanelRightOpen aria-hidden="true" />
      </button>

      <template v-else>
        <div class="graph-explorer__inspector-title">
          <p class="eyebrow">Inspector</p>
          <button
            type="button"
            class="icon-button graph-explorer__panel-toggle"
            aria-label="收起详情侧栏"
            title="收起详情侧栏"
            :aria-expanded="true"
            @click="isInspectorPanelCollapsed = true"
          >
            <PanelRightClose aria-hidden="true" />
          </button>
        </div>
        <template v-if="selectedItem">
          <h4>{{ selectedItem.label }}</h4>
          <div class="graph-inspector__meta">
            <span v-if="selectedItem.group === 'node'">{{ selectedItem.nodeType }}</span>
            <span v-else>{{ selectedItem.relationLabel || formatRelationType(selectedItem.relationType) }}</span>
            <span v-if="selectedItem.mentionCount">提及 {{ selectedItem.mentionCount }}</span>
            <span v-if="selectedItem.degree">连接 {{ selectedItem.degree }}</span>
            <span v-if="selectedItem.weight">证据 {{ selectedItem.weight }}</span>
            <span v-if="selectedItem.confidence">score {{ formatScore(selectedItem.confidence) }}</span>
          </div>
          <p
            v-if="selectedItem.group === 'edge' && selectedItem.description"
            class="graph-inspector__description"
          >
            {{ selectedItem.description }}
          </p>
          <p v-if="selectedItem.group === 'edge'" class="graph-inspector__path">
            {{ edgePathText(selectedItem) }}
          </p>
          <button
            v-if="selectedItem.group === 'edge' || (selectedItem.kind === 'entity' && selectedItem.evidenceId)"
            type="button"
            class="primary-button graph-inspector__evidence"
            @click="openSelectedEvidence"
          >
            查看证据
          </button>
        </template>
        <template v-else>
          <h4>选择节点或关系</h4>
          <p class="muted-text">点击画布中的实体、标题或关系，查看类型、证据数和回链入口。</p>
        </template>
      </template>
    </aside>
  </section>
</template>
