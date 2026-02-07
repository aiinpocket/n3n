import { create } from 'zustand'
import { credentialApi, Credential, CredentialType, CreateCredentialRequest, ConnectionTestResult, TestCredentialRequest } from '../api/credential'
import { extractApiError } from '../utils/errorMessages'

interface CredentialState {
  credentials: Credential[]
  credentialTypes: CredentialType[]
  loading: boolean
  error: string | null
  totalElements: number
  totalPages: number
  currentPage: number

  fetchCredentials: (page?: number) => Promise<void>
  fetchCredentialTypes: () => Promise<void>
  createCredential: (request: CreateCredentialRequest) => Promise<Credential>
  deleteCredential: (id: string) => Promise<void>
  testCredential: (id: string) => Promise<ConnectionTestResult>
  testUnsavedCredential: (request: TestCredentialRequest) => Promise<ConnectionTestResult>
  clearError: () => void
}

export const useCredentialStore = create<CredentialState>((set, get) => ({
  credentials: [],
  credentialTypes: [],
  loading: false,
  error: null,
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,

  fetchCredentials: async (page = 0) => {
    set({ loading: true, error: null })
    try {
      const response = await credentialApi.list(page, 20)
      set({
        credentials: response.content,
        totalElements: response.totalElements,
        totalPages: response.totalPages,
        currentPage: response.number,
        loading: false
      })
    } catch (error: unknown) {
      set({ error: extractApiError(error, 'Failed to fetch credentials'), loading: false })
    }
  },

  fetchCredentialTypes: async () => {
    try {
      const types = await credentialApi.listTypes()
      set({ credentialTypes: types })
    } catch (error: unknown) {
      set({ error: extractApiError(error, 'Failed to fetch credential types') })
    }
  },

  createCredential: async (request: CreateCredentialRequest) => {
    set({ loading: true, error: null })
    try {
      const credential = await credentialApi.create(request)
      const { credentials } = get()
      set({
        credentials: [credential, ...credentials],
        loading: false
      })
      return credential
    } catch (error: unknown) {
      set({ error: extractApiError(error, 'Failed to create credential'), loading: false })
      throw error
    }
  },

  deleteCredential: async (id: string) => {
    set({ loading: true, error: null })
    try {
      await credentialApi.delete(id)
      const { credentials } = get()
      set({
        credentials: credentials.filter(c => c.id !== id),
        loading: false
      })
    } catch (error: unknown) {
      set({ error: extractApiError(error, 'Failed to delete credential'), loading: false })
      throw error
    }
  },

  testCredential: async (id: string) => {
    try {
      return await credentialApi.test(id)
    } catch (error: unknown) {
      return {
        success: false,
        message: extractApiError(error, 'Failed to test credential'),
        latencyMs: 0,
        testedAt: new Date().toISOString()
      }
    }
  },

  testUnsavedCredential: async (request: TestCredentialRequest) => {
    try {
      return await credentialApi.testUnsaved(request)
    } catch (error: unknown) {
      return {
        success: false,
        message: extractApiError(error, 'Failed to test connection'),
        latencyMs: 0,
        testedAt: new Date().toISOString()
      }
    }
  },

  clearError: () => set({ error: null })
}))
