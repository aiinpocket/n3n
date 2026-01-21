import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import apiClient from '../api/client'

interface User {
  id: string
  email: string
  name: string
  avatarUrl?: string
  roles: string[]
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string) => Promise<void>
  logout: () => Promise<void>
  refreshAccessToken: () => Promise<void>
  clearError: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (email: string, password: string) => {
        set({ isLoading: true, error: null })
        try {
          const response = await apiClient.post('/auth/login', { email, password })
          const { accessToken, refreshToken, user } = response.data
          set({
            accessToken,
            refreshToken,
            user,
            isAuthenticated: true,
            isLoading: false,
          })
        } catch (error: unknown) {
          const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Login failed'
          set({ error: message, isLoading: false })
          throw error
        }
      },

      register: async (email: string, password: string, name: string) => {
        set({ isLoading: true, error: null })
        try {
          await apiClient.post('/auth/register', { email, password, name })
          set({ isLoading: false })
        } catch (error: unknown) {
          const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Registration failed'
          set({ error: message, isLoading: false })
          throw error
        }
      },

      logout: async () => {
        const { refreshToken } = get()
        try {
          await apiClient.post('/auth/logout', { refreshToken })
        } catch {
          // Ignore logout errors
        }
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false,
        })
      },

      refreshAccessToken: async () => {
        const { refreshToken } = get()
        if (!refreshToken) {
          throw new Error('No refresh token')
        }
        try {
          const response = await apiClient.post('/auth/refresh', { refreshToken })
          const { accessToken, refreshToken: newRefreshToken } = response.data
          set({ accessToken, refreshToken: newRefreshToken })
        } catch {
          set({
            accessToken: null,
            refreshToken: null,
            user: null,
            isAuthenticated: false,
          })
          throw new Error('Token refresh failed')
        }
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'n3n-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
