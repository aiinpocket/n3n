import apiClient from './client'

export interface Credential {
  id: string
  name: string
  type: string
  description?: string
  ownerId: string
  workspaceId?: string
  visibility: string
  createdAt: string
  updatedAt: string
}

export interface CredentialType {
  id: string
  name: string
  displayName: string
  description?: string
  icon?: string
  fieldsSchema: Record<string, unknown>
  testEndpoint?: string
}

export interface CreateCredentialRequest {
  name: string
  type: string
  description?: string
  workspaceId?: string
  visibility?: string
  data: Record<string, unknown>
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// Credential API
export const credentialApi = {
  // List credentials accessible by user
  list: async (page = 0, size = 20): Promise<PageResponse<Credential>> => {
    const response = await apiClient.get('/credentials', {
      params: { page, size }
    })
    return response.data
  },

  // List my own credentials
  listMine: async (page = 0, size = 20): Promise<PageResponse<Credential>> => {
    const response = await apiClient.get('/credentials/mine', {
      params: { page, size }
    })
    return response.data
  },

  // Get credential by ID
  get: async (id: string): Promise<Credential> => {
    const response = await apiClient.get(`/credentials/${id}`)
    return response.data
  },

  // Create credential
  create: async (request: CreateCredentialRequest): Promise<Credential> => {
    const response = await apiClient.post('/credentials', request)
    return response.data
  },

  // Delete credential
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/credentials/${id}`)
  },

  // Test credential (if supported)
  test: async (id: string): Promise<{ success: boolean; message?: string }> => {
    const response = await apiClient.post(`/credentials/${id}/test`)
    return response.data
  },

  // List credential types
  listTypes: async (): Promise<CredentialType[]> => {
    const response = await apiClient.get('/credentials/types')
    return response.data
  }
}

export default credentialApi
