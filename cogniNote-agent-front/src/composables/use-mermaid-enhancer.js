import { nextTick } from 'vue'
import { renderMermaidSourceHtml, trimFenceBoundaryBlankLines } from '../utils/mermaid-source-highlight'

const MERMAID_FIT_PADDING_PX = 24
// markstream-vue 的缩小按钮有内部下限；这里统一接管增强后的控件，保证百分比和 10% 下限同步。
const MERMAID_ZOOM_MIN = 0.1
const MERMAID_ZOOM_MAX = 3
const MERMAID_ZOOM_STEP = 0.1
const MERMAID_ZOOM_DEFAULT = 1
const MERMAID_WHEEL_ZOOM_RATIO = 0.01
const MERMAID_WHEEL_EVENT_OPTIONS = { capture: true, passive: false }
const MERMAID_FIT_ICON = '<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24" aria-hidden="true" class="action-icon"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/><path d="M12 8v8"/><path d="M8 12h8"/></g></svg>'

export function useMermaidEnhancer(markdownRoot) {
  let sourceObserver = null
  let modalObserver = null
  let enhanceQueued = false
  let dragState = null
  const sourceCache = new WeakMap()
  const zoomState = new WeakMap()

  function mount() {
    queueEnhance()
    document.addEventListener('click', handleControlClick, true)
    document.addEventListener('mousedown', handleDragStart, true)
    document.addEventListener('mousemove', handleDragMove, true)
    document.addEventListener('mouseup', handleDragEnd, true)
    document.addEventListener('wheel', handleWheel, MERMAID_WHEEL_EVENT_OPTIONS)
    if (typeof MutationObserver === 'undefined' || !markdownRoot.value) {
      return
    }
    sourceObserver = new MutationObserver(() => {
      queueEnhance()
    })
    sourceObserver.observe(markdownRoot.value, {
      childList: true,
      subtree: true,
      characterData: true
    })
    if (document.body) {
      modalObserver = new MutationObserver(() => {
        queueEnhance()
      })
      modalObserver.observe(document.body, {
        childList: true,
        subtree: true
      })
    }
  }

  function unmount() {
    document.removeEventListener('click', handleControlClick, true)
    document.removeEventListener('mousedown', handleDragStart, true)
    document.removeEventListener('mousemove', handleDragMove, true)
    document.removeEventListener('mouseup', handleDragEnd, true)
    document.removeEventListener('wheel', handleWheel, MERMAID_WHEEL_EVENT_OPTIONS)
    dragState = null
    sourceObserver?.disconnect()
    sourceObserver = null
    modalObserver?.disconnect()
    modalObserver = null
  }

  function queueEnhance() {
    if (enhanceQueued) {
      return
    }
    enhanceQueued = true
    nextTick(() => {
      enhanceQueued = false
      enhanceMermaidBlocks()
    })
  }

  function enhanceMermaidBlocks() {
    const root = markdownRoot.value
    if (root) {
      const sourceBlocks = Array.from(root.querySelectorAll('.mermaid-source-code'))
      for (const sourceBlock of sourceBlocks) {
        enhanceSourceBlock(sourceBlock)
      }

      const mermaidBlocks = Array.from(root.querySelectorAll('.mermaid-block-container'))
      for (const mermaidBlock of mermaidBlocks) {
        ensureInlineFitButton(mermaidBlock)
      }
    }

    ensureModalFitButtons()
  }

  function enhanceSourceBlock(sourceBlock) {
    const source = trimFenceBoundaryBlankLines(sourceBlock.textContent ?? '')
    if (!source) {
      return
    }
    if (sourceBlock.dataset.cogninoteMermaidHighlighted === 'true' && sourceCache.get(sourceBlock) === source) {
      return
    }

    try {
      // markstream-vue 的 Mermaid Source 不是普通 code_block；这里只替换源码内容，不接管预览、Open、导出等图表能力。
      sourceBlock.innerHTML = renderMermaidSourceHtml(source)
      sourceBlock.dataset.cogninoteMermaidHighlighted = 'true'
      sourceCache.set(sourceBlock, source)
      sourceBlock.classList.add('cogninote-mermaid-source-highlight', 'language-mermaid')
    } catch {
      sourceBlock.textContent = source
      sourceBlock.dataset.cogninoteMermaidHighlighted = 'false'
      sourceCache.set(sourceBlock, source)
      sourceBlock.classList.remove('cogninote-mermaid-source-highlight')
    }
  }

  function ensureInlineFitButton(mermaidBlock) {
    const controls = findInlineZoomControls(mermaidBlock)
    if (!controls) {
      return
    }

    ensureFitButton(controls)
  }

  function ensureModalFitButtons() {
    const panels = Array.from(document.querySelectorAll('.mermaid-modal-panel, .mermaid-modal-overlay .dialog-panel'))
    for (const panel of panels) {
      const controls = findModalZoomControls(panel)
      if (controls) {
        ensureFitButton(controls)
      }
    }
  }

  function ensureFitButton(controls) {
    if (controls.querySelector('.cogninote-mermaid-fit-action')) {
      return
    }

    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'mermaid-action-btn cogninote-mermaid-fit-action p-[var(--ms-action-btn-padding)] rounded transition-colors'
    button.title = '自适应'
    button.setAttribute('aria-label', '自适应显示 Mermaid 图表')
    button.innerHTML = `${MERMAID_FIT_ICON}<span class="cogninote-mermaid-fit-label">自适应</span>`
    const { reset } = getZoomControls(controls)
    controls.insertBefore(button, reset || null)
  }

  function handleControlClick(event) {
    const button = event.target?.closest?.('button.mermaid-action-btn')
    if (!button || !button.parentElement) {
      return
    }
    const context = resolveFitContext(button)
    if (!context) {
      return
    }
    const { zoomIn, zoomOut, reset } = context.zoomControls
    if (button.classList.contains('cogninote-mermaid-fit-action')) {
      stopNativeEvent(event)
      void fitToViewport(context)
      return
    }
    if (button === zoomIn) {
      stopNativeEvent(event)
      stepZoom(context, MERMAID_ZOOM_STEP)
      return
    }
    if (button === zoomOut) {
      stopNativeEvent(event)
      stepZoom(context, -MERMAID_ZOOM_STEP)
      return
    }
    if (button === reset) {
      stopNativeEvent(event)
      resetZoom(context)
    }
  }

  async function fitToViewport(context) {
    resetZoom(context)
    await waitForFrame()

    const targetScale = getFitScale(context)
    setZoomTransform(context, {
      translateX: 0,
      translateY: 0,
      scale: targetScale
    })
    await waitForFrame()

    centerWithTransform(context)
  }

  function stepZoom(context, delta) {
    const current = getZoomState(context.wrapper)
    setZoomTransform(context, {
      ...current,
      scale: current.scale + delta
    })
  }

  function resetZoom(context) {
    setZoomTransform(context, {
      translateX: 0,
      translateY: 0,
      scale: MERMAID_ZOOM_DEFAULT
    })
  }

  function centerWithTransform(context) {
    const { viewport, svg } = context
    const viewportRect = viewport.getBoundingClientRect()
    const svgRect = svg.getBoundingClientRect()
    if (!isValidRect(viewportRect) || !isValidRect(svgRect)) {
      return
    }

    const current = getZoomState(context.wrapper)
    const targetLeft = viewportRect.left + (viewportRect.width - svgRect.width) / 2
    const targetTop = viewportRect.top + (viewportRect.height - svgRect.height) / 2
    setZoomTransform(context, {
      ...current,
      translateX: current.translateX + targetLeft - svgRect.left,
      translateY: current.translateY + targetTop - svgRect.top
    })
  }

  function handleDragStart(event) {
    if (event.button !== 0 || event.target?.closest?.('button, a, input, textarea, select')) {
      return
    }
    const context = resolveSurfaceContext(event.target)
    if (!context) {
      return
    }

    stopNativeEvent(event)
    const current = getZoomState(context.wrapper)
    dragState = {
      context,
      startX: event.clientX,
      startY: event.clientY,
      startTranslateX: current.translateX,
      startTranslateY: current.translateY
    }
  }

  function handleDragMove(event) {
    if (!dragState) {
      return
    }
    stopNativeEvent(event)
    const { context, startX, startY, startTranslateX, startTranslateY } = dragState
    const current = getZoomState(context.wrapper)
    setZoomTransform(context, {
      ...current,
      translateX: startTranslateX + event.clientX - startX,
      translateY: startTranslateY + event.clientY - startY
    })
  }

  function handleDragEnd(event) {
    if (!dragState) {
      return
    }
    stopNativeEvent(event)
    dragState = null
  }

  function handleWheel(event) {
    if (!event.ctrlKey && !event.metaKey) {
      return
    }
    const context = resolveSurfaceContext(event.target)
    if (!context) {
      return
    }

    stopNativeEvent(event)
    const viewportRect = context.viewport.getBoundingClientRect()
    if (!isValidRect(viewportRect)) {
      return
    }

    const current = getZoomState(context.wrapper)
    const originX = event.clientX - viewportRect.left - viewportRect.width / 2
    const originY = event.clientY - viewportRect.top - viewportRect.height / 2
    const contentX = (originX - current.translateX) / current.scale
    const contentY = (originY - current.translateY) / current.scale
    const scale = normalizeScale(current.scale - event.deltaY * MERMAID_WHEEL_ZOOM_RATIO)
    if (scale === current.scale) {
      return
    }

    setZoomTransform(context, {
      translateX: originX - contentX * scale,
      translateY: originY - contentY * scale,
      scale
    })
  }

  function resolveFitContext(button) {
    const controls = button.parentElement
    const panel = button.closest('.mermaid-modal-panel, .mermaid-modal-overlay .dialog-panel')
    if (panel && !button.closest('.fullscreen')) {
      const clone = panel.querySelector('[data-mermaid-modal-clone="1"], .fullscreen')
      const surface = clone?.parentElement
      return createFitContext({ controls, surface, viewport: surface, root: clone })
    }

    const mermaidBlock = button.closest('.mermaid-block-container')
    const preview = mermaidBlock?.querySelector('.mermaid-preview-area')
    return createFitContext({ controls, surface: preview, viewport: preview, root: mermaidBlock })
  }

  function resolveSurfaceContext(target) {
    if (!target?.closest) {
      return null
    }

    const panel = target.closest('.mermaid-modal-panel, .mermaid-modal-overlay .dialog-panel')
    if (panel) {
      const clone = panel.querySelector('[data-mermaid-modal-clone="1"], .fullscreen')
      const surface = clone?.parentElement
      const controls = findModalZoomControls(panel)
      if (surface?.contains(target)) {
        return createFitContext({ controls, surface, viewport: surface, root: clone })
      }
    }

    const mermaidBlock = target.closest('.mermaid-block-container')
    const preview = mermaidBlock?.querySelector('.mermaid-preview-area')
    if (preview?.contains(target)) {
      return createFitContext({ controls: findInlineZoomControls(mermaidBlock), surface: preview, viewport: preview, root: mermaidBlock })
    }
    return null
  }

  function createFitContext({ controls, surface, viewport, root }) {
    const wrapper = root?.querySelector('[data-mermaid-wrapper]')
    const svg = root?.querySelector('[data-mermaid-svg-layer] svg, ._mermaid svg')
    const zoomControls = getZoomControls(controls)
    if (!controls || !surface || !viewport || !wrapper || !svg || !zoomControls.reset || !zoomControls.zoomIn || !zoomControls.zoomOut) {
      return null
    }
    return {
      controls,
      surface,
      viewport,
      wrapper,
      svg,
      zoomControls
    }
  }

  function getFitScale({ viewport, svg }) {
    const viewportRect = viewport.getBoundingClientRect()
    const svgRect = svg.getBoundingClientRect()
    if (!isValidRect(viewportRect) || !isValidRect(svgRect)) {
      return 1
    }

    const availableWidth = Math.max(1, viewportRect.width - MERMAID_FIT_PADDING_PX * 2)
    const availableHeight = Math.max(1, viewportRect.height - MERMAID_FIT_PADDING_PX * 2)
    const scale = Math.min(1, availableWidth / svgRect.width, availableHeight / svgRect.height)
    return normalizeScale(scale)
  }

  function getZoomControls(controls) {
    const buttons = Array.from(controls?.children || [])
      .filter((item) => item.matches?.('button.mermaid-action-btn') && !item.classList.contains('cogninote-mermaid-fit-action'))
    return {
      zoomIn: buttons[0] || null,
      zoomOut: buttons[1] || null,
      reset: buttons.find((button) => /%/.test(button.textContent || '')) || null
    }
  }

  function setZoomTransform(context, nextState) {
    const state = normalizeZoomState(nextState)
    context.wrapper.style.transform = `translate(${toCssNumber(state.translateX)}px, ${toCssNumber(state.translateY)}px) scale(${toCssNumber(state.scale)})`
    zoomState.set(context.wrapper, state)
    updateZoomLabel(context.zoomControls, state.scale)
  }

  function updateZoomLabel({ reset }, scale) {
    if (reset) {
      reset.textContent = `${Math.round(scale * 100)}%`
    }
  }

  function getZoomState(wrapper) {
    return normalizeZoomState(parseTransform(wrapper?.style?.transform) || zoomState.get(wrapper))
  }

  return {
    mount,
    unmount,
    queueEnhance
  }
}

