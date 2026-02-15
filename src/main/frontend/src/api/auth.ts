import apiClient from './client'

export interface UserProfile {
  id: string
  email: string
  name: string
  roles: string[]
}

export const authApi = {
  updateProfile: async (name: string): Promise<UserProfile> => {
    const response = await apiClient.put<UserProfile>('/auth/profile', { name })
    return response.data
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiClient.post('/auth/change-password', { currentPassword, newPassword })
  },

  forgotPassword: async (email: string): Promise<void> => {
    await apiClient.post('/auth/forgot-password', { email })
  },

  resetPassword: async (token: string, newPassword: string): Promise<void> => {
    await apiClient.post('/auth/reset-password', { token, newPassword })
  },
}

export default authApi
