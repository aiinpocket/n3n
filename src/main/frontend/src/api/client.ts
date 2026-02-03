import axios from 'axios'
import { useAuthStore } from '../stores/authStore'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const status = error.response?.status

    // Handle 401 (Unauthorized) - try to refresh token
    if (status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        await useAuthStore.getState().refreshAccessToken()
        const { accessToken } = useAuthStore.getState()
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return apiClient(originalRequest)
      } catch {
        useAuthStore.getState().logout()
        window.location.href = '/login?reason=session_expired'
      }
    }

    // Handle 403 (Forbidden) - user not logged in or session invalid
    if (status === 403) {
      const { accessToken } = useAuthStore.getState()
      if (!accessToken) {
        // Not logged in at all
        window.location.href = '/login?reason=login_required'
        return Promise.reject(new Error('請先登入'))
      }
      // Has token but still 403 - might be expired or invalid
      useAuthStore.getState().logout()
      window.location.href = '/login?reason=session_expired'
      return Promise.reject(new Error('登入已過期，請重新登入'))
    }

    return Promise.reject(error)
  }
)

export default apiClient
