import apiClient from './client'

// ==================== Types ====================

export interface Conversation {
  id: string
  title: string
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'ARCHIVED'
  draftFlowId: string | null
  createdAt: string
  updatedAt: string
}

export interface ConversationDetail extends Conversation {
  messages: Message[]
  totalTokens: number
}

export interface Message {
  id: string
  role: 'SYSTEM' | 'USER' | 'ASSISTANT'
  content: string
  structuredData: StructuredData | null
  tokenCount: number | null
  modelId: string | null
  latencyMs: number | null
  createdAt: string
}

export interface StructuredData {
  understanding?: string
  existingComponents?: ComponentRecommendation[]
  suggestedNewComponents?: NewComponentSuggestion[]
  flowDefinition?: FlowDefinition
}

export interface ComponentRecommendation {
  name: string
  purpose: string
}

export interface NewComponentSuggestion {
  name: string
  description: string
  interfaceDef?: Record<string, unknown>
}

export interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
}

export interface FlowNode {
  id: string
  type: string
  data: Record<string, unknown>
  position: { x: number; y: number }
}

export interface FlowEdge {
  id: string
  source: string
  target: string
}

export interface AgentResponse {
  messageId: string
  content: string
  structuredData: StructuredData | null
  model: string
  tokenCount: number
  latencyMs: number
  hasFlowDefinition: boolean
  hasComponentRecommendations: boolean
}

export interface CreateConversationRequest {
  title?: string
}

export interface SendMessageRequest {
  content: string
}

// ==================== API Functions ====================

/**
 * 建立新對話
 */
export async function createConversation(
  request: CreateConversationRequest = {}
): Promise<Conversation> {
  const response = await apiClient.post('/agent/conversations', request)
  return response.data
}

/**
 * 取得對話列表
 */
export async function getConversations(
  activeOnly = false
): Promise<Conversation[]> {
  const response = await apiClient.get('/agent/conversations', {
    params: { activeOnly },
  })
  return response.data.content || response.data
}

/**
 * 取得對話詳情
 */
export async function getConversation(id: string): Promise<ConversationDetail> {
  const response = await apiClient.get(`/agent/conversations/${id}`)
  return response.data
}

/**
 * 發送訊息
 */
export async function sendMessage(
  conversationId: string,
  content: string
): Promise<AgentResponse> {
  const response = await apiClient.post(
    `/agent/conversations/${conversationId}/messages`,
    { content }
  )
  return response.data
}

/**
 * 更新對話標題
 */
export async function updateConversationTitle(
  id: string,
  title: string
): Promise<Conversation> {
  const response = await apiClient.patch(`/agent/conversations/${id}`, {
    title,
  })
  return response.data
}

/**
 * 完成對話
 */
export async function completeConversation(
  id: string,
  flowId?: string
): Promise<Conversation> {
  const response = await apiClient.post(`/agent/conversations/${id}/complete`, {
    flowId,
  })
  return response.data
}

/**
 * 取消對話
 */
export async function cancelConversation(id: string): Promise<Conversation> {
  const response = await apiClient.post(`/agent/conversations/${id}/cancel`)
  return response.data
}

/**
 * 封存對話
 */
export async function archiveConversation(id: string): Promise<void> {
  await apiClient.delete(`/agent/conversations/${id}`)
}

/**
 * 建立 SSE 串流連線
 */
export function createStreamConnection(
  conversationId: string,
  message: string
): EventSource {
  const encodedMessage = encodeURIComponent(message)
  const url = `/api/agent/conversations/${conversationId}/stream?message=${encodedMessage}`
  return new EventSource(url)
}
