/**
 * AI Quick Actions Configuration
 * Defines common AI-assisted operations for quick access.
 */

export interface QuickAction {
  id: string
  label: string
  description: string
  icon: string
  prompt: string
  category: QuickActionCategory
  requiresFlow?: boolean
  tags?: string[]
}

export type QuickActionCategory = 'create' | 'optimize' | 'analyze' | 'debug' | 'template'

export const QUICK_ACTION_CATEGORIES: Record<QuickActionCategory, { label: string; color: string }> = {
  create: { label: '建立', color: '#52c41a' },
  optimize: { label: '優化', color: '#1890ff' },
  analyze: { label: '分析', color: '#8B5CF6' },
  debug: { label: '除錯', color: '#fa8c16' },
  template: { label: '範本', color: '#13c2c2' },
}

export const AI_QUICK_ACTIONS: QuickAction[] = [
  // Create actions
  {
    id: 'create-basic-flow',
    label: '建立基本流程',
    description: '從零開始建立一個新的自動化流程',
    icon: 'plus-circle',
    prompt: '請幫我建立一個新的自動化流程。',
    category: 'create',
    tags: ['新建', '流程'],
  },
  {
    id: 'create-schedule-task',
    label: '建立排程任務',
    description: '建立定時執行的自動化任務',
    icon: 'clock-circle',
    prompt: '請幫我建立一個每天定時執行的排程任務，',
    category: 'create',
    tags: ['排程', '定時', 'cron'],
  },
  {
    id: 'create-webhook-handler',
    label: '建立 Webhook 處理',
    description: '建立接收外部 Webhook 請求的流程',
    icon: 'api',
    prompt: '請幫我建立一個 Webhook 接收器，當收到請求時進行處理。',
    category: 'create',
    tags: ['webhook', 'api', '接收'],
  },
  {
    id: 'create-data-sync',
    label: '建立資料同步',
    description: '建立資料來源之間的同步流程',
    icon: 'sync',
    prompt: '請幫我建立一個資料同步流程，從來源讀取資料並寫入目標。',
    category: 'create',
    tags: ['資料', '同步', '匯入'],
  },
  {
    id: 'create-notification',
    label: '建立通知流程',
    description: '建立多管道通知發送流程',
    icon: 'bell',
    prompt: '請幫我建立一個通知流程，當特定事件發生時發送通知。',
    category: 'create',
    tags: ['通知', '訊息', 'slack', 'email'],
  },

  // Optimize actions
  {
    id: 'optimize-performance',
    label: '優化效能',
    description: '分析並優化流程執行效能',
    icon: 'thunderbolt',
    prompt: '請分析這個流程的效能瓶頸，並建議優化方式。',
    category: 'optimize',
    requiresFlow: true,
    tags: ['效能', '優化', '速度'],
  },
  {
    id: 'add-error-handling',
    label: '加入錯誤處理',
    description: '為流程加入完整的錯誤處理機制',
    icon: 'warning',
    prompt: '請為這個流程加入完整的錯誤處理，包括重試機制和告警通知。',
    category: 'optimize',
    requiresFlow: true,
    tags: ['錯誤', '處理', '重試'],
  },
  {
    id: 'add-logging',
    label: '加入日誌記錄',
    description: '為流程加入詳細的日誌記錄',
    icon: 'file-text',
    prompt: '請為這個流程在關鍵步驟加入日誌記錄，便於追蹤和除錯。',
    category: 'optimize',
    requiresFlow: true,
    tags: ['日誌', '記錄', 'log'],
  },
  {
    id: 'parallelize',
    label: '並行化處理',
    description: '將可並行的步驟改為同時執行',
    icon: 'branches',
    prompt: '請分析這個流程中可以並行執行的步驟，並建議如何優化。',
    category: 'optimize',
    requiresFlow: true,
    tags: ['並行', '同時', '效率'],
  },

  // Analyze actions
  {
    id: 'explain-flow',
    label: '解釋流程',
    description: '詳細解釋流程的每個步驟',
    icon: 'question-circle',
    prompt: '請詳細解釋這個流程的運作方式，包括每個節點的作用和資料流向。',
    category: 'analyze',
    requiresFlow: true,
    tags: ['解釋', '說明', '理解'],
  },
  {
    id: 'find-issues',
    label: '檢查潛在問題',
    description: '找出流程中可能的問題',
    icon: 'bug',
    prompt: '請檢查這個流程中可能存在的問題，包括潛在的錯誤、效能問題或設計問題。',
    category: 'analyze',
    requiresFlow: true,
    tags: ['問題', '檢查', '審查'],
  },
  {
    id: 'suggest-improvements',
    label: '建議改進',
    description: '提供流程改進建議',
    icon: 'bulb',
    prompt: '請分析這個流程並提供改進建議，讓它更有效率、更可靠。',
    category: 'analyze',
    requiresFlow: true,
    tags: ['建議', '改進', '優化'],
  },

  // Debug actions
  {
    id: 'debug-error',
    label: '除錯錯誤',
    description: '協助找出並修復流程錯誤',
    icon: 'tool',
    prompt: '這個流程執行時發生錯誤，請幫我分析可能的原因並提供修復建議。',
    category: 'debug',
    requiresFlow: true,
    tags: ['除錯', '錯誤', '修復'],
  },
  {
    id: 'trace-data',
    label: '追蹤資料流',
    description: '追蹤資料在流程中的傳遞',
    icon: 'node-index',
    prompt: '請幫我追蹤資料在這個流程中的傳遞過程，找出資料可能遺失或轉換的地方。',
    category: 'debug',
    requiresFlow: true,
    tags: ['追蹤', '資料', '流向'],
  },

  // Template actions
  {
    id: 'template-monitor',
    label: 'API 監控範本',
    description: '建立 API 健康檢查與告警流程',
    icon: 'monitor',
    prompt: '請幫我建立一個 API 監控流程，每 5 分鐘檢查 API 狀態，如果異常則發送告警。',
    category: 'template',
    tags: ['監控', 'api', '告警'],
  },
  {
    id: 'template-etl',
    label: 'ETL 處理範本',
    description: '建立資料擷取、轉換、載入流程',
    icon: 'database',
    prompt: '請幫我建立一個 ETL 流程，從來源擷取資料、進行轉換處理、然後載入目標資料庫。',
    category: 'template',
    tags: ['etl', '資料', '轉換'],
  },
  {
    id: 'template-ai-pipeline',
    label: 'AI 處理管線',
    description: '建立包含 AI 處理的流程',
    icon: 'robot',
    prompt: '請幫我建立一個包含 AI 處理的流程，可以自動處理和分析資料。',
    category: 'template',
    tags: ['ai', '處理', '分析'],
  },
]

