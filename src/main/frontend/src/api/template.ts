import apiClient from './client'

export interface Template {
  id: string
  name: string
  description: string | null
  category: string | null
  tags: string[]
  definition: Record<string, unknown>
  thumbnailUrl: string | null
  isOfficial: boolean
  usageCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** 隨程式碼發布的內建範本（後端已濾掉本站台缺節點的） */
export interface OfficialTemplate {
  id: string
  name: string
  description: string
  category: string
  tags: string[]
  complexity: string
  estimatedNodes: number
  useCases: string[]
  definition: Record<string, unknown>
}

export interface OfficialTemplateCategory {
  id: string
  name: string
  description: string
  icon: string
}

export interface CreateTemplateRequest {
  name: string
  description?: string
  category?: string
  tags?: string[]
  definition?: Record<string, unknown>
  thumbnailUrl?: string
}

export interface TemplatePage {
  content: Template[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface FlowFromTemplate {
  id: string
  name: string
  description: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
  latestVersion: string | null
  publishedVersion: string | null
}

export const templateApi = {
  list: async (page = 0, size = 20, category?: string, search?: string): Promise<TemplatePage> => {
    const params: Record<string, unknown> = { page, size }
    if (category) params.category = category
    if (search) params.search = search
    const response = await apiClient.get('/templates', { params })
    return response.data
  },

  getCategories: async (): Promise<string[]> => {
    const response = await apiClient.get('/templates/categories')
    return response.data
  },

  listOfficial: async (category?: string, search?: string): Promise<OfficialTemplate[]> => {
    const params: Record<string, unknown> = {}
    if (category) params.category = category
    if (search) params.search = search
    const response = await apiClient.get('/templates/official', { params })
    return response.data
  },

  getOfficialCategories: async (): Promise<OfficialTemplateCategory[]> => {
    const response = await apiClient.get('/templates/official/categories')
    return response.data
  },

  useOfficialTemplate: async (templateId: string, flowName: string): Promise<FlowFromTemplate> => {
    const response = await apiClient.post(`/templates/official/${templateId}/use`, null, { params: { flowName } })
    return response.data
  },

  getMine: async (): Promise<Template[]> => {
    const response = await apiClient.get('/templates/mine')
    return response.data
  },

  get: async (id: string): Promise<Template> => {
    const response = await apiClient.get(`/templates/${id}`)
    return response.data
  },

  create: async (request: CreateTemplateRequest): Promise<Template> => {
    const response = await apiClient.post('/templates', request)
    return response.data
  },

  update: async (id: string, data: { name?: string; description?: string; category?: string; tags?: string[] }): Promise<Template> => {
    const response = await apiClient.put<Template>(`/templates/${id}`, data)
    return response.data
  },

  createFromFlow: async (flowId: string, version: string, request: CreateTemplateRequest): Promise<Template> => {
    const response = await apiClient.post(`/templates/from-flow/${flowId}/version/${version}`, request)
    return response.data
  },

  useTemplate: async (id: string, flowName: string): Promise<FlowFromTemplate> => {
    const response = await apiClient.post(`/templates/${id}/use`, null, { params: { flowName } })
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/templates/${id}`)
  },
}

export default templateApi
