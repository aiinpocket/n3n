import { create } from 'zustand'
import { Node, Edge } from '@xyflow/react'
import { Flow, FlowVersion, flowApi, FlowDefinition } from '../api/flow'
import { logger } from '../utils/logger'
import { ClipboardData, FlowSnapshot } from '../types'
import i18n from '../i18n'

interface FlowEditorState {
  currentFlow: Flow | null
  currentVersion: FlowVersion | null
  versions: FlowVersion[]
  nodes: Node[]
  edges: Edge[]
  selectedNodeId: string | null
  selectedNodeIds: string[]
  isDirty: boolean
  saving: boolean
  lastSavedAt: Date | null
  loading: boolean
  error: string | null
  // Clipboard
  clipboard: ClipboardData | null
  // History (Undo/Redo)
  history: FlowSnapshot[]
  historyIndex: number
  maxHistory: number
  // Pinned Data
  pinnedData: Record<string, unknown>
  // Actions
  loadFlow: (flowId: string, version?: string) => Promise<void>
  loadVersions: (flowId: string) => Promise<void>
  setNodes: (nodes: Node[]) => void
  setEdges: (edges: Edge[]) => void
  onNodesChange: (changes: Node[]) => void
  onEdgesChange: (changes: Edge[]) => void
  setSelectedNodeId: (id: string | null) => void
  setSelectedNodeIds: (ids: string[]) => void
  selectAllNodes: () => void
  addNode: (node: Node) => void
  updateNode: (id: string, data: Partial<Node>) => void
  updateNodeData: (id: string, data: Record<string, unknown>) => void
  removeNode: (id: string) => void
  removeSelectedNodes: () => void
  saveVersion: (version: string, settings?: Record<string, unknown>) => Promise<FlowVersion>
  autoSaveDraft: () => Promise<FlowVersion | null>
  publishVersion: (version: string) => Promise<FlowVersion>
  clearEditor: () => void
  // Clipboard actions
  copySelectedNodes: () => void
  cutSelectedNodes: () => void
  pasteNodes: (offsetX?: number, offsetY?: number) => void
  duplicateSelectedNodes: () => void
  // History actions
  pushHistory: () => void
  undo: () => void
  redo: () => void
  canUndo: () => boolean
  canRedo: () => boolean
  // Data Pinning actions
  pinNodeData: (nodeId: string, data: Record<string, unknown>) => Promise<void>
  unpinNodeData: (nodeId: string) => Promise<void>
  isNodePinned: (nodeId: string) => boolean
  getNodePinnedData: (nodeId: string) => Record<string, unknown> | null
}

