import apiClient from './client'

export interface Flow {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  createdBy: string
  latestVersion: string | null
  publishedVersion: string | null
}

export interface FlowVersion {
  id: string
  flowId: string
  version: string
  definition: FlowDefinition
  settings: Record<string, unknown>
  status: 'draft' | 'published' | 'deprecated'
  createdAt: string
  createdBy: string
}

export interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
  viewport?: { x: number; y: number; zoom: number }
}

export interface FlowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: Record<string, unknown>
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

export interface CreateFlowRequest {
  name: string
  description?: string
}

export interface UpdateFlowRequest {
  name?: string
  description?: string
}

export interface SaveVersionRequest {
  version: string
  definition: FlowDefinition
  settings?: Record<string, unknown>
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const flowApi = {
  listFlows: async (page = 0, size = 20, search?: string): Promise<PageResponse<Flow>> => {
    const params: Record<string, unknown> = { page, size }
    if (search) params.search = search
    const response = await apiClient.get('/flows', { params })
    return response.data
  },

  getFlow: async (id: string): Promise<Flow> => {
    const response = await apiClient.get(`/flows/${id}`)
    return response.data
  },

  createFlow: async (data: CreateFlowRequest): Promise<Flow> => {
    const response = await apiClient.post('/flows', data)
    return response.data
  },

  updateFlow: async (id: string, data: UpdateFlowRequest): Promise<Flow> => {
    const response = await apiClient.put(`/flows/${id}`, data)
    return response.data
  },

  deleteFlow: async (id: string): Promise<void> => {
    await apiClient.delete(`/flows/${id}`)
  },

  listVersions: async (flowId: string): Promise<FlowVersion[]> => {
    const response = await apiClient.get(`/flows/${flowId}/versions`)
    return response.data
  },

  getVersion: async (flowId: string, version: string): Promise<FlowVersion> => {
    const response = await apiClient.get(`/flows/${flowId}/versions/${version}`)
    return response.data
  },

  getPublishedVersion: async (flowId: string): Promise<FlowVersion> => {
    const response = await apiClient.get(`/flows/${flowId}/versions/published`)
    return response.data
  },

  saveVersion: async (flowId: string, data: SaveVersionRequest): Promise<FlowVersion> => {
    const response = await apiClient.post(`/flows/${flowId}/versions`, data)
    return response.data
  },

  publishVersion: async (flowId: string, version: string): Promise<FlowVersion> => {
    const response = await apiClient.post(`/flows/${flowId}/versions/${version}/publish`)
    return response.data
  },
}
