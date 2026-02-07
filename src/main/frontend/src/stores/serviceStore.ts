import { create } from 'zustand'
import { serviceApi } from '../api/service'
import { extractApiError } from '../utils/errorMessages'
import type {
  ExternalService,
  ExternalServiceDetail,
  CreateServiceRequest,
  UpdateServiceRequest,
  CreateEndpointRequest,
  ServiceEndpoint,
} from '../types'

interface ServiceState {
  services: ExternalService[]
  currentService: ExternalServiceDetail | null
  isLoading: boolean
  error: string | null
  totalElements: number
  currentPage: number
  pageSize: number

  fetchServices: (page?: number, size?: number) => Promise<void>
  fetchService: (id: string) => Promise<void>
  createService: (data: CreateServiceRequest) => Promise<ExternalService>
  updateService: (id: string, data: UpdateServiceRequest) => Promise<void>
  deleteService: (id: string) => Promise<void>
  refreshSchema: (id: string) => Promise<{ addedEndpoints: number; updatedEndpoints: number }>
  testConnection: (id: string) => Promise<{ success: boolean; latencyMs: number; message: string }>
  createEndpoint: (serviceId: string, data: CreateEndpointRequest) => Promise<ServiceEndpoint>
  updateEndpoint: (serviceId: string, endpointId: string, data: CreateEndpointRequest) => Promise<void>
  deleteEndpoint: (serviceId: string, endpointId: string) => Promise<void>
  clearError: () => void
  clearCurrentService: () => void
}

export const useServiceStore = create<ServiceState>((set, get) => ({
  services: [],
  currentService: null,
  isLoading: false,
  error: null,
  totalElements: 0,
  currentPage: 0,
  pageSize: 20,

  fetchServices: async (page = 0, size = 20) => {
    set({ isLoading: true, error: null })
    try {
      const response = await serviceApi.listServices(page, size)
      set({
        services: response.content,
        totalElements: response.totalElements,
        currentPage: response.number,
        pageSize: response.size,
        isLoading: false,
      })
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to fetch services')
      set({ error: message, isLoading: false })
    }
  },

  fetchService: async (id: string) => {
    set({ isLoading: true, error: null })
    try {
      const service = await serviceApi.getService(id)
      set({ currentService: service, isLoading: false })
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to fetch service')
      set({ error: message, isLoading: false })
    }
  },

  createService: async (data: CreateServiceRequest) => {
    set({ isLoading: true, error: null })
    try {
      const service = await serviceApi.createService(data)
      set((state) => ({
        services: [service, ...state.services],
        isLoading: false,
      }))
      return service
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to create service')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  updateService: async (id: string, data: UpdateServiceRequest) => {
    set({ isLoading: true, error: null })
    try {
      const updated = await serviceApi.updateService(id, data)
      set((state) => ({
        services: state.services.map((s) => (s.id === id ? { ...s, ...updated } : s)),
        currentService: state.currentService?.id === id ? { ...state.currentService, ...updated } : state.currentService,
        isLoading: false,
      }))
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to update service')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  deleteService: async (id: string) => {
    set({ isLoading: true, error: null })
    try {
      await serviceApi.deleteService(id)
      set((state) => ({
        services: state.services.filter((s) => s.id !== id),
        isLoading: false,
      }))
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to delete service')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  refreshSchema: async (id: string) => {
    set({ isLoading: true, error: null })
    try {
      const result = await serviceApi.refreshSchema(id)
      await get().fetchService(id)
      set({ isLoading: false })
      return { addedEndpoints: result.addedEndpoints, updatedEndpoints: result.updatedEndpoints }
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to refresh schema')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  testConnection: async (id: string) => {
    try {
      return await serviceApi.testConnection(id)
    } catch (error: unknown) {
      const message = extractApiError(error, 'Connection test failed')
      return { success: false, latencyMs: 0, message }
    }
  },

  createEndpoint: async (serviceId: string, data: CreateEndpointRequest) => {
    set({ isLoading: true, error: null })
    try {
      const endpoint = await serviceApi.createEndpoint(serviceId, data)
      set((state) => {
        if (state.currentService?.id === serviceId) {
          return {
            currentService: {
              ...state.currentService,
              endpoints: [...state.currentService.endpoints, endpoint],
            },
            isLoading: false,
          }
        }
        return { isLoading: false }
      })
      return endpoint
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to create endpoint')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  updateEndpoint: async (serviceId: string, endpointId: string, data: CreateEndpointRequest) => {
    set({ isLoading: true, error: null })
    try {
      const updated = await serviceApi.updateEndpoint(serviceId, endpointId, data)
      set((state) => {
        if (state.currentService?.id === serviceId) {
          return {
            currentService: {
              ...state.currentService,
              endpoints: state.currentService.endpoints.map((e) =>
                e.id === endpointId ? updated : e
              ),
            },
            isLoading: false,
          }
        }
        return { isLoading: false }
      })
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to update endpoint')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  deleteEndpoint: async (serviceId: string, endpointId: string) => {
    set({ isLoading: true, error: null })
    try {
      await serviceApi.deleteEndpoint(serviceId, endpointId)
      set((state) => {
        if (state.currentService?.id === serviceId) {
          return {
            currentService: {
              ...state.currentService,
              endpoints: state.currentService.endpoints.filter((e) => e.id !== endpointId),
            },
            isLoading: false,
          }
        }
        return { isLoading: false }
      })
    } catch (error: unknown) {
      const message = extractApiError(error, 'Failed to delete endpoint')
      set({ error: message, isLoading: false })
      throw error
    }
  },

  clearError: () => set({ error: null }),
  clearCurrentService: () => set({ currentService: null }),
}))
