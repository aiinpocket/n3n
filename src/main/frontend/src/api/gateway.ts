import apiClient from './client'

export interface GatewayNode {
  connectionId: string
  displayName: string
  platform: string
  version: string
  capabilities: string[]
  status: string
  connectedAt: string
  lastActiveAt: string
  latencyMs: number
}

export interface InvokeRequest {
  capability: string
  args: Record<string, unknown>
}

export interface InvokeResponse {
  success: boolean
  data: Record<string, unknown> | null
  error: string | null
}

export interface GatewayStats {
  connectedNodes: number
  totalInvocations: number
  uptime: string
}

export interface PairingCodeResponse {
  code: string
  expiresInSeconds: number
}

export const gatewayApi = {
  listNodes: async (): Promise<GatewayNode[]> => {
    const response = await apiClient.get<GatewayNode[]>('/gateway/nodes')
    return response.data
  },

  getNode: async (connectionId: string): Promise<GatewayNode> => {
    const response = await apiClient.get<GatewayNode>(`/gateway/nodes/${connectionId}`)
    return response.data
  },

  invokeNode: async (connectionId: string, request: InvokeRequest): Promise<InvokeResponse> => {
    const response = await apiClient.post<InvokeResponse>(`/gateway/nodes/${connectionId}/invoke`, request)
    return response.data
  },

  getCapabilities: async (): Promise<Record<string, unknown>> => {
    const response = await apiClient.get<Record<string, unknown>>('/gateway/capabilities')
    return response.data
  },

  invokeAny: async (request: InvokeRequest): Promise<InvokeResponse> => {
    const response = await apiClient.post<InvokeResponse>('/gateway/invoke', request)
    return response.data
  },

  getStats: async (): Promise<GatewayStats> => {
    const response = await apiClient.get<GatewayStats>('/gateway/stats')
    return response.data
  },

  generatePairingCode: async (): Promise<PairingCodeResponse> => {
    const response = await apiClient.post<PairingCodeResponse>('/gateway/pairing-code')
    return response.data
  },
}
