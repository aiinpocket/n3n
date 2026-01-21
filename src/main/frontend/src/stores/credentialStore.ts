import { create } from 'zustand'
import { credentialApi, Credential, CredentialType, CreateCredentialRequest } from '../api/credential'

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
  testCredential: (id: string) => Promise<{ success: boolean; message?: string }>
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
      const message = error instanceof Error ? error.message : 'Failed to fetch credentials'
      set({ error: message, loading: false })
    }
  },

  fetchCredentialTypes: async () => {
    try {
      const types = await credentialApi.listTypes()
      set({ credentialTypes: types })
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to fetch credential types'
      set({ error: message })
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
      const message = error instanceof Error ? error.message : 'Failed to create credential'
      set({ error: message, loading: false })
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
      const message = error instanceof Error ? error.message : 'Failed to delete credential'
      set({ error: message, loading: false })
      throw error
    }
  },

  testCredential: async (id: string) => {
    try {
      return await credentialApi.test(id)
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to test credential'
      return { success: false, message }
    }
  },

  clearError: () => set({ error: null })
}))