function findInlineZoomControls(mermaidBlock) {
  const preview = mermaidBlock.querySelector('.mermaid-preview-area')
  const previewShell = preview?.parentElement
  if (!previewShell) {
    return null
  }
  const zoomShell = Array.from(previewShell.children).find((child) => {
    return child !== preview && child.querySelectorAll?.('button.mermaid-action-btn').length >= 3
  })
  return zoomShell?.firstElementChild || zoomShell || null
}

function findModalZoomControls(panel) {
  return Array.from(panel.children).find((child) => {
    const buttons = Array.from(child.children || []).filter((item) => item.matches?.('button.mermaid-action-btn'))
    return buttons.length >= 3 && !child.querySelector?.('.fullscreen')
  }) || null
}

function parseTransform(transform) {
  if (!transform || transform === 'none') {
    return null
  }
  const translate = transform.match(/translate\(\s*(-?\d+(?:\.\d+)?)px\s*,\s*(-?\d+(?:\.\d+)?)px\s*\)/)
  const scale = transform.match(/scale\(\s*(-?\d+(?:\.\d+)?)\s*\)/)
  if (translate || scale) {
    return {
      translateX: translate ? Number.parseFloat(translate[1]) : 0,
      translateY: translate ? Number.parseFloat(translate[2]) : 0,
      scale: scale ? Number.parseFloat(scale[1]) : MERMAID_ZOOM_DEFAULT
    }
  }

  const matrix = transform.match(/matrix\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)/)
  if (!matrix) {
    return null
  }
  return {
    translateX: Number.parseFloat(matrix[5]),
    translateY: Number.parseFloat(matrix[6]),
    scale: Number.parseFloat(matrix[1])
  }
}

function normalizeZoomState(state) {
  return {
    translateX: toFiniteNumber(state?.translateX, 0),
    translateY: toFiniteNumber(state?.translateY, 0),
    scale: normalizeScale(state?.scale ?? MERMAID_ZOOM_DEFAULT)
  }
}

function normalizeScale(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return MERMAID_ZOOM_DEFAULT
  }
  return Math.max(MERMAID_ZOOM_MIN, Math.min(MERMAID_ZOOM_MAX, Math.round(numeric * 10) / 10))
}

function toFiniteNumber(value, fallback) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

function toCssNumber(value) {
  const rounded = Math.round(value * 1000) / 1000
  return Math.abs(rounded) < 0.001 ? 0 : rounded
}

function stopNativeEvent(event) {
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation?.()
}

function waitForFrame() {
  return new Promise((resolve) => {
    window.requestAnimationFrame(() => resolve())
  })
}

function isValidRect(rect) {
  return Number.isFinite(rect?.width) && Number.isFinite(rect?.height) && rect.width > 0 && rect.height > 0
}
