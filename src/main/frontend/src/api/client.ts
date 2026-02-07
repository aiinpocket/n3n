import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/authStore'

const MAX_RETRIES = 3
const RETRY_BASE_DELAY = 1000 // 1 second

// Status codes that should trigger a retry
const RETRYABLE_STATUS_CODES = new Set([408, 429, 500, 502, 503, 504])

// Only retry idempotent methods automatically
const IDEMPOTENT_METHODS = new Set(['get', 'head', 'options', 'put', 'delete'])

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
  _retryCount?: number
}

function shouldRetry(error: AxiosError, config: RetryConfig): boolean {
  const retryCount = config._retryCount || 0
  if (retryCount >= MAX_RETRIES) return false

  // Network error (no response) - always retry
  if (!error.response) return true

  const status = error.response.status
  if (!RETRYABLE_STATUS_CODES.has(status)) return false

  // Only auto-retry idempotent methods for server errors
  const method = (config.method || 'get').toLowerCase()
  return IDEMPOTENT_METHODS.has(method)
}

function getRetryDelay(retryCount: number): number {
  // Exponential backoff: 1s, 2s, 4s + jitter
  const delay = RETRY_BASE_DELAY * Math.pow(2, retryCount)
  const jitter = Math.random() * 500
  return delay + jitter
}

// Mutex for token refresh: ensures only one refresh request at a time
let refreshPromise: Promise<void> | null = null

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
  async (error: AxiosError) => {
    const config = error.config as RetryConfig
    if (!config) return Promise.reject(error)

    const status = error.response?.status

    // Handle 401 (Unauthorized) - try to refresh token
    if (status === 401 && !config._retry) {
      config._retry = true

      try {
        // If a refresh is already in progress, wait for it instead of triggering another
        if (!refreshPromise) {
          refreshPromise = useAuthStore.getState().refreshAccessToken().finally(() => {
            refreshPromise = null
          })
        }
        await refreshPromise
        const { accessToken } = useAuthStore.getState()
        config.headers.Authorization = `Bearer ${accessToken}`
        return apiClient(config)
      } catch {
        useAuthStore.getState().logout()
        window.location.href = '/login?reason=session_expired'
      }
    }

    // Handle 403 (Forbidden)
    if (status === 403) {
      const { accessToken } = useAuthStore.getState()
      if (!accessToken) {
        // Not logged in at all - redirect to login
        window.location.href = '/login?reason=login_required'
        return Promise.reject(error)
      }
      // Has valid token but lacks permission - do NOT logout
      // This is a normal permission denied, not a session issue
      return Promise.reject(error)
    }

    // Retry logic for transient errors
    if (shouldRetry(error, config)) {
      config._retryCount = (config._retryCount || 0) + 1
      const delay = getRetryDelay(config._retryCount - 1)

      await new Promise((resolve) => setTimeout(resolve, delay))
      return apiClient(config)
    }

    return Promise.reject(error)
  }
)

export default apiClient
