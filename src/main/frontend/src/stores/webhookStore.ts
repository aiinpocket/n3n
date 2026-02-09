import { create } from 'zustand'
import { webhookApi, type Webhook, type CreateWebhookRequest } from '../api/webhook'
import { logger } from '../utils/logger'
import { extractApiError } from '../utils/errorMessages'

interface WebhookState {
  webhooks: Webhook[]
  flowWebhooks: Webhook[]
  selectedWebhook: Webhook | null
  isLoading: boolean
  error: string | null

  // Actions
  fetchWebhooks: () => Promise<void>
  fetchWebhooksForFlow: (flowId: string) => Promise<void>
  getWebhook: (id: string) => Promise<Webhook>
  createWebhook: (request: CreateWebhookRequest) => Promise<Webhook>
  activateWebhook: (id: string) => Promise<void>
  deactivateWebhook: (id: string) => Promise<void>
  deleteWebhook: (id: string) => Promise<void>
  testWebhook: (id: string) => Promise<{ success: boolean; executionId?: string; error?: string }>
  setSelectedWebhook: (webhook: Webhook | null) => void
  clearError: () => void
}

export const useWebhookStore = create<WebhookState>((set, get) => ({
  webhooks: [],
  flowWebhooks: [],
  selectedWebhook: null,
  isLoading: false,
  error: null,

  fetchWebhooks: async () => {
    set({ isLoading: true, error: null })
    try {
      const webhooks = await webhookApi.list()
      set({ webhooks, isLoading: false })
    } catch (error) {
      logger.error('Failed to fetch webhooks:', error)
      set({ error: extractApiError(error), isLoading: false })
    }
  },

  fetchWebhooksForFlow: async (flowId: string) => {
    set({ isLoading: true, error: null })
    try {
      const flowWebhooks = await webhookApi.listForFlow(flowId)
      set({ flowWebhooks, isLoading: false })
    } catch (error) {
      logger.error('Failed to fetch webhooks for flow:', error)
      set({ error: extractApiError(error), isLoading: false })
    }
  },

  getWebhook: async (id: string) => {
    try {
      const webhook = await webhookApi.get(id)
      set({ selectedWebhook: webhook })
      return webhook
    } catch (error) {
      logger.error('Failed to get webhook:', error)
      set({ error: extractApiError(error) })
      throw error
    }
  },

  createWebhook: async (request: CreateWebhookRequest) => {
    try {
      const webhook = await webhookApi.create(request)
      const { webhooks, flowWebhooks } = get()
      set({
        webhooks: [...webhooks, webhook],
        flowWebhooks: flowWebhooks.length > 0 && request.flowId === flowWebhooks[0]?.flowId
          ? [...flowWebhooks, webhook]
          : request.flowId ? [webhook] : flowWebhooks
      })
      return webhook
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  activateWebhook: async (id: string) => {
    try {
      const webhook = await webhookApi.activate(id)
      const { webhooks, flowWebhooks } = get()
      set({
        webhooks: webhooks.map((w) => (w.id === id ? webhook : w)),
        flowWebhooks: flowWebhooks.map((w) => (w.id === id ? webhook : w)),
        selectedWebhook: get().selectedWebhook?.id === id ? webhook : get().selectedWebhook
      })
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  deactivateWebhook: async (id: string) => {
    try {
      const webhook = await webhookApi.deactivate(id)
      const { webhooks, flowWebhooks } = get()
      set({
        webhooks: webhooks.map((w) => (w.id === id ? webhook : w)),
        flowWebhooks: flowWebhooks.map((w) => (w.id === id ? webhook : w)),
        selectedWebhook: get().selectedWebhook?.id === id ? webhook : get().selectedWebhook
      })
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  deleteWebhook: async (id: string) => {
    try {
      await webhookApi.delete(id)
      const { webhooks, flowWebhooks } = get()
      set({
        webhooks: webhooks.filter((w) => w.id !== id),
        flowWebhooks: flowWebhooks.filter((w) => w.id !== id),
        selectedWebhook: get().selectedWebhook?.id === id ? null : get().selectedWebhook
      })
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  testWebhook: async (id: string) => {
    try {
      const result = await webhookApi.test(id)
      return result
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  setSelectedWebhook: (webhook: Webhook | null) => {
    set({ selectedWebhook: webhook })
  },

  clearError: () => {
    set({ error: null })
  }
}))

export default useWebhookStore
