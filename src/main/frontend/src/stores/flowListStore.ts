import { create } from 'zustand'
import { Flow, flowApi } from '../api/flow'
import { extractApiError } from '../utils/errorMessages'

interface FlowListState {
  flows: Flow[]
  totalElements: number
  loading: boolean
  error: string | null
  currentPage: number
  pageSize: number
  searchQuery: string
  fetchFlows: (page?: number, size?: number, search?: string) => Promise<void>
  setSearchQuery: (query: string) => void
  createFlow: (name: string, description?: string) => Promise<Flow>
  updateFlow: (id: string, name?: string, description?: string) => Promise<Flow>
  deleteFlow: (id: string) => Promise<void>
  clearError: () => void
}

let fetchRequestId = 0

export const useFlowListStore = create<FlowListState>((set, get) => ({
  flows: [],
  totalElements: 0,
  loading: false,
  error: null,
  currentPage: 0,
  pageSize: 20,
  searchQuery: '',

  fetchFlows: async (page = 0, size = 20, search?: string) => {
    const query = search !== undefined ? search : get().searchQuery
    const requestId = ++fetchRequestId
    set({ loading: true, error: null })
    try {
      const response = await flowApi.listFlows(page, size, query || undefined)
      if (requestId === fetchRequestId) {
        set({
          flows: response.content,
          totalElements: response.totalElements,
          currentPage: response.number,
          pageSize: response.size,
          searchQuery: query,
        })
      }
    } catch (error: unknown) {
      if (requestId === fetchRequestId) {
        set({ error: extractApiError(error, 'Failed to load flows') })
      }
    } finally {
      if (requestId === fetchRequestId) {
        set({ loading: false })
      }
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

  clearError: () => set({ error: null }),
}))
