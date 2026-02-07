import apiClient from './client'
import type { FlowDefinition } from './flow'

/**
 * Flow Optimizer API - 使用 Llamafile + Phi-3-Mini 進行流程優化分析
 */

export interface OptimizationSuggestion {
  type: 'parallel' | 'merge' | 'remove' | 'reorder'
  title: string
  description: string
  affectedNodes: string[]
  priority: 1 | 2 | 3  // 1=高影響, 2=中等, 3=低
}

export interface FlowOptimizationResponse {
  success: boolean
  suggestions: OptimizationSuggestion[]
  error?: string
  analysisTimeMs?: number
}

export interface OptimizerStatus {
  available: boolean
  model: string
  url: string
  enabled: boolean
}

export interface OptimizerConfig {
  enabled: boolean
  url: string
  timeoutMs: number
  model: string
  temperature: number
  maxTokens: number
}

export const optimizerApi = {
  /**
   * 分析流程定義並取得優化建議
   */
  analyzeFlow: async (definition: FlowDefinition): Promise<FlowOptimizationResponse> => {
    const startTime = Date.now()
    const response = await apiClient.post('/flow-optimizer/analyze', { definition })
    return {
      ...response.data,
      analysisTimeMs: Date.now() - startTime,
    }
  },

  /**
   * 檢查優化器服務狀態
   */
  getStatus: async (): Promise<OptimizerStatus> => {
    const response = await apiClient.get('/flow-optimizer/status')
    return response.data
  },

  /**
   * 取得優化器配置
   */
  getConfig: async (): Promise<OptimizerConfig> => {
    const response = await apiClient.get('/flow-optimizer/config')
    return response.data
  },
}

/**
 * 取得建議類型的顏色
 */
export const getSuggestionTypeColor = (type: OptimizationSuggestion['type']): string => {
  const colors: Record<string, string> = {
    parallel: '#22C55E',   // 綠色 - 並行優化
    merge: '#6366F1',      // 紫色 - 合併優化
    remove: '#F59E0B',     // 橙色 - 移除冗餘
    reorder: '#3B82F6',    // 藍色 - 重排順序
  }
  return colors[type] || '#64748B'
}

/**
 * 取得建議類型的中文名稱
 */
export const getSuggestionTypeName = (type: OptimizationSuggestion['type']): string => {
  const names: Record<string, string> = {
    parallel: 'optimization.parallel',
    merge: 'optimization.merge',
    remove: 'optimization.remove',
    reorder: 'optimization.reorder',
  }
  return names[type] || type
}

/**
 * 取得優先級的標籤
 */
export const getPriorityLabel = (priority: 1 | 2 | 3): { text: string; color: string } => {
  switch (priority) {
    case 1:
      return { text: 'optimization.highImpact', color: '#EF4444' }
    case 2:
      return { text: 'optimization.medium', color: '#F59E0B' }
    case 3:
      return { text: 'optimization.low', color: '#22C55E' }
    default:
      return { text: '未知', color: '#64748B' }
  }
}
