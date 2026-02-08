import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import apiClient from '../api/client'
import { extractApiError } from '../utils/errorMessages'
import i18n from '../i18n'

/**
 * Reset all Zustand stores on logout.
 * Lazy-imported to avoid circular dependencies.
 */
function resetAllStores() {
  // Use dynamic imports to avoid circular dependency issues
  // Each store's setState resets to initial values
  import('./credentialStore').then(m => m.useCredentialStore.setState({
    credentials: [], credentialTypes: [], loading: false, error: null,
    totalElements: 0, totalPages: 0, currentPage: 0,
  })).catch(() => {})
  import('./webhookStore').then(m => m.useWebhookStore.setState({
    webhooks: [], flowWebhooks: [], selectedWebhook: null, isLoading: false, error: null,
  })).catch(() => {})
  import('./serviceStore').then(m => m.useServiceStore.setState({
    services: [], currentService: null, isLoading: false, error: null,
    totalElements: 0, currentPage: 0, pageSize: 20,
  })).catch(() => {})
  import('./skillStore').then(m => m.useSkillStore.setState({
    skills: [], builtinSkills: [], categories: [], selectedSkill: null, isLoading: false, error: null,
  })).catch(() => {})
  import('./aiStore').then(m => m.useAiStore.setState({
    providerTypes: [], providerTypesLoading: false, configs: [], configsLoading: false,
    selectedConfigId: null, models: [], modelsLoading: false,
    testResult: null, testLoading: false, error: null,
  })).catch(() => {})
  import('./aiAssistantStore').then(m => m.useAIAssistantStore.setState({
    isPanelOpen: false, currentSession: null, sessions: [],
    isStreaming: false, streamingContent: '', streamingStage: '',
    pendingChanges: [], currentFlowId: null, currentFlowDefinition: null,
    flowHistory: [], historyIndex: -1, error: null,
  })).catch(() => {})
}

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
  // Setup 狀態
  setupRequired: boolean | null  // null = 未檢查, true = 需要設定, false = 已設定
  setupChecked: boolean
  // Recovery Key 相關狀態
  showRecoveryKeyModal: boolean
  recoveryKey: string[] | null
  checkSetupStatus: () => Promise<void>
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string) => Promise<boolean | undefined>
  logout: () => Promise<void>
  refreshAccessToken: () => Promise<void>
  clearError: () => void
  confirmRecoveryKeyBackup: () => void
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
      setupRequired: null,
      setupChecked: false,
      showRecoveryKeyModal: false,
      recoveryKey: null,

      checkSetupStatus: async () => {
        try {
          const response = await apiClient.get('/auth/setup-status')
          set({
            setupRequired: response.data.setupRequired,
            setupChecked: true
          })
        } catch {
          // If API fails, assume setup is not required
          set({ setupRequired: false, setupChecked: true })
        }
      },

      login: async (email: string, password: string) => {
        set({ isLoading: true, error: null })
        try {
          const response = await apiClient.post('/auth/login', { email, password })
          const { accessToken, refreshToken, user, recoveryKey, needsRecoveryKeyBackup } = response.data

          set({
            accessToken,
            refreshToken,
            user,
            isAuthenticated: true,
            isLoading: false,
            // 如果需要備份 Recovery Key，顯示 Modal
            showRecoveryKeyModal: needsRecoveryKeyBackup || false,
            recoveryKey: recoveryKey || null,
          })
        } catch (error: unknown) {
          const message = extractApiError(error)
          set({ error: message, isLoading: false })
          throw error
        }
      },

      register: async (email: string, password: string, name: string) => {
        set({ isLoading: true, error: null })
        try {
          const response = await apiClient.post('/auth/register', { email, password, name })
          const { accessToken, refreshToken, user, isFirstUser } = response.data

          // Auto-login after registration
          if (accessToken) {
            localStorage.setItem('accessToken', accessToken)
            if (refreshToken) {
              localStorage.setItem('refreshToken', refreshToken)
            }
            set({
              accessToken,
              refreshToken,
              user,
              isAuthenticated: true,
              isLoading: false,
              setupRequired: false,
            })
          } else {
            set({
              isLoading: false,
              setupRequired: false,
            })
          }
          return isFirstUser || false
        } catch (error: unknown) {
          const message = extractApiError(error)
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
        // Clear all other stores to prevent data leakage between sessions
        resetAllStores()
      },

      refreshAccessToken: async () => {
        const { refreshToken } = get()
        if (!refreshToken) {
          throw new Error(i18n.t('auth.noRefreshToken'))
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
          throw new Error(i18n.t('auth.tokenRefreshFailed'))
        }
      },

      clearError: () => set({ error: null }),

      confirmRecoveryKeyBackup: () => {
        set({
          showRecoveryKeyModal: false,
          recoveryKey: null,
        })
      },
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