/**
 * Get quick actions by category
 */
export function getActionsByCategory(category: QuickActionCategory): QuickAction[] {
  return AI_QUICK_ACTIONS.filter(action => action.category === category)
}

/**
 * Get quick actions that require a flow
 */
export function getFlowRequiredActions(): QuickAction[] {
  return AI_QUICK_ACTIONS.filter(action => action.requiresFlow)
}

/**
 * Get quick actions that don't require a flow
 */
export function getStandaloneActions(): QuickAction[] {
  return AI_QUICK_ACTIONS.filter(action => !action.requiresFlow)
}

/**
 * Search quick actions by query
 */
export function searchQuickActions(query: string): QuickAction[] {
  const lowerQuery = query.toLowerCase()
  return AI_QUICK_ACTIONS.filter(action => {
    if (action.label.toLowerCase().includes(lowerQuery)) return true
    if (action.description.toLowerCase().includes(lowerQuery)) return true
    if (action.tags?.some(tag => tag.toLowerCase().includes(lowerQuery))) return true
    return false
  })
}

/**
 * Context-aware suggestion types
 */
export type FlowContextType =
  | 'empty_canvas'
  | 'has_trigger'
  | 'has_condition'
  | 'complex_flow'
  | 'has_http'
  | 'has_ai'
  | 'no_error_handler'

/**
 * Context-aware suggestions based on current flow state
 */
export interface ContextSuggestion {
  text: string
  prompt: string
  priority: number
}

