<script setup>
import { computed } from 'vue'

/**
 * 轻量 SVG 关系图视图。
 *
 * <p>后端 payload 只提供拓扑和权重，前端用确定性环形布局生成坐标，并通过 open-evidence
 * 事件把 node/edge id 交回 store 回查证据。</p>
 */
const props = defineProps({
  payload: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['open-evidence'])
const width = 960
const height = 560
const centerX = width / 2
const centerY = height / 2

const nodes = computed(() => props.payload?.nodes || [])
const edges = computed(() => props.payload?.edges || [])
const layoutNodes = computed(() => {
  const count = Math.max(1, nodes.value.length)
  // 使用固定半径布局，保证同一 payload 在刷新后位置稳定，便于用户对照证据。
  const radius = count < 8 ? 150 : 220
  return nodes.value.map((node, index) => {
    const angle = (Math.PI * 2 * index) / count - Math.PI / 2
    return {
      ...node,
      x: centerX + Math.cos(angle) * radius,
      y: centerY + Math.sin(angle) * radius,
      r: Math.min(26, 12 + Math.max(0, node.degree || 0) * 2),
      color: colorForType(node.type)
    }
  })
})
const nodeById = computed(() => new Map(layoutNodes.value.map((node) => [node.id, node])))
const layoutEdges = computed(() =>
  edges.value
    .map((edge) => ({
      ...edge,
      sourceNode: nodeById.value.get(edge.source),
      targetNode: nodeById.value.get(edge.target)
    }))
    .filter((edge) => edge.sourceNode && edge.targetNode)
)

function colorForType(type) {
  const palette = ['#0f766e', '#2563eb', '#7c3aed', '#b45309', '#15803d', '#be123c']
  const text = String(type || 'ENTITY')
  let hash = 0
  // 类型映射到稳定颜色，不依赖后端返回顺序，避免同一实体类型在刷新后换色。
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash + text.charCodeAt(index) * (index + 1)) % palette.length
  }
  return palette[hash]
}

function openNode(node) {
  emit('open-evidence', {
    type: 'node',
    id: node.id,
    label: node.label,
    meta: node.type
  })
}

function openEdge(edge) {
  emit('open-evidence', {
    type: 'edge',
    id: edge.id,
    label: edge.label,
    meta: `${edge.sourceNode.label} -> ${edge.targetNode.label}`
  })
}
</script>

<template>
  <section class="graph-viewer" aria-label="关系图">
    <p v-if="!nodes.length" class="panel-message">暂无关系图数据。</p>
    <svg v-else class="graph-viewer__canvas" :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="知识图谱关系图">
      <g class="graph-viewer__edges">
        <g v-for="edge in layoutEdges" :key="edge.id">
          <line
            class="graph-viewer__edge-hit"
            :x1="edge.sourceNode.x"
            :y1="edge.sourceNode.y"
            :x2="edge.targetNode.x"
            :y2="edge.targetNode.y"
            tabindex="0"
            role="button"
            :aria-label="`查看关系 ${edge.label} 的证据`"
            @click="openEdge(edge)"
            @keyup.enter="openEdge(edge)"
          />
          <line
            class="graph-viewer__edge"
            :x1="edge.sourceNode.x"
            :y1="edge.sourceNode.y"
            :x2="edge.targetNode.x"
            :y2="edge.targetNode.y"
            :stroke-width="Math.min(5, 1 + edge.weight)"
          />
        </g>
      </g>
      <g class="graph-viewer__nodes">
        <g
          v-for="node in layoutNodes"
          :key="node.id"
          class="graph-viewer__node"
          tabindex="0"
          role="button"
          :aria-label="`查看实体 ${node.label} 的证据`"
          @click="openNode(node)"
          @keyup.enter="openNode(node)"
        >
          <circle :cx="node.x" :cy="node.y" :r="node.r" :fill="node.color" />
          <text :x="node.x" :y="node.y + node.r + 18" text-anchor="middle">{{ node.label }}</text>
        </g>
      </g>
    </svg>
  </section>
</template>
