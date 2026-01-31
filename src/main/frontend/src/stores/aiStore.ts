import { create } from 'zustand'
import * as aiApi from '../api/ai'
import type {
  AiProviderType,
  AiProviderConfig,
  AiModel,
  CreateAiProviderRequest,
  UpdateAiProviderRequest,
} from '../api/ai'

interface AiState {
  // Provider 類型
  providerTypes: AiProviderType[]
  providerTypesLoading: boolean

  // 使用者設定
  configs: AiProviderConfig[]
  configsLoading: boolean
  selectedConfigId: string | null

  // 模型列表
  models: AiModel[]
  modelsLoading: boolean

  // 連線測試
  testResult: { success: boolean; message: string } | null
  testLoading: boolean

  // 錯誤
  error: string | null

  // Actions
  fetchProviderTypes: () => Promise<void>
  fetchConfigs: () => Promise<void>
  createConfig: (request: CreateAiProviderRequest) => Promise<AiProviderConfig>
  updateConfig: (id: string, request: UpdateAiProviderRequest) => Promise<AiProviderConfig>
  deleteConfig: (id: string) => Promise<void>
  setAsDefault: (id: string) => Promise<void>
  testConnection: (id: string) => Promise<void>
  fetchModels: (id: string) => Promise<void>
  fetchModelsWithKey: (provider: string, apiKey: string, baseUrl?: string) => Promise<void>
  selectConfig: (id: string | null) => void
  clearError: () => void
  clearTestResult: () => void
}

export const useAiStore = create<AiState>((set, get) => ({
  // Initial state
  providerTypes: [],
  providerTypesLoading: false,
  configs: [],
  configsLoading: false,
  selectedConfigId: null,
  models: [],
  modelsLoading: false,
  testResult: null,
  testLoading: false,
  error: null,

  // Fetch provider types
  fetchProviderTypes: async () => {
    set({ providerTypesLoading: true, error: null })
    try {
      const types = await aiApi.getProviderTypes()
      set({ providerTypes: types, providerTypesLoading: false })
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to fetch provider types'
      set({ error: message, providerTypesLoading: false })
    }
  },

  // Fetch user configs
  fetchConfigs: async () => {
    set({ configsLoading: true, error: null })
    try {
      const configs = await aiApi.getConfigs()
      set({ configs, configsLoading: false })

      // 自動選擇預設設定
      const defaultConfig = configs.find(c => c.isDefault)
      if (defaultConfig && !get().selectedConfigId) {
        set({ selectedConfigId: defaultConfig.id })
      }
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to fetch configs'
      set({ error: message, configsLoading: false })
    }
  },

  // Create config
  createConfig: async (request: CreateAiProviderRequest) => {
    set({ error: null })
    try {
      const config = await aiApi.createConfig(request)
      set((state) => ({
        configs: [...state.configs, config],
        selectedConfigId: config.id,
      }))
      return config
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to create config'
      set({ error: message })
      throw error
    }
  },

  // Update config
  updateConfig: async (id: string, request: UpdateAiProviderRequest) => {
    set({ error: null })
    try {
      const updated = await aiApi.updateConfig(id, request)
      set((state) => ({
        configs: state.configs.map(c => c.id === id ? updated : c),
      }))
      return updated
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to update config'
      set({ error: message })
      throw error
    }
  },

  // Delete config
  deleteConfig: async (id: string) => {
    set({ error: null })
    try {
      await aiApi.deleteConfig(id)
      set((state) => ({
        configs: state.configs.filter(c => c.id !== id),
        selectedConfigId: state.selectedConfigId === id ? null : state.selectedConfigId,
      }))
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to delete config'
      set({ error: message })
      throw error
    }
  },

  // Set as default
  setAsDefault: async (id: string) => {
    set({ error: null })
    try {
      await aiApi.setAsDefault(id)
      set((state) => ({
        configs: state.configs.map(c => ({
          ...c,
          isDefault: c.id === id,
        })),
      }))
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to set as default'
      set({ error: message })
      throw error
    }
  },

  // Test connection
  testConnection: async (id: string) => {
    set({ testLoading: true, testResult: null, error: null })
    try {
      const result = await aiApi.testConnection(id)
      set({ testResult: result, testLoading: false })
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Connection test failed'
      set({
        testResult: { success: false, message },
        testLoading: false,
      })
    }
  },

  // Fetch models for a config
  fetchModels: async (id: string) => {
    set({ modelsLoading: true, error: null })
    try {
      const models = await aiApi.getModels(id)
      set({ models, modelsLoading: false })
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to fetch models'
      set({ error: message, modelsLoading: false })
    }
  },

  // Fetch models with API key (for new config)
  fetchModelsWithKey: async (provider: string, apiKey: string, baseUrl?: string) => {
    set({ modelsLoading: true, error: null })
    try {
      const models = await aiApi.fetchModelsWithKey(provider, apiKey, baseUrl)
      set({ models, modelsLoading: false })
    } catch (error: unknown) {
      const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to fetch models'
      set({ error: message, modelsLoading: false, models: [] })
    }
  },

  // Select config
  selectConfig: (id: string | null) => {
    set({ selectedConfigId: id, models: [], testResult: null })
  },

  // Clear error
  clearError: () => set({ error: null }),

  // Clear test result
  clearTestResult: () => set({ testResult: null }),
}))
