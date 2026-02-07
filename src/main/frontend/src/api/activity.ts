import apiClient from './client'

export interface UserActivity {
  id: string
  userId: string | null
  activityType: string
  resourceType: string | null
  resourceId: string | null
  resourceName: string | null
  details: Record<string, unknown> | null
  ipAddress: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const activityApi = {
  listAll: async (page = 0, size = 20, type?: string): Promise<Page<UserActivity>> => {
    const params: Record<string, unknown> = { page, size, sort: 'createdAt,desc' }
    if (type) params.type = type
    const response = await apiClient.get<Page<UserActivity>>('/api/activities', { params })
    return response.data
  },

  listMy: async (page = 0, size = 20, type?: string): Promise<Page<UserActivity>> => {
    const params: Record<string, unknown> = { page, size, sort: 'createdAt,desc' }
    if (type) params.type = type
    const response = await apiClient.get<Page<UserActivity>>('/api/activities/my', { params })
    return response.data
  },

  listByResource: async (resourceType: string, resourceId: string, page = 0, size = 20): Promise<Page<UserActivity>> => {
    const response = await apiClient.get<Page<UserActivity>>(
      `/api/activities/resource/${resourceType}/${resourceId}`,
      { params: { page, size, sort: 'createdAt,desc' } }
    )
    return response.data
  },
}
