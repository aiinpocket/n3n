import { create } from 'zustand'
import { Node, Edge } from '@xyflow/react'
import { Flow, FlowVersion, flowApi, FlowDefinition } from '../api/flow'
import { logger } from '../utils/logger'

interface FlowListState {
  flows: Flow[]
  totalElements: number
  loading: boolean
  currentPage: number
  pageSize: number
  searchQuery: string
  fetchFlows: (page?: number, size?: number, search?: string) => Promise<void>
  setSearchQuery: (query: string) => void
  createFlow: (name: string, description?: string) => Promise<Flow>
  updateFlow: (id: string, name?: string, description?: string) => Promise<Flow>
  deleteFlow: (id: string) => Promise<void>
}

interface FlowEditorState {
  currentFlow: Flow | null
  currentVersion: FlowVersion | null
  versions: FlowVersion[]
  nodes: Node[]
  edges: Edge[]
  selectedNodeId: string | null
  isDirty: boolean
  saving: boolean
  lastSavedAt: Date | null
  loadFlow: (flowId: string, version?: string) => Promise<void>
  loadVersions: (flowId: string) => Promise<void>
  setNodes: (nodes: Node[]) => void
  setEdges: (edges: Edge[]) => void
  onNodesChange: (changes: Node[]) => void
  onEdgesChange: (changes: Edge[]) => void
  setSelectedNodeId: (id: string | null) => void
  addNode: (node: Node) => void
  updateNode: (id: string, data: Partial<Node>) => void
  updateNodeData: (id: string, data: Record<string, unknown>) => void
  removeNode: (id: string) => void
  saveVersion: (version: string, settings?: Record<string, unknown>) => Promise<FlowVersion>
  autoSaveDraft: () => Promise<FlowVersion | null>
  publishVersion: (version: string) => Promise<FlowVersion>
  clearEditor: () => void
}

type FlowState = FlowListState & FlowEditorState

export const useFlowStore = create<FlowState>((set, get) => ({
  // List state
  flows: [],
  totalElements: 0,
  loading: false,
  currentPage: 0,
  pageSize: 20,
  searchQuery: '',

  // Editor state
  currentFlow: null,
  currentVersion: null,
  versions: [],
  nodes: [],
  edges: [],
  selectedNodeId: null,
  isDirty: false,
  saving: false,
  lastSavedAt: null,

  // List actions
  fetchFlows: async (page = 0, size = 20, search?: string) => {
    const query = search !== undefined ? search : get().searchQuery
    set({ loading: true })
    try {
      const response = await flowApi.listFlows(page, size, query || undefined)
      set({
        flows: response.content,
        totalElements: response.totalElements,
        currentPage: response.number,
        pageSize: response.size,
        searchQuery: query,
      })
    } finally {
      set({ loading: false })
    }
  },

  setSearchQuery: (query: string) => set({ searchQuery: query }),

  createFlow: async (name: string, description?: string) => {
    const flow = await flowApi.createFlow({ name, description })
    set((state) => ({ flows: [flow, ...state.flows] }))
    return flow
  },

  updateFlow: async (id: string, name?: string, description?: string) => {
    const flow = await flowApi.updateFlow(id, { name, description })
    set((state) => ({
      flows: state.flows.map((f) => (f.id === id ? flow : f)),
    }))
    return flow
  },

  deleteFlow: async (id: string) => {
    await flowApi.deleteFlow(id)
    set((state) => ({
      flows: state.flows.filter((f) => f.id !== id),
    }))
  },

  // Editor actions
  loadFlow: async (flowId: string, version?: string) => {
    set({ loading: true })
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
          isDirty: false,
        })
      } else {
        set({
          currentVersion: null,
          nodes: [],
          edges: [],
          isDirty: false,
        })
      }
    } finally {
      set({ loading: false })
    }
  },

  loadVersions: async (flowId: string) => {
    const versions = await flowApi.listVersions(flowId)
    set({ versions })
  },

  setNodes: (nodes) => set({ nodes, isDirty: true }),
  setEdges: (edges) => set({ edges, isDirty: true }),
  onNodesChange: (nodes) => set({ nodes, isDirty: true }),
  onEdgesChange: (edges) => set({ edges, isDirty: true }),
  setSelectedNodeId: (selectedNodeId) => set({ selectedNodeId }),

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

  saveVersion: async (version: string, settings?: Record<string, unknown>) => {
    const { currentFlow, nodes, edges } = get()
    if (!currentFlow) throw new Error('No flow loaded')

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
    if (!currentFlow) throw new Error('No flow loaded')

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
      isDirty: false,
    }),
}))
