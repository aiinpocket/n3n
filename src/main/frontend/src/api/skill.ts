import apiClient from './client'

export interface Skill {
  id: string
  name: string
  displayName: string
  description?: string
  category: string
  icon?: string
  isBuiltin: boolean
  isEnabled: boolean
  implementationType: string
  inputSchema: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  visibility: string
  createdAt: string
  updatedAt: string
}

export interface CreateSkillRequest {
  name: string
  displayName: string
  description?: string
  category: string
  icon?: string
  implementationType: string
  implementationConfig?: Record<string, unknown>
  inputSchema: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  visibility?: string
}

export interface UpdateSkillRequest {
  displayName?: string
  description?: string
  category?: string
  icon?: string
  isEnabled?: boolean
  implementationConfig?: Record<string, unknown>
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  visibility?: string
}

export interface ExecuteSkillRequest {
  input: Record<string, unknown>
}

export interface ExecuteSkillResponse {
  success: boolean
  data?: Record<string, unknown>
  error?: string
  errorCode?: string
}

// Skill API
export const skillApi = {
  // List all accessible skills
  list: async (): Promise<Skill[]> => {
    const response = await apiClient.get('/skills')
    return response.data
  },

  // List built-in skills
  listBuiltin: async (): Promise<Skill[]> => {
    const response = await apiClient.get('/skills/builtin')
    return response.data
  },

  // List all categories
  listCategories: async (): Promise<string[]> => {
    const response = await apiClient.get('/skills/categories')
    return response.data
  },

  // List skills by category
  listByCategory: async (category: string): Promise<Skill[]> => {
    const response = await apiClient.get(`/skills/category/${category}`)
    return response.data
  },

  // Get skill by ID
  get: async (id: string): Promise<Skill> => {
    const response = await apiClient.get(`/skills/${id}`)
    return response.data
  },

  // Get skill by name
  getByName: async (name: string): Promise<Skill> => {
    const response = await apiClient.get(`/skills/name/${name}`)
    return response.data
  },

  // Create custom skill
  create: async (request: CreateSkillRequest): Promise<Skill> => {
    const response = await apiClient.post('/skills', request)
    return response.data
  },

  // Update skill
  update: async (id: string, request: UpdateSkillRequest): Promise<Skill> => {
    const response = await apiClient.put(`/skills/${id}`, request)
    return response.data
  },

  // Delete skill
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/skills/${id}`)
  },

  // Execute skill (for testing)
  execute: async (id: string, input: Record<string, unknown>): Promise<ExecuteSkillResponse> => {
    const response = await apiClient.post(`/skills/${id}/execute`, { input })
    return response.data
  },

  // Execute skill by name (for testing)
  executeByName: async (name: string, input: Record<string, unknown>): Promise<ExecuteSkillResponse> => {
    const response = await apiClient.post(`/skills/name/${name}/execute`, { input })
    return response.data
  }
}

export default skillApi