export const CONTEXT_AWARE_SUGGESTIONS: Record<FlowContextType, ContextSuggestion[]> = {
  empty_canvas: [
    { text: '建立新流程', prompt: '請幫我建立一個新的自動化流程', priority: 1 },
    { text: '從範本開始', prompt: '請推薦適合我需求的流程範本', priority: 2 },
    { text: '匯入現有流程', prompt: '我想匯入一個現有的流程並進行修改', priority: 3 },
    { text: '排程任務', prompt: '請幫我建立一個定時執行的排程任務', priority: 4 },
    { text: 'Webhook 處理', prompt: '請幫我建立一個 Webhook 接收處理流程', priority: 5 },
  ],
  has_trigger: [
    { text: '加入處理邏輯', prompt: '觸發器建立好了，請幫我加入後續的處理邏輯', priority: 1 },
    { text: '加入條件判斷', prompt: '請在觸發後加入條件判斷，根據不同情況執行不同操作', priority: 2 },
    { text: '加入資料轉換', prompt: '請加入資料轉換節點，處理觸發器傳入的資料', priority: 3 },
    { text: '加入通知', prompt: '請在流程最後加入通知，告知執行結果', priority: 4 },
  ],
  has_condition: [
    { text: '加入分支邏輯', prompt: '請幫我完善條件判斷後的兩個分支處理', priority: 1 },
    { text: '加入預設處理', prompt: '請加入預設處理，處理不符合任何條件的情況', priority: 2 },
    { text: '合併分支結果', prompt: '請幫我在分支結束後合併處理結果', priority: 3 },
  ],
  complex_flow: [
    { text: '優化流程', prompt: '請分析這個流程並提供優化建議', priority: 1 },
    { text: '拆分子流程', prompt: '這個流程有點複雜，請幫我拆分成多個子流程', priority: 2 },
    { text: '加入監控', prompt: '請為這個複雜流程加入執行監控和日誌', priority: 3 },
    { text: '檢查效能', prompt: '請檢查這個流程的效能瓶頸', priority: 4 },
  ],
  has_http: [
    { text: '加入重試機制', prompt: '請為 HTTP 請求加入自動重試機制', priority: 1 },
    { text: '加入逾時處理', prompt: '請加入 HTTP 請求逾時的處理邏輯', priority: 2 },
    { text: '處理回應', prompt: '請幫我加入處理 HTTP 回應的邏輯', priority: 3 },
    { text: '加入認證', prompt: '請為 HTTP 請求加入認證機制', priority: 4 },
  ],
  has_ai: [
    { text: '加入提示優化', prompt: '請幫我優化 AI 節點的提示詞', priority: 1 },
    { text: '處理 AI 輸出', prompt: '請加入處理 AI 輸出結果的邏輯', priority: 2 },
    { text: '加入快取', prompt: '請為 AI 呼叫加入快取，避免重複處理', priority: 3 },
    { text: '加入備用方案', prompt: '請加入 AI 呼叫失敗時的備用處理', priority: 4 },
  ],
  no_error_handler: [
    { text: '加入錯誤處理', prompt: '請為這個流程加入完整的錯誤處理機制', priority: 1 },
    { text: '加入告警通知', prompt: '請加入錯誤發生時的告警通知', priority: 2 },
    { text: '加入重試邏輯', prompt: '請加入失敗自動重試的機制', priority: 3 },
  ],
}

/**
 * Get context-aware suggestions based on flow state
 */
export function getContextSuggestions(
  nodeTypes: string[],
  nodeCount: number,
  hasErrorHandler: boolean
): ContextSuggestion[] {
  const suggestions: ContextSuggestion[] = []

  // Empty canvas
  if (nodeCount === 0) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.empty_canvas)
    return suggestions.sort((a, b) => a.priority - b.priority).slice(0, 5)
  }

  // Check for specific node types
  const hasTrigger = nodeTypes.some(t =>
    t.includes('trigger') || t.includes('webhook') || t.includes('schedule')
  )
  const hasCondition = nodeTypes.some(t =>
    t.includes('condition') || t.includes('switch') || t.includes('if')
  )
  const hasHttp = nodeTypes.some(t =>
    t.includes('http') || t.includes('request') || t.includes('api')
  )
  const hasAi = nodeTypes.some(t =>
    t.includes('ai') || t.includes('llm') || t.includes('chat') || t.includes('agent')
  )

  // Add relevant suggestions
  if (hasTrigger && nodeCount < 3) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.has_trigger)
  }

  if (hasCondition) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.has_condition)
  }

  if (hasHttp) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.has_http)
  }

  if (hasAi) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.has_ai)
  }

  // Complex flow
  if (nodeCount > 8) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.complex_flow)
  }

  // No error handler
  if (!hasErrorHandler && nodeCount > 2) {
    suggestions.push(...CONTEXT_AWARE_SUGGESTIONS.no_error_handler)
  }

  // Deduplicate and sort
  const uniqueSuggestions = suggestions.filter((s, i, arr) =>
    arr.findIndex(x => x.text === s.text) === i
  )

  return uniqueSuggestions.sort((a, b) => a.priority - b.priority).slice(0, 5)
}

/**
 * Common prompt templates for quick input
 */
export const PROMPT_TEMPLATES = [
  '請幫我建立一個{description}的流程',
  '當{trigger}時，執行{action}',
  '每{interval}執行一次{task}',
  '從{source}讀取資料，轉換後存入{target}',
  '監控{target}，異常時發送{notification}',
  '接收 Webhook，處理後回傳{response}',
]

export default AI_QUICK_ACTIONS
