import apiClient from './client'

export interface AdminUser {
  id: string
  email: string
  name: string
  roles: string[]
  status: string
  emailVerified: boolean
  createdAt: string
  lastLoginAt: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CreateUserRequest {
  email: string
  name: string
  password?: string
  roles: string[]
}

export const adminApi = {
  getUser: async (id: string): Promise<AdminUser> => {
    const response = await apiClient.get(`/admin/users/${id}`)
    return response.data
  },

  listUsers: async (page = 0, size = 20, search?: string): Promise<PageResponse<AdminUser>> => {
    const params: Record<string, unknown> = { page, size }
    if (search) params.search = search
    const response = await apiClient.get('/admin/users', { params })
    return response.data
  },

  createUser: async (request: CreateUserRequest): Promise<AdminUser> => {
    const response = await apiClient.post('/admin/users', request)
    return response.data
  },

  updateStatus: async (userId: string, status: string): Promise<void> => {
    await apiClient.patch(`/admin/users/${userId}/status`, null, { params: { status } })
  },

  updateRoles: async (userId: string, roles: string[]): Promise<void> => {
    await apiClient.put(`/admin/users/${userId}/roles`, { roles })
  },

  resetPassword: async (userId: string): Promise<void> => {
    await apiClient.post(`/admin/users/${userId}/reset-password`)
  },
}

export default adminApi
