import apiClient from './client'

export interface Webhook {
  id: string
  flowId: string
  name: string
  path: string
  method: string
  isActive: boolean
  authType: string | null
  authConfig: Record<string, unknown> | null
  webhookUrl: string
  createdAt: string
  updatedAt: string
}

export interface CreateWebhookRequest {
  flowId: string
  name: string
  path: string
  method?: string
  authType?: string
  authConfig?: Record<string, unknown>
}

export const webhookApi = {
  list: async (): Promise<Webhook[]> => {
    const response = await apiClient.get<Webhook[]>('/api/webhooks')
    return response.data
  },

  listForFlow: async (flowId: string): Promise<Webhook[]> => {
    const response = await apiClient.get<Webhook[]>(`/api/webhooks/flow/${flowId}`)
    return response.data
  },

  get: async (id: string): Promise<Webhook> => {
    const response = await apiClient.get<Webhook>(`/api/webhooks/${id}`)
    return response.data
  },

  create: async (request: CreateWebhookRequest): Promise<Webhook> => {
    const response = await apiClient.post<Webhook>('/api/webhooks', request)
    return response.data
  },

  activate: async (id: string): Promise<Webhook> => {
    const response = await apiClient.post<Webhook>(`/api/webhooks/${id}/activate`)
    return response.data
  },

  deactivate: async (id: string): Promise<Webhook> => {
    const response = await apiClient.post<Webhook>(`/api/webhooks/${id}/deactivate`)
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/webhooks/${id}`)
  },
}

export default webhookApi
