import { fetchEventSource } from '@microsoft/fetch-event-source'
import client from './client'
import logger from '../utils/logger'
import { useAuthStore } from '../stores/authStore'

// Types
export interface ChatStreamRequest {
  message: string
  conversationId?: string
  flowId?: string
  flowDefinition?: {
    nodes: unknown[]
    edges: unknown[]
  }
  /** 帶入執行 ID 時，後端會附上該次執行的節點結果與錯誤供 AI 分析 */
  executionId?: string
}

export interface ChatStreamChunk {
  type: 'thinking' | 'text' | 'structured' | 'progress' | 'error' | 'done' | 'metadata'
  text?: string
  structuredData?: Record<string, unknown>
  progress?: number
  stage?: string
  conversationId?: string
  timestamp?: string
}

export interface StreamCallbacks {
  onThinking?: (message: string) => void
  onText?: (text: string) => void
  onStructured?: (data: Record<string, unknown>) => void
  onProgress?: (percent: number, stage: string) => void
  onMetadata?: (conversationId: string) => void
  onError?: (error: string) => void
  onDone?: () => void
}

// Get auth token from store
const getAuthToken = (): string | null => {
  return useAuthStore.getState().accessToken || null
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
    // Keep the SSE stream alive when the tab is hidden (avoids abort + re-POST)
    openWhenHidden: true,

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
          case 'metadata':
            if (chunk.conversationId) {
              callbacks.onMetadata?.(chunk.conversationId)
            }
            break
          case 'error':
            callbacks.onError?.(chunk.text || 'Unknown error')
            break
          case 'done':
            callbacks.onDone?.()
            break
        }
      } catch (e) {
        logger.warn('Failed to parse SSE chunk:', e)
      }
    },

    onerror: (error) => {
      logger.error('SSE error:', error)
      callbacks.onError?.(error.message || 'Connection error')
      // Re-throw to stop fetch-event-source from retrying forever
      throw error
    },
  })
}

/**
 * Send chat message without streaming (regular POST)
 */
export async function chat(request: ChatStreamRequest) {
  const response = await client.post('/ai-assistant/chat', request)
  return response.data
}

// ==================== Flow Generation Stream ====================

export interface RequirementContext {
  triggerType?: string
  triggerDescription?: string
  dataSource?: string
  processSteps?: string[]
  outputTarget?: string
  errorHandling?: string
  fullDescription?: string
}

export interface ExistingFlowDefinition {
  nodes: unknown[]
  edges: unknown[]
  understanding?: string
}

export interface GenerateFlowStreamRequest {
  userInput: string
  language?: string // 'zh-TW' | 'en'
  requirementContext?: RequirementContext
  existingFlow?: ExistingFlowDefinition
  feedback?: string
}

export interface FlowGenerationChunk {
  type:
    | 'thinking'
    | 'progress'
    | 'understanding'
    | 'node_added'
    | 'edge_added'
    | 'missing_nodes'
    | 'done'
    | 'error'
  progress?: number
  stage?: string
  message?: string
  node?: NodeData
  edge?: EdgeData
  missingNodes?: MissingNodeInfo[]
  flowDefinition?: {
    nodes: unknown[]
    edges: unknown[]
  }
  requiredNodes?: string[]
  timestamp?: string
}

export interface NodeData {
  id: string
  type: string
  label: string
  config?: Record<string, unknown>
  position?: { x: number; y: number }
}

export interface EdgeData {
  id: string
  source: string
  target: string
  label?: string
}

export interface MissingNodeInfo {
  nodeType: string
  displayName: string
  description?: string
  pluginId?: string
  canAutoInstall: boolean
}

export interface FlowGenerationCallbacks {
  onThinking?: (message: string) => void
  onProgress?: (percent: number, stage: string, message?: string) => void
  onUnderstanding?: (understanding: string) => void
  onNodeAdded?: (node: NodeData) => void
  onEdgeAdded?: (edge: EdgeData) => void
  onMissingNodes?: (missing: MissingNodeInfo[]) => void
  onDone?: (flowDefinition: { nodes: unknown[]; edges: unknown[] }, requiredNodes: string[]) => void
  onError?: (error: string) => void
}

/**
 * Generate flow with SSE streaming for real-time preview
 */
export async function generateFlowStream(
  request: GenerateFlowStreamRequest,
  callbacks: FlowGenerationCallbacks,
  abortController?: AbortController
): Promise<void> {
  const token = getAuthToken()

  await fetchEventSource('/api/ai-assistant/generate-flow/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(request),
    signal: abortController?.signal,
    // Keep the SSE stream alive when the tab is hidden (avoids abort + re-POST)
    openWhenHidden: true,

    onopen: async (response) => {
      if (!response.ok) {
        const error = await response.text()
        throw new Error(error || `HTTP ${response.status}`)
      }
    },

    onmessage: (event) => {
      if (!event.data) return

      try {
        const chunk: FlowGenerationChunk = JSON.parse(event.data)

        switch (chunk.type) {
          case 'thinking':
            callbacks.onThinking?.(chunk.message || '')
            break
          case 'progress':
            callbacks.onProgress?.(chunk.progress || 0, chunk.stage || '', chunk.message)
            break
          case 'understanding':
            callbacks.onUnderstanding?.(chunk.message || '')
            break
          case 'node_added':
            if (chunk.node) {
              callbacks.onNodeAdded?.(chunk.node)
            }
            break
          case 'edge_added':
            if (chunk.edge) {
              callbacks.onEdgeAdded?.(chunk.edge)
            }
            break
          case 'missing_nodes':
            if (chunk.missingNodes) {
              callbacks.onMissingNodes?.(chunk.missingNodes)
            }
            break
          case 'done':
            if (chunk.flowDefinition) {
              callbacks.onDone?.(chunk.flowDefinition, chunk.requiredNodes || [])
            }
            break
          case 'error':
            callbacks.onError?.(chunk.message || 'Unknown error')
            break
        }
      } catch (e) {
        logger.warn('Failed to parse flow generation SSE chunk:', e)
      }
    },

    onerror: (error) => {
      logger.error('Flow generation SSE error:', error)
      callbacks.onError?.(error.message || 'Connection error')
      // Re-throw to stop fetch-event-source from retrying forever
      throw error
    },
  })
}

// ==================== Plugin Install ====================

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
  const response = await client.post('/plugins/install/missing', { nodeTypes })
  return response.data
}

/**
 * Get install task status
 */
export async function getInstallTaskStatus(taskId: string): Promise<PluginInstallTaskStatus> {
  const response = await client.get(`/plugins/install/tasks/${taskId}`)
  return response.data
}

/**
 * Get active install tasks
 */
export async function getActiveInstallTasks(): Promise<PluginInstallTaskStatus[]> {
  const response = await client.get('/plugins/install/tasks')
  return response.data
}

/**
 * Cancel install task
 */
export async function cancelInstallTask(taskId: string): Promise<void> {
  await client.delete(`/plugins/install/tasks/${taskId}`)
}
