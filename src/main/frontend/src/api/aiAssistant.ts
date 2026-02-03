import client from './client'

// Types
export interface FlowSummary {
  nodeCount: number
  edgeCount: number
  version: string
  nodeTypes: string[]
  hasUnconnectedNodes: boolean
  hasCycles: boolean
}

export interface OptimizationSuggestion {
  id: string
  type: 'parallel' | 'merge' | 'remove' | 'reorder'
  title: string
  description: string
  benefit: string
  priority: number // 1=high, 2=medium, 3=low
  affectedNodes: string[]
}

export interface PublishAnalysisResponse {
  success: boolean
  summary?: FlowSummary
  suggestions: OptimizationSuggestion[]
  analysisTimeMs?: number
  error?: string
}

export interface AnalyzeForPublishRequest {
  definition: {
    nodes: unknown[]
    edges: unknown[]
  }
  flowId: string
  version: string
}

export interface ApplySuggestionsRequest {
  flowId: string
  version: string
  suggestionIds: string[]
}

export interface ApplySuggestionsResponse {
  success: boolean
  appliedCount: number
  appliedSuggestions: string[]
  updatedDefinition?: {
    nodes: unknown[]
    edges: unknown[]
  }
  error?: string
}

// Node Category Types
export interface NodeCategoryInfo {
  id: string
  displayName: string
  icon: string
  installedCount: number
  availableCount: number
}

export interface InstalledNodeInfo {
  nodeType: string
  displayName: string
  description: string
  category: string
  icon: string
  source: 'builtin' | 'plugin'
  pluginId?: string
}

export interface NodeRecommendation {
  nodeType: string
  displayName: string
  description: string
  category?: string
  matchReason: string
  pros: string[]
  cons: string[]
  source: 'builtin' | 'marketplace' | 'dockerhub'
  rating?: number
  downloads?: number
  needsInstall: boolean
}

export interface NodeRecommendationRequest {
  currentFlow?: {
    nodes: unknown[]
    edges: unknown[]
  }
  searchQuery?: string
  category?: string
}

export interface NodeRecommendationResponse {
  success: boolean
  aiAvailable: boolean
  categories?: NodeCategoryInfo[]
  installedNodes?: InstalledNodeInfo[]
  aiRecommendations?: NodeRecommendation[]
  marketplaceResults?: NodeRecommendation[]
  error?: string
}

// Flow Generation Types
export interface GenerateFlowRequest {
  userInput: string
  language?: 'zh-TW' | 'en'
}

export interface GenerateFlowResponse {
  success: boolean
  aiAvailable: boolean
  understanding?: string
  flowDefinition?: {
    nodes: Array<{
      id: string
      type: string
      label: string
      config?: Record<string, unknown>
    }>
    edges: Array<{
      source: string
      target: string
    }>
  }
  requiredNodes?: string[]
  missingNodes?: string[]
  error?: string
}

// API functions
export const aiAssistantApi = {
  /**
   * Analyze flow before publishing
   */
  analyzeForPublish: async (
    request: AnalyzeForPublishRequest
  ): Promise<PublishAnalysisResponse> => {
    const response = await client.post<PublishAnalysisResponse>(
      '/api/ai-assistant/analyze-for-publish',
      request
    )
    return response.data
  },

  /**
   * Apply selected optimization suggestions
   */
  applySuggestions: async (
    request: ApplySuggestionsRequest
  ): Promise<ApplySuggestionsResponse> => {
    const response = await client.post<ApplySuggestionsResponse>(
      '/api/ai-assistant/apply-suggestions',
      request
    )
    return response.data
  },

  /**
   * Get all node categories
   */
  getNodeCategories: async (): Promise<NodeCategoryInfo[]> => {
    const response = await client.get<NodeCategoryInfo[]>(
      '/api/ai-assistant/node-categories'
    )
    return response.data
  },

  /**
   * Get installed nodes, optionally filtered by category
   */
  getInstalledNodes: async (category?: string): Promise<InstalledNodeInfo[]> => {
    const params = category ? { category } : {}
    const response = await client.get<InstalledNodeInfo[]>(
      '/api/ai-assistant/installed-nodes',
      { params }
    )
    return response.data
  },

  /**
   * Get AI-powered node recommendations
   */
  recommendNodes: async (
    request: NodeRecommendationRequest
  ): Promise<NodeRecommendationResponse> => {
    const response = await client.post<NodeRecommendationResponse>(
      '/api/ai-assistant/recommend-nodes',
      request
    )
    return response.data
  },

  /**
   * Generate a flow from natural language description
   */
  generateFlow: async (
    request: GenerateFlowRequest
  ): Promise<GenerateFlowResponse> => {
    const response = await client.post<GenerateFlowResponse>(
      '/api/ai-assistant/generate-flow',
      request
    )
    return response.data
  },
}

// Helper functions
export function getSuggestionTypeColor(type: OptimizationSuggestion['type']): string {
  const colors: Record<string, string> = {
    parallel: '#52c41a',  // green
    merge: '#1890ff',     // blue
    remove: '#ff4d4f',    // red
    reorder: '#faad14',   // orange
  }
  return colors[type] || '#666'
}

export function getSuggestionTypeName(type: OptimizationSuggestion['type']): string {
  const names: Record<string, string> = {
    parallel: '並行執行',
    merge: '合併請求',
    remove: '移除冗餘',
    reorder: '重新排序',
  }
  return names[type] || type
}

export function getSuggestionTypeIcon(type: OptimizationSuggestion['type']): string {
  const icons: Record<string, string> = {
    parallel: 'branches',
    merge: 'merge-cells',
    remove: 'delete',
    reorder: 'ordered-list',
  }
  return icons[type] || 'info-circle'
}

export function getPriorityLabel(priority: number): { text: string; color: string } {
  switch (priority) {
    case 1:
      return { text: '高影響', color: 'red' }
    case 2:
      return { text: '中等', color: 'orange' }
    case 3:
    default:
      return { text: '低', color: 'default' }
  }
}

export function getCategoryIcon(icon: string): string {
  const iconMap: Record<string, string> = {
    thunderbolt: '🎯',
    robot: '🤖',
    database: '📊',
    message: '💬',
    table: '🗄️',
    cloud: '☁️',
    api: '🔗',
    tool: '🔧',
    appstore: '📦',
  }
  return iconMap[icon] || '📦'
}

export default aiAssistantApi
