import apiClient from './client'

export interface GenerateCodeRequest {
  description: string
  language?: string
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  sampleInput?: string
}

export interface GenerateCodeResponse {
  success: boolean
  aiAvailable: boolean
  code?: string
  explanation?: string
  language?: string
  error?: string
}

/**
 * AI 程式碼生成 API
 */
export const aiCodeApi = {
  /**
   * 生成程式碼
   */
  generate: async (request: GenerateCodeRequest): Promise<GenerateCodeResponse> => {
    const response = await apiClient.post<GenerateCodeResponse>('/ai/code/generate', {
      ...request,
      language: request.language || 'javascript',
    })
    return response.data
  },
}

export default aiCodeApi
