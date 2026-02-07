import { create } from 'zustand'
import { webhookApi, type Webhook, type CreateWebhookRequest } from '../api/webhook'
import { logger } from '../utils/logger'

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
      set({ error: (error as Error).message, isLoading: false })
    }
  },

  fetchWebhooksForFlow: async (flowId: string) => {
    set({ isLoading: true, error: null })
    try {
      const flowWebhooks = await webhookApi.listForFlow(flowId)
      set({ flowWebhooks, isLoading: false })
    } catch (error) {
      logger.error('Failed to fetch webhooks for flow:', error)
      set({ error: (error as Error).message, isLoading: false })
    }
  },

  getWebhook: async (id: string) => {
    try {
      const webhook = await webhookApi.get(id)
      set({ selectedWebhook: webhook })
      return webhook
    } catch (error) {
      logger.error('Failed to get webhook:', error)
      set({ error: (error as Error).message })
      throw error
    }
  },

  createWebhook: async (request: CreateWebhookRequest) => {
    const webhook = await webhookApi.create(request)
    const { webhooks, flowWebhooks } = get()
    set({
      webhooks: [...webhooks, webhook],
      flowWebhooks: request.flowId === flowWebhooks[0]?.flowId
        ? [...flowWebhooks, webhook]
        : flowWebhooks
    })
    return webhook
  },

  activateWebhook: async (id: string) => {
    const webhook = await webhookApi.activate(id)
    const { webhooks, flowWebhooks } = get()
    set({
      webhooks: webhooks.map((w) => (w.id === id ? webhook : w)),
      flowWebhooks: flowWebhooks.map((w) => (w.id === id ? webhook : w)),
      selectedWebhook: get().selectedWebhook?.id === id ? webhook : get().selectedWebhook
    })
  },

  deactivateWebhook: async (id: string) => {
    const webhook = await webhookApi.deactivate(id)
    const { webhooks, flowWebhooks } = get()
    set({
      webhooks: webhooks.map((w) => (w.id === id ? webhook : w)),
      flowWebhooks: flowWebhooks.map((w) => (w.id === id ? webhook : w)),
      selectedWebhook: get().selectedWebhook?.id === id ? webhook : get().selectedWebhook
    })
  },

  deleteWebhook: async (id: string) => {
    await webhookApi.delete(id)
    const { webhooks, flowWebhooks } = get()
    set({
      webhooks: webhooks.filter((w) => w.id !== id),
      flowWebhooks: flowWebhooks.filter((w) => w.id !== id),
      selectedWebhook: get().selectedWebhook?.id === id ? null : get().selectedWebhook
    })
  },

  setSelectedWebhook: (webhook: Webhook | null) => {
    set({ selectedWebhook: webhook })
  },

  clearError: () => {
    set({ error: null })
  }
}))

export default useWebhookStore
