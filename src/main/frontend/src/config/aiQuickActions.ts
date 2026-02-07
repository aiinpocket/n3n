/**
 * AI Quick Actions Configuration
 * Defines common AI-assisted operations for quick access.
 * Labels, descriptions, prompts and text use i18n keys - call t() when rendering.
 */

export interface QuickAction {
  id: string
  label: string       // i18n key
  description: string  // i18n key
  icon: string
  prompt: string       // i18n key
  category: QuickActionCategory
  requiresFlow?: boolean
  tags?: string[]      // i18n keys
}

export type QuickActionCategory = 'create' | 'optimize' | 'analyze' | 'debug' | 'template'

export const QUICK_ACTION_CATEGORIES: Record<QuickActionCategory, { label: string; color: string }> = {
  create: { label: 'quickActions.category.create', color: '#52c41a' },
  optimize: { label: 'quickActions.category.optimize', color: '#1890ff' },
  analyze: { label: 'quickActions.category.analyze', color: '#8B5CF6' },
  debug: { label: 'quickActions.category.debug', color: '#fa8c16' },
  template: { label: 'quickActions.category.template', color: '#13c2c2' },
}

export const AI_QUICK_ACTIONS: QuickAction[] = [
  // Create actions
  {
    id: 'create-basic-flow',
    label: 'quickActions.createBasicFlow.label',
    description: 'quickActions.createBasicFlow.description',
    icon: 'plus-circle',
    prompt: 'quickActions.createBasicFlow.prompt',
    category: 'create',
    tags: ['quickActions.tags.new', 'quickActions.tags.flow'],
  },
  {
    id: 'create-schedule-task',
    label: 'quickActions.createScheduleTask.label',
    description: 'quickActions.createScheduleTask.description',
    icon: 'clock-circle',
    prompt: 'quickActions.createScheduleTask.prompt',
    category: 'create',
    tags: ['quickActions.tags.schedule', 'quickActions.tags.timer', 'cron'],
  },
  {
    id: 'create-webhook-handler',
    label: 'quickActions.createWebhookHandler.label',
    description: 'quickActions.createWebhookHandler.description',
    icon: 'api',
    prompt: 'quickActions.createWebhookHandler.prompt',
    category: 'create',
    tags: ['webhook', 'api', 'quickActions.tags.receive'],
  },
  {
    id: 'create-data-sync',
    label: 'quickActions.createDataSync.label',
    description: 'quickActions.createDataSync.description',
    icon: 'sync',
    prompt: 'quickActions.createDataSync.prompt',
    category: 'create',
    tags: ['quickActions.tags.data', 'quickActions.tags.sync', 'quickActions.tags.import'],
  },
  {
    id: 'create-notification',
    label: 'quickActions.createNotification.label',
    description: 'quickActions.createNotification.description',
    icon: 'bell',
    prompt: 'quickActions.createNotification.prompt',
    category: 'create',
    tags: ['quickActions.tags.notification', 'quickActions.tags.message', 'slack', 'email'],
  },

  // Optimize actions
  {
    id: 'optimize-performance',
    label: 'quickActions.optimizePerformance.label',
    description: 'quickActions.optimizePerformance.description',
    icon: 'thunderbolt',
    prompt: 'quickActions.optimizePerformance.prompt',
    category: 'optimize',
    requiresFlow: true,
    tags: ['quickActions.tags.performance', 'quickActions.tags.optimize', 'quickActions.tags.speed'],
  },
  {
    id: 'add-error-handling',
    label: 'quickActions.addErrorHandling.label',
    description: 'quickActions.addErrorHandling.description',
    icon: 'warning',
    prompt: 'quickActions.addErrorHandling.prompt',
    category: 'optimize',
    requiresFlow: true,
    tags: ['quickActions.tags.error', 'quickActions.tags.handling', 'quickActions.tags.retry'],
  },
  {
    id: 'add-logging',
    label: 'quickActions.addLogging.label',
    description: 'quickActions.addLogging.description',
    icon: 'file-text',
    prompt: 'quickActions.addLogging.prompt',
    category: 'optimize',
    requiresFlow: true,
    tags: ['quickActions.tags.log', 'quickActions.tags.record', 'log'],
  },
  {
    id: 'parallelize',
    label: 'quickActions.parallelize.label',
    description: 'quickActions.parallelize.description',
    icon: 'branches',
    prompt: 'quickActions.parallelize.prompt',
    category: 'optimize',
    requiresFlow: true,
    tags: ['quickActions.tags.parallel', 'quickActions.tags.concurrent', 'quickActions.tags.efficiency'],
  },

  // Analyze actions
  {
    id: 'explain-flow',
    label: 'quickActions.explainFlow.label',
    description: 'quickActions.explainFlow.description',
    icon: 'question-circle',
    prompt: 'quickActions.explainFlow.prompt',
    category: 'analyze',
    requiresFlow: true,
    tags: ['quickActions.tags.explain', 'quickActions.tags.description', 'quickActions.tags.understand'],
  },
  {
    id: 'find-issues',
    label: 'quickActions.findIssues.label',
    description: 'quickActions.findIssues.description',
    icon: 'bug',
    prompt: 'quickActions.findIssues.prompt',
    category: 'analyze',
    requiresFlow: true,
    tags: ['quickActions.tags.issue', 'quickActions.tags.check', 'quickActions.tags.review'],
  },
  {
    id: 'suggest-improvements',
    label: 'quickActions.suggestImprovements.label',
    description: 'quickActions.suggestImprovements.description',
    icon: 'bulb',
    prompt: 'quickActions.suggestImprovements.prompt',
    category: 'analyze',
    requiresFlow: true,
    tags: ['quickActions.tags.suggestion', 'quickActions.tags.improvement', 'quickActions.tags.optimize'],
  },

  // Debug actions
  {
    id: 'debug-error',
    label: 'quickActions.debugError.label',
    description: 'quickActions.debugError.description',
    icon: 'tool',
    prompt: 'quickActions.debugError.prompt',
    category: 'debug',
    requiresFlow: true,
    tags: ['quickActions.tags.debug', 'quickActions.tags.error', 'quickActions.tags.fix'],
  },
  {
    id: 'trace-data',
    label: 'quickActions.traceData.label',
    description: 'quickActions.traceData.description',
    icon: 'node-index',
    prompt: 'quickActions.traceData.prompt',
    category: 'debug',
    requiresFlow: true,
    tags: ['quickActions.tags.trace', 'quickActions.tags.data', 'quickActions.tags.direction'],
  },

  // Template actions
  {
    id: 'template-monitor',
    label: 'quickActions.templateMonitor.label',
    description: 'quickActions.templateMonitor.description',
    icon: 'monitor',
    prompt: 'quickActions.templateMonitor.prompt',
    category: 'template',
    tags: ['quickActions.tags.monitor', 'api', 'quickActions.tags.alert'],
  },
  {
    id: 'template-etl',
    label: 'quickActions.templateEtl.label',
    description: 'quickActions.templateEtl.description',
    icon: 'database',
    prompt: 'quickActions.templateEtl.prompt',
    category: 'template',
    tags: ['etl', 'quickActions.tags.data', 'quickActions.tags.transform'],
  },
  {
    id: 'template-ai-pipeline',
    label: 'quickActions.templateAiPipeline.label',
    description: 'quickActions.templateAiPipeline.description',
    icon: 'robot',
    prompt: 'quickActions.templateAiPipeline.prompt',
    category: 'template',
    tags: ['ai', 'quickActions.tags.processing', 'quickActions.tags.analysis'],
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
 * Search quick actions by query (searches translated values)
 * Note: caller should pass t() function to translate keys before searching
 */
export function searchQuickActions(query: string, t: (key: string) => string): QuickAction[] {
  const lowerQuery = query.toLowerCase()
  return AI_QUICK_ACTIONS.filter(action => {
    if (t(action.label).toLowerCase().includes(lowerQuery)) return true
    if (t(action.description).toLowerCase().includes(lowerQuery)) return true
    if (action.tags?.some(tag => t(tag).toLowerCase().includes(lowerQuery))) return true
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
  text: string    // i18n key
  prompt: string  // i18n key
  priority: number
}

export const CONTEXT_AWARE_SUGGESTIONS: Record<FlowContextType, ContextSuggestion[]> = {
  empty_canvas: [
    { text: 'contextSuggestions.emptyCanvas.createNew', prompt: 'contextSuggestions.emptyCanvas.createNewPrompt', priority: 1 },
    { text: 'contextSuggestions.emptyCanvas.fromTemplate', prompt: 'contextSuggestions.emptyCanvas.fromTemplatePrompt', priority: 2 },
    { text: 'contextSuggestions.emptyCanvas.importFlow', prompt: 'contextSuggestions.emptyCanvas.importFlowPrompt', priority: 3 },
    { text: 'contextSuggestions.emptyCanvas.scheduleTask', prompt: 'contextSuggestions.emptyCanvas.scheduleTaskPrompt', priority: 4 },
    { text: 'contextSuggestions.emptyCanvas.webhookHandler', prompt: 'contextSuggestions.emptyCanvas.webhookHandlerPrompt', priority: 5 },
  ],
  has_trigger: [
    { text: 'contextSuggestions.hasTrigger.addLogic', prompt: 'contextSuggestions.hasTrigger.addLogicPrompt', priority: 1 },
    { text: 'contextSuggestions.hasTrigger.addCondition', prompt: 'contextSuggestions.hasTrigger.addConditionPrompt', priority: 2 },
    { text: 'contextSuggestions.hasTrigger.addTransform', prompt: 'contextSuggestions.hasTrigger.addTransformPrompt', priority: 3 },
    { text: 'contextSuggestions.hasTrigger.addNotification', prompt: 'contextSuggestions.hasTrigger.addNotificationPrompt', priority: 4 },
  ],
  has_condition: [
    { text: 'contextSuggestions.hasCondition.addBranch', prompt: 'contextSuggestions.hasCondition.addBranchPrompt', priority: 1 },
    { text: 'contextSuggestions.hasCondition.addDefault', prompt: 'contextSuggestions.hasCondition.addDefaultPrompt', priority: 2 },
    { text: 'contextSuggestions.hasCondition.mergeBranches', prompt: 'contextSuggestions.hasCondition.mergeBranchesPrompt', priority: 3 },
  ],
  complex_flow: [
    { text: 'contextSuggestions.complexFlow.optimize', prompt: 'contextSuggestions.complexFlow.optimizePrompt', priority: 1 },
    { text: 'contextSuggestions.complexFlow.splitSubflow', prompt: 'contextSuggestions.complexFlow.splitSubflowPrompt', priority: 2 },
    { text: 'contextSuggestions.complexFlow.addMonitoring', prompt: 'contextSuggestions.complexFlow.addMonitoringPrompt', priority: 3 },
    { text: 'contextSuggestions.complexFlow.checkPerformance', prompt: 'contextSuggestions.complexFlow.checkPerformancePrompt', priority: 4 },
  ],
  has_http: [
    { text: 'contextSuggestions.hasHttp.addRetry', prompt: 'contextSuggestions.hasHttp.addRetryPrompt', priority: 1 },
    { text: 'contextSuggestions.hasHttp.addTimeout', prompt: 'contextSuggestions.hasHttp.addTimeoutPrompt', priority: 2 },
    { text: 'contextSuggestions.hasHttp.handleResponse', prompt: 'contextSuggestions.hasHttp.handleResponsePrompt', priority: 3 },
    { text: 'contextSuggestions.hasHttp.addAuth', prompt: 'contextSuggestions.hasHttp.addAuthPrompt', priority: 4 },
  ],
  has_ai: [
    { text: 'contextSuggestions.hasAi.optimizePrompts', prompt: 'contextSuggestions.hasAi.optimizePromptsPrompt', priority: 1 },
    { text: 'contextSuggestions.hasAi.handleOutput', prompt: 'contextSuggestions.hasAi.handleOutputPrompt', priority: 2 },
    { text: 'contextSuggestions.hasAi.addCache', prompt: 'contextSuggestions.hasAi.addCachePrompt', priority: 3 },
    { text: 'contextSuggestions.hasAi.addFallback', prompt: 'contextSuggestions.hasAi.addFallbackPrompt', priority: 4 },
  ],
  no_error_handler: [
    { text: 'contextSuggestions.noErrorHandler.addErrorHandling', prompt: 'contextSuggestions.noErrorHandler.addErrorHandlingPrompt', priority: 1 },
    { text: 'contextSuggestions.noErrorHandler.addAlert', prompt: 'contextSuggestions.noErrorHandler.addAlertPrompt', priority: 2 },
    { text: 'contextSuggestions.noErrorHandler.addRetry', prompt: 'contextSuggestions.noErrorHandler.addRetryPrompt', priority: 3 },
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
 * Common prompt templates for quick input - i18n keys
 */
export const PROMPT_TEMPLATES = [
  'quickActions.promptTemplates.createFlow',
  'quickActions.promptTemplates.whenTrigger',
  'quickActions.promptTemplates.everyInterval',
  'quickActions.promptTemplates.readTransformStore',
  'quickActions.promptTemplates.monitorAlert',
  'quickActions.promptTemplates.webhookRespond',
]

export default AI_QUICK_ACTIONS