export const useFlowEditorStore = create<FlowEditorState>((set, get) => ({
  currentFlow: null,
  currentVersion: null,
  versions: [],
  nodes: [],
  edges: [],
  selectedNodeId: null,
  selectedNodeIds: [],
  isDirty: false,
  saving: false,
  lastSavedAt: null,
  loading: false,
  error: null,
  // Clipboard
  clipboard: null,
  // History
  history: [],
  historyIndex: -1,
  maxHistory: 50,
  // Pinned Data
  pinnedData: {},

  loadFlow: async (flowId: string, version?: string) => {
    set({ loading: true, error: null })
    try {
      const flow = await flowApi.getFlow(flowId)
      set({ currentFlow: flow })

      let flowVersion: FlowVersion | null = null
      if (version) {
        flowVersion = await flowApi.getVersion(flowId, version)
      } else if (flow.latestVersion) {
        flowVersion = await flowApi.getVersion(flowId, flow.latestVersion)
      }

      if (flowVersion) {
        const definition = flowVersion.definition || { nodes: [], edges: [] }
        set({
          currentVersion: flowVersion,
          nodes: definition.nodes.map((n) => ({
            ...n,
            data: n.data || {},
          })) as Node[],
          edges: definition.edges as Edge[],
          pinnedData: flowVersion.pinnedData || {},
          isDirty: false,
        })
      } else {
        set({
          currentVersion: null,
          nodes: [],
          edges: [],
          pinnedData: {},
          isDirty: false,
        })
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load flow'
      logger.error('Failed to load flow:', msg)
      set({ error: msg, currentFlow: null })
    } finally {
      set({ loading: false })
    }
  },

  loadVersions: async (flowId: string) => {
    try {
      const versions = await flowApi.listVersions(flowId)
      set({ versions })
    } catch (err) {
      logger.error('Failed to load versions:', err)
    }
  },

  setNodes: (nodes) => set({ nodes, isDirty: true }),
  setEdges: (edges) => set({ edges, isDirty: true }),
  onNodesChange: (nodes) => set({ nodes, isDirty: true }),
  onEdgesChange: (edges) => set({ edges, isDirty: true }),
  setSelectedNodeId: (selectedNodeId) => set({ selectedNodeId, selectedNodeIds: selectedNodeId ? [selectedNodeId] : [] }),

  setSelectedNodeIds: (selectedNodeIds) => set({
    selectedNodeIds,
    selectedNodeId: selectedNodeIds.length > 0 ? selectedNodeIds[0] : null
  }),

  selectAllNodes: () => {
    const { nodes } = get()
    set({
      selectedNodeIds: nodes.map(n => n.id),
      selectedNodeId: nodes.length > 0 ? nodes[0].id : null
    })
  },

  addNode: (node) =>
    set((state) => ({
      nodes: [...state.nodes, node],
      isDirty: true,
    })),

  updateNode: (id, data) =>
    set((state) => ({
      nodes: state.nodes.map((n) => (n.id === id ? { ...n, ...data } : n)),
      isDirty: true,
    })),

  updateNodeData: (id, data) =>
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id
          ? { ...n, data: { ...(n.data as Record<string, unknown>), ...data } }
          : n
      ),
      isDirty: true,
    })),

  removeNode: (id) =>
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      isDirty: true,
    })),

  removeSelectedNodes: () => {
    const { selectedNodeIds, nodes, edges } = get()
    if (selectedNodeIds.length === 0) return

    get().pushHistory()
    set({
      nodes: nodes.filter((n) => !selectedNodeIds.includes(n.id)),
      edges: edges.filter((e) => !selectedNodeIds.includes(e.source) && !selectedNodeIds.includes(e.target)),
      selectedNodeIds: [],
      selectedNodeId: null,
      isDirty: true,
    })
  },

  // Clipboard actions
  copySelectedNodes: () => {
    const { selectedNodeIds, nodes, edges } = get()
    if (selectedNodeIds.length === 0) return

    const selectedNodes = nodes.filter((n) => selectedNodeIds.includes(n.id))
    const selectedEdges = edges.filter(
      (e) => selectedNodeIds.includes(e.source) && selectedNodeIds.includes(e.target)
    )

    set({
      clipboard: {
        nodes: selectedNodes.map((n) => ({
          id: n.id,
          type: n.type || 'default',
          position: n.position,
          data: n.data as Record<string, unknown>,
        })),
        edges: selectedEdges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle || undefined,
          targetHandle: e.targetHandle || undefined,
        })),
        timestamp: Date.now(),
      },
    })
  },

  cutSelectedNodes: () => {
    const { copySelectedNodes, removeSelectedNodes } = get()
    copySelectedNodes()
    removeSelectedNodes()
  },

  pasteNodes: (offsetX = 50, offsetY = 50) => {
    const { clipboard, nodes, edges } = get()
    if (!clipboard || clipboard.nodes.length === 0) return

    get().pushHistory()

    // Create ID mapping for new nodes
    const idMap: Record<string, string> = {}
    const newNodes: Node[] = clipboard.nodes.map((n) => {
      const newId = `node-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`
      idMap[n.id] = newId
      return {
        id: newId,
        type: n.type,
        position: { x: n.position.x + offsetX, y: n.position.y + offsetY },
        data: { ...n.data },
      }
    })

    // Create new edges with updated IDs
    const newEdges: Edge[] = clipboard.edges
      .filter((e) => idMap[e.source] && idMap[e.target])
      .map((e) => ({
        id: `edge-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`,
        source: idMap[e.source],
        target: idMap[e.target],
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
      }))

    set({
      nodes: [...nodes, ...newNodes],
      edges: [...edges, ...newEdges],
      selectedNodeIds: newNodes.map((n) => n.id),
      selectedNodeId: newNodes.length > 0 ? newNodes[0].id : null,
      isDirty: true,
    })
  },

  duplicateSelectedNodes: () => {
    const { copySelectedNodes, pasteNodes } = get()
    copySelectedNodes()
    pasteNodes(30, 30)
  },

  // History actions
  pushHistory: () => {
    const { nodes, edges, history, historyIndex, maxHistory } = get()
    const snapshot: FlowSnapshot = {
      nodes: nodes.map((n) => ({
        id: n.id,
        type: n.type || 'default',
        position: n.position,
        data: n.data as Record<string, unknown>,
      })),
      edges: edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle || undefined,
        targetHandle: e.targetHandle || undefined,
      })),
      timestamp: Date.now(),
    }

    // Remove any future history if we're not at the end
    const newHistory = history.slice(0, historyIndex + 1)
    newHistory.push(snapshot)

    // Limit history size
    if (newHistory.length > maxHistory) {
      newHistory.shift()
    }

    set({
      history: newHistory,
      historyIndex: newHistory.length - 1,
    })
  },

  undo: () => {
    const { history, historyIndex, nodes, edges } = get()
    if (historyIndex < 0) return

    // Save current state for redo if at the tip of history.
    // This appends the current (unsaved) state so redo can restore it.
    // We also advance historyIndex to point at the newly saved current state,
    // so the subsequent decrement will land on the correct previous snapshot.
    let updatedHistory = history
    let currentIndex = historyIndex
    if (historyIndex === history.length - 1) {
      const currentSnapshot: FlowSnapshot = {
        nodes: nodes.map((n) => ({
          id: n.id,
          type: n.type || 'default',
          position: n.position,
          data: n.data as Record<string, unknown>,
        })),
        edges: edges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle || undefined,
          targetHandle: e.targetHandle || undefined,
        })),
        timestamp: Date.now(),
      }
      updatedHistory = [...history, currentSnapshot]
      currentIndex = updatedHistory.length - 1
    }

    // Move back one step and restore that snapshot.
    // historyIndex now represents the currently displayed state in history.
    const prevIndex = currentIndex - 1
    const snapshot = updatedHistory[prevIndex]
    if (!snapshot) return

    set({
      history: updatedHistory,
      nodes: snapshot.nodes as Node[],
      edges: snapshot.edges as Edge[],
      historyIndex: prevIndex,
      isDirty: true,
    })
  },

  redo: () => {
    const { history, historyIndex } = get()
    if (historyIndex >= history.length - 1) return

    const nextIndex = historyIndex + 1
    const snapshot = history[nextIndex]
    if (!snapshot) return

    set({
      nodes: snapshot.nodes as Node[],
      edges: snapshot.edges as Edge[],
      historyIndex: nextIndex,
      isDirty: true,
    })
  },

  canUndo: () => {
    const { historyIndex, history } = get()
    if (historyIndex < 0) return false
    // Can undo if there are earlier snapshots, or if at the tip (current state will be saved first)
    return historyIndex > 0 || historyIndex === history.length - 1
  },
  canRedo: () => get().historyIndex < get().history.length - 1,

  saveVersion: async (version: string, settings?: Record<string, unknown>) => {
    const { currentFlow, nodes, edges } = get()
    if (!currentFlow) throw new Error(i18n.t('flow.noFlowLoaded'))

    set({ saving: true })
    try {
      const definition: FlowDefinition = {
        nodes: nodes.map((n) => ({
          id: n.id,
          type: n.type || 'default',
          position: n.position,
          data: n.data as Record<string, unknown>,
        })),
        edges: edges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle || undefined,
          targetHandle: e.targetHandle || undefined,
        })),
      }

      const flowVersion = await flowApi.saveVersion(currentFlow.id, {
        version,
        definition,
        settings,
      })

      set({
        currentVersion: flowVersion,
        isDirty: false,
        lastSavedAt: new Date(),
      })

      return flowVersion
    } finally {
      set({ saving: false })
    }
  },

  autoSaveDraft: async () => {
    const { currentFlow, currentVersion, nodes, edges, isDirty, saving } = get()
    if (!currentFlow || !isDirty || saving) return null

    // Generate draft version name based on current version or timestamp
    const draftVersion = currentVersion?.status === 'draft'
      ? currentVersion.version
      : `draft-${Date.now()}`

    set({ saving: true })
    try {
      const definition: FlowDefinition = {
        nodes: nodes.map((n) => ({
          id: n.id,
          type: n.type || 'default',
          position: n.position,
          data: n.data as Record<string, unknown>,
        })),
        edges: edges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle || undefined,
          targetHandle: e.targetHandle || undefined,
        })),
      }

      const flowVersion = await flowApi.saveVersion(currentFlow.id, {
        version: draftVersion,
        definition,
      })

      set({
        currentVersion: flowVersion,
        isDirty: false,
        lastSavedAt: new Date(),
      })

      return flowVersion
    } catch (error) {
      logger.error('Auto-save failed:', error)
      return null
    } finally {
      set({ saving: false })
    }
  },

  publishVersion: async (version: string) => {
    const { currentFlow } = get()
    if (!currentFlow) throw new Error(i18n.t('flow.noFlowLoaded'))

    const flowVersion = await flowApi.publishVersion(currentFlow.id, version)
    set((state) => ({
      currentVersion: flowVersion,
      versions: state.versions.map((v) =>
        v.version === version
          ? flowVersion
          : v.status === 'published'
          ? { ...v, status: 'deprecated' as const }
          : v
      ),
    }))

    return flowVersion
  },

  clearEditor: () =>
    set({
      currentFlow: null,
      currentVersion: null,
      versions: [],
      nodes: [],
      edges: [],
      selectedNodeId: null,
      selectedNodeIds: [],
      isDirty: false,
      history: [],
      historyIndex: -1,
      pinnedData: {},
    }),

  // Data Pinning actions
  pinNodeData: async (nodeId: string, data: Record<string, unknown>) => {
    const { currentFlow, currentVersion } = get()
    if (!currentFlow || !currentVersion) {
      throw new Error(i18n.t('flow.noFlowOrVersionLoaded'))
    }

    await flowApi.pinNodeData(currentFlow.id, currentVersion.version, { nodeId, data })

    set((state) => ({
      pinnedData: { ...state.pinnedData, [nodeId]: data },
    }))
  },

  unpinNodeData: async (nodeId: string) => {
    const { currentFlow, currentVersion } = get()
    if (!currentFlow || !currentVersion) {
      throw new Error(i18n.t('flow.noFlowOrVersionLoaded'))
    }

    await flowApi.unpinNodeData(currentFlow.id, currentVersion.version, nodeId)

    set((state) => {
      const newPinnedData = { ...state.pinnedData }
      delete newPinnedData[nodeId]
      return { pinnedData: newPinnedData }
    })
  },

  isNodePinned: (nodeId: string) => {
    const { pinnedData } = get()
    return nodeId in pinnedData
  },

  getNodePinnedData: (nodeId: string) => {
    const { pinnedData } = get()
    return (pinnedData[nodeId] as Record<string, unknown>) || null
  },
}))
