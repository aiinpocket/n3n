import apiClient from './client'

export type MemoryCategory = 'preference' | 'fact' | 'project' | 'style' | 'general'
export type MemorySource = 'assistant' | 'user'

export interface UserMemory {
  id: string
  content: string
  category: MemoryCategory
  source: MemorySource
  createdAt: string
  updatedAt: string
}

export interface UserMemoryPayload {
  content: string
  category?: MemoryCategory
}

export const aiMemoryApi = {
  list: async (): Promise<UserMemory[]> => {
    const response = await apiClient.get<UserMemory[]>('/ai/memory')
    return response.data
  },

  add: async (payload: UserMemoryPayload): Promise<UserMemory> => {
    const response = await apiClient.post<UserMemory>('/ai/memory', payload)
    return response.data
  },

  update: async (id: string, payload: UserMemoryPayload): Promise<UserMemory> => {
    const response = await apiClient.put<UserMemory>(`/ai/memory/${id}`, payload)
    return response.data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/ai/memory/${id}`)
  },

  removeAll: async (): Promise<void> => {
    await apiClient.delete('/ai/memory')
  },
}
