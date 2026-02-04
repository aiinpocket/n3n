import { fetchEventSource } from '@microsoft/fetch-event-source'
import client from './client'

// Types
export interface ChatStreamRequest {
  message: string
  conversationId?: string
  flowId?: string
  flowDefinition?: {
    nodes: unknown[]
    edges: unknown[]
  }
}

export interface ChatStreamChunk {
  type: 'thinking' | 'text' | 'structured' | 'progress' | 'error' | 'done'
  text?: string
  structuredData?: Record<string, unknown>
  progress?: number
  stage?: string
  timestamp?: string
}

export interface StreamCallbacks {
  onThinking?: (message: string) => void
  onText?: (text: string) => void
  onStructured?: (data: Record<string, unknown>) => void
  onProgress?: (percent: number, stage: string) => void
  onError?: (error: string) => void
  onDone?: () => void
}

// Get auth token from localStorage
const getAuthToken = (): string | null => {
  const authData = localStorage.getItem('n3n-auth')
  if (authData) {
    try {
      const parsed = JSON.parse(authData)
      return parsed.state?.token || null
    } catch {
      return null
    }
  }
  return null
}

/**
 * Send chat message with SSE streaming response
 */
export async function chatStream(
  request: ChatStreamRequest,
  callbacks: StreamCallbacks,
  abortController?: AbortController
): Promise<void> {
  const token = getAuthToken()

  await fetchEventSource('/api/ai-assistant/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(request),
    signal: abortController?.signal,

    onopen: async (response) => {
      if (!response.ok) {
        const error = await response.text()
        throw new Error(error || `HTTP ${response.status}`)
      }
    },

    onmessage: (event) => {
      if (!event.data) return

      try {
        const chunk: ChatStreamChunk = JSON.parse(event.data)

        switch (chunk.type) {
          case 'thinking':
            callbacks.onThinking?.(chunk.text || '')
            break
          case 'text':
            callbacks.onText?.(chunk.text || '')
            break
          case 'structured':
            callbacks.onStructured?.(chunk.structuredData || {})
            break
          case 'progress':
            callbacks.onProgress?.(chunk.progress || 0, chunk.stage || '')
            break
          case 'error':
            callbacks.onError?.(chunk.text || 'Unknown error')
            break
          case 'done':
            callbacks.onDone?.()
            break
        }
      } catch (e) {
        console.warn('Failed to parse SSE chunk:', e)
      }
    },

    onerror: (error) => {
      console.error('SSE error:', error)
      callbacks.onError?.(error.message || 'Connection error')
      throw error
    },
  })
}

/**
 * Send chat message without streaming (regular POST)
 */
export async function chat(request: ChatStreamRequest) {
  const response = await client.post('/api/ai-assistant/chat', request)
  return response.data
}

/**
 * Get conversation history
 */
export async function getConversationHistory(conversationId: string) {
  const response = await client.get(`/api/ai-assistant/conversations/${conversationId}`)
  return response.data
}

/**
 * List user's conversations
 */
export async function listConversations(flowId?: string) {
  const params = flowId ? { flowId } : {}
  const response = await client.get('/api/ai-assistant/conversations', { params })
  return response.data
}

/**
 * Delete a conversation
 */
export async function deleteConversation(conversationId: string) {
  const response = await client.delete(`/api/ai-assistant/conversations/${conversationId}`)
  return response.data
}

// Plugin install types
export interface PluginInstallTaskStatus {
  taskId: string
  nodeType: string
  status: 'PENDING' | 'PULLING' | 'STARTING' | 'CONFIGURING' | 'REGISTERING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  stage: string
  error?: string
  containerId?: string
  containerPort?: number
}

/**
 * Install missing node types
 */
export async function installMissingNodes(nodeTypes: string[]) {
  const response = await client.post('/api/plugins/install/missing', { nodeTypes })
  return response.data
}

/**
 * Get install task status
 */
export async function getInstallTaskStatus(taskId: string): Promise<PluginInstallTaskStatus> {
  const response = await client.get(`/api/plugins/install/tasks/${taskId}`)
  return response.data
}

/**
 * Get active install tasks
 */
export async function getActiveInstallTasks(): Promise<PluginInstallTaskStatus[]> {
  const response = await client.get('/api/plugins/install/tasks')
  return response.data
}

/**
 * Cancel install task
 */
export async function cancelInstallTask(taskId: string) {
  const response = await client.delete(`/api/plugins/install/tasks/${taskId}`)
  return response.data
}
