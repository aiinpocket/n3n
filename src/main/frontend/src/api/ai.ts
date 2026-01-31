import apiClient from './client'

// ==================== Types ====================

export interface AiProviderType {
  id: string
  displayName: string
  requiresApiKey: boolean
  defaultBaseUrl: string | null
  description: string
}

export interface AiModel {
  id: string
  displayName: string
  contextWindow: number
  maxOutputTokens: number
  supportsVision: boolean
  supportsStreaming: boolean
}

export interface AiProviderConfig {
  id: string
  provider: string
  name: string
  hasCredential: boolean
  baseUrl: string | null
  defaultModel: string | null
  settings: Record<string, unknown>
  isDefault: boolean
  isActive: boolean
  createdAt: string
}

export interface CreateAiProviderRequest {
  provider: string
  name: string
  apiKey?: string
  baseUrl?: string
  defaultModel?: string
  settings?: Record<string, unknown>
}

export interface UpdateAiProviderRequest {
  name?: string
  apiKey?: string
  baseUrl?: string
  defaultModel?: string
  settings?: Record<string, unknown>
  isDefault?: boolean
  isActive?: boolean
}

export interface TestConnectionResponse {
  success: boolean
  message: string
  modelCount?: number
  latencyMs?: number
}

// ==================== API Functions ====================

/**
 * 取得可用的 AI Provider 類型
 */
export async function getProviderTypes(): Promise<AiProviderType[]> {
  const response = await apiClient.get('/ai/providers/types')
  return response.data
}

/**
 * 取得使用者的 AI 設定列表
 */
export async function getConfigs(): Promise<AiProviderConfig[]> {
  const response = await apiClient.get('/ai/providers/configs')
  return response.data
}

/**
 * 取得單一 AI 設定
 */
export async function getConfig(id: string): Promise<AiProviderConfig> {
  const response = await apiClient.get(`/ai/providers/configs/${id}`)
  return response.data
}

/**
 * 建立新的 AI 設定
 */
export async function createConfig(request: CreateAiProviderRequest): Promise<AiProviderConfig> {
  const response = await apiClient.post('/ai/providers/configs', request)
  return response.data
}

/**
 * 更新 AI 設定
 */
export async function updateConfig(id: string, request: UpdateAiProviderRequest): Promise<AiProviderConfig> {
  const response = await apiClient.put(`/ai/providers/configs/${id}`, request)
  return response.data
}

/**
 * 刪除 AI 設定
 */
export async function deleteConfig(id: string): Promise<void> {
  await apiClient.delete(`/ai/providers/configs/${id}`)
}

/**
 * 設為預設
 */
export async function setAsDefault(id: string): Promise<AiProviderConfig> {
  const response = await apiClient.post(`/ai/providers/configs/${id}/default`)
  return response.data
}

/**
 * 測試連線
 */
export async function testConnection(id: string): Promise<TestConnectionResponse> {
  const response = await apiClient.post(`/ai/providers/configs/${id}/test`)
  return response.data
}

/**
 * 取得可用模型列表
 */
export async function getModels(id: string): Promise<AiModel[]> {
  const response = await apiClient.get(`/ai/providers/configs/${id}/models`)
  return response.data
}

/**
 * 直接用 API Key 取得模型列表（建立新設定時使用）
 */
export async function fetchModelsWithKey(
  provider: string,
  apiKey: string,
  baseUrl?: string
): Promise<AiModel[]> {
  const response = await apiClient.post('/ai/providers/models', {
    provider,
    apiKey,
    baseUrl,
  })
  return response.data
}
