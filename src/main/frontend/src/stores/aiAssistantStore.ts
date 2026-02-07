import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import i18n from '../i18n'
import logger from '../utils/logger'

// Types
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: Date
  structuredData?: Record<string, unknown>
  flowSnapshot?: FlowSnapshot
  isStreaming?: boolean
}

export interface FlowSnapshot {
  nodes: Array<{
    id: string
    type: string
    label?: string
    config?: Record<string, unknown>
  }>
  edges: Array<{
    source: string
    target: string
  }>
}

export interface PendingChange {
  id: string
  type: 'add_node' | 'remove_node' | 'modify_node' | 'connect_nodes'
  description: string
  before?: Record<string, unknown>
  after?: Record<string, unknown>
  applied?: boolean
}

export interface ConversationSession {
  id: string
  flowId?: string
  title: string
  messages: ChatMessage[]
  createdAt: Date
  updatedAt: Date
  isFavorite?: boolean
  tags?: string[]
}

interface AIAssistantState {
  // Panel state
  isPanelOpen: boolean
  panelWidth: number

  // Current session
  currentSession: ConversationSession | null
  sessions: ConversationSession[]

  // Streaming state
  isStreaming: boolean
  streamingContent: string
  streamingStage: string

  // Pending changes
  pendingChanges: PendingChange[]

  // Flow context
  currentFlowId: string | null
  currentFlowDefinition: FlowSnapshot | null

  // History for undo/redo
  flowHistory: FlowSnapshot[]
  historyIndex: number

  // Error state
  error: string | null

  // Actions
  openPanel: () => void
  closePanel: () => void
  togglePanel: () => void
  setPanelWidth: (width: number) => void

  // Session management
  startNewSession: (flowId?: string) => void
  loadSession: (sessionId: string) => void
  clearSession: () => void
  deleteSession: (sessionId: string) => void
  toggleFavorite: (sessionId: string) => void
  addTagToSession: (sessionId: string, tag: string) => void
  removeTagFromSession: (sessionId: string, tag: string) => void
  searchSessions: (query: string) => ConversationSession[]
  getGroupedSessions: () => Record<string, ConversationSession[]>

  // Message handling
  addUserMessage: (content: string) => void
  addAssistantMessage: (content: string, structuredData?: Record<string, unknown>) => void
  updateStreamingContent: (content: string, stage?: string) => void
  finalizeStreaming: (structuredData?: Record<string, unknown>) => void
  setStreaming: (isStreaming: boolean) => void

  // Pending changes
  addPendingChange: (change: PendingChange) => void
  applyChange: (changeId: string) => void
  rejectChange: (changeId: string) => void
  applyAllChanges: () => void
  clearPendingChanges: () => void

  // Flow context
  setFlowContext: (flowId: string, definition: FlowSnapshot) => void
  updateFlowDefinition: (definition: FlowSnapshot) => void

  // History management (undo/redo)
  undo: () => void
  redo: () => void
  canUndo: () => boolean
  canRedo: () => boolean
  pushHistory: (snapshot: FlowSnapshot) => void
  clearHistory: () => void

  // Error handling
  setError: (error: string | null) => void
  clearError: () => void

  // Import/Export
  exportSession: (sessionId?: string) => string | null
  importSession: (json: string) => boolean
}

const generateId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
const MAX_HISTORY_SIZE = 50

export const useAIAssistantStore = create<AIAssistantState>()(
  persist(
    (set, get) => ({
      // Initial state
      isPanelOpen: false,
      panelWidth: 400,
      currentSession: null,
      sessions: [],
      isStreaming: false,
      streamingContent: '',
      streamingStage: '',
      pendingChanges: [],
      currentFlowId: null,
      currentFlowDefinition: null,
      flowHistory: [],
      historyIndex: -1,
      error: null,

      // Panel actions
      openPanel: () => set({ isPanelOpen: true }),
      closePanel: () => set({ isPanelOpen: false }),
      togglePanel: () => set((state) => ({ isPanelOpen: !state.isPanelOpen })),
      setPanelWidth: (width) => set({ panelWidth: Math.max(320, Math.min(800, width)) }),

      // Session management
      startNewSession: (flowId) => {
        const session: ConversationSession = {
          id: generateId(),
          flowId,
          title: flowId ? `${i18n.t('ai.flowConversation')} ${new Date().toLocaleTimeString()}` : `${i18n.t('ai.newConversation')} ${new Date().toLocaleTimeString()}`,
          messages: [],
          createdAt: new Date(),
          updatedAt: new Date(),
        }
        set((state) => ({
          currentSession: session,
          sessions: [session, ...state.sessions].slice(0, 20), // Keep last 20 sessions
          pendingChanges: [],
          error: null,
        }))
      },

      loadSession: (sessionId) => {
        const session = get().sessions.find((s) => s.id === sessionId)
        if (session) {
          set({
            currentSession: session,
            pendingChanges: [],
            error: null,
          })
        }
      },

      clearSession: () => set({
        currentSession: null,
        pendingChanges: [],
        streamingContent: '',
        streamingStage: '',
        isStreaming: false,
        error: null,
      }),

      deleteSession: (sessionId) => set((state) => ({
        sessions: state.sessions.filter((s) => s.id !== sessionId),
        currentSession: state.currentSession?.id === sessionId ? null : state.currentSession,
      })),

      toggleFavorite: (sessionId) => set((state) => ({
        sessions: state.sessions.map((s) =>
          s.id === sessionId ? { ...s, isFavorite: !s.isFavorite } : s
        ),
        currentSession: state.currentSession?.id === sessionId
          ? { ...state.currentSession, isFavorite: !state.currentSession.isFavorite }
          : state.currentSession,
      })),

      addTagToSession: (sessionId, tag) => set((state) => ({
        sessions: state.sessions.map((s) =>
          s.id === sessionId
            ? { ...s, tags: [...(s.tags || []), tag].filter((t, i, arr) => arr.indexOf(t) === i) }
            : s
        ),
      })),

      removeTagFromSession: (sessionId, tag) => set((state) => ({
        sessions: state.sessions.map((s) =>
          s.id === sessionId
            ? { ...s, tags: (s.tags || []).filter((t) => t !== tag) }
            : s
        ),
      })),

      searchSessions: (query) => {
        const state = get()
        const lowerQuery = query.toLowerCase()
        return state.sessions.filter((s) => {
          // Search in title
          if (s.title.toLowerCase().includes(lowerQuery)) return true
          // Search in tags
          if (s.tags?.some((t) => t.toLowerCase().includes(lowerQuery))) return true
          // Search in messages
          if (s.messages.some((m) => m.content.toLowerCase().includes(lowerQuery))) return true
          return false
        })
      },

      getGroupedSessions: () => {
        const state = get()
        const groups: Record<string, ConversationSession[]> = {
          favorites: [],
          today: [],
          yesterday: [],
          thisWeek: [],
          older: [],
        }

        const now = new Date()
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
        const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
        const weekAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)

        state.sessions.forEach((session) => {
          const sessionDate = new Date(session.updatedAt)

          if (session.isFavorite) {
            groups.favorites.push(session)
          } else if (sessionDate >= today) {
            groups.today.push(session)
          } else if (sessionDate >= yesterday) {
            groups.yesterday.push(session)
          } else if (sessionDate >= weekAgo) {
            groups.thisWeek.push(session)
          } else {
            groups.older.push(session)
          }
        })

        return groups
      },

      // Message handling
      addUserMessage: (content) => {
        const message: ChatMessage = {
          id: generateId(),
          role: 'user',
          content,
          timestamp: new Date(),
        }

        set((state) => {
          if (!state.currentSession) {
            // Auto-create session
            const session: ConversationSession = {
              id: generateId(),
              flowId: state.currentFlowId || undefined,
              title: content.substring(0, 30) + (content.length > 30 ? '...' : ''),
              messages: [message],
              createdAt: new Date(),
              updatedAt: new Date(),
            }
            return {
              currentSession: session,
              sessions: [session, ...state.sessions].slice(0, 20),
            }
          }

          const updatedSession = {
            ...state.currentSession,
            messages: [...state.currentSession.messages, message],
            updatedAt: new Date(),
          }

          return {
            currentSession: updatedSession,
            sessions: state.sessions.map((s) =>
              s.id === updatedSession.id ? updatedSession : s
            ),
          }
        })
      },

      addAssistantMessage: (content, structuredData) => {
        const message: ChatMessage = {
          id: generateId(),
          role: 'assistant',
          content,
          timestamp: new Date(),
          structuredData,
          flowSnapshot: structuredData?.flowDefinition as FlowSnapshot | undefined,
        }

        set((state) => {
          if (!state.currentSession) return state

          const updatedSession = {
            ...state.currentSession,
            messages: [...state.currentSession.messages, message],
            updatedAt: new Date(),
          }

          return {
            currentSession: updatedSession,
            sessions: state.sessions.map((s) =>
              s.id === updatedSession.id ? updatedSession : s
            ),
            streamingContent: '',
            streamingStage: '',
          }
        })
      },

      updateStreamingContent: (content, stage) => set((state) => ({
        streamingContent: state.streamingContent + content,
        streamingStage: stage || state.streamingStage,
      })),

      finalizeStreaming: (structuredData) => {
        const state = get()
        if (state.streamingContent) {
          state.addAssistantMessage(state.streamingContent, structuredData)
        }
        set({
          isStreaming: false,
          streamingContent: '',
          streamingStage: '',
        })
      },

      setStreaming: (isStreaming) => set({
        isStreaming,
        streamingContent: isStreaming ? '' : get().streamingContent,
        streamingStage: isStreaming ? i18n.t('ai.thinking') : '',
      }),

      // Pending changes
      addPendingChange: (change) => set((state) => ({
        pendingChanges: [...state.pendingChanges, change],
      })),

      applyChange: (changeId) => set((state) => ({
        pendingChanges: state.pendingChanges.map((c) =>
          c.id === changeId ? { ...c, applied: true } : c
        ),
      })),

      rejectChange: (changeId) => set((state) => ({
        pendingChanges: state.pendingChanges.filter((c) => c.id !== changeId),
      })),

      applyAllChanges: () => set((state) => ({
        pendingChanges: state.pendingChanges.map((c) => ({ ...c, applied: true })),
      })),

      clearPendingChanges: () => set({ pendingChanges: [] }),

      // Flow context
      setFlowContext: (flowId, definition) => {
        set({
          currentFlowId: flowId,
          currentFlowDefinition: definition,
          // Initialize history with the initial definition
          flowHistory: [definition],
          historyIndex: 0,
        })
      },

      updateFlowDefinition: (definition) => {
        const state = get()
        // Push to history when updating
        const newHistory = [...state.flowHistory.slice(0, state.historyIndex + 1), definition]
          .slice(-MAX_HISTORY_SIZE)
        set({
          currentFlowDefinition: definition,
          flowHistory: newHistory,
          historyIndex: newHistory.length - 1,
        })
      },

      // History management (undo/redo)
      undo: () => {
        const state = get()
        if (state.historyIndex > 0) {
          const newIndex = state.historyIndex - 1
          set({
            historyIndex: newIndex,
            currentFlowDefinition: state.flowHistory[newIndex],
          })
        }
      },

      redo: () => {
        const state = get()
        if (state.historyIndex < state.flowHistory.length - 1) {
          const newIndex = state.historyIndex + 1
          set({
            historyIndex: newIndex,
            currentFlowDefinition: state.flowHistory[newIndex],
          })
        }
      },

      canUndo: () => {
        const state = get()
        return state.historyIndex > 0
      },

      canRedo: () => {
        const state = get()
        return state.historyIndex < state.flowHistory.length - 1
      },

      pushHistory: (snapshot) => {
        set((state) => {
          // Truncate future history when pushing new state
          const newHistory = [...state.flowHistory.slice(0, state.historyIndex + 1), snapshot]
            .slice(-MAX_HISTORY_SIZE)
          return {
            flowHistory: newHistory,
            historyIndex: newHistory.length - 1,
          }
        })
      },

      clearHistory: () => set({
        flowHistory: [],
        historyIndex: -1,
      }),

      // Error handling
      setError: (error) => set({ error }),
      clearError: () => set({ error: null }),

      // Import/Export
      exportSession: (sessionId) => {
        const state = get()
        const session = sessionId
          ? state.sessions.find((s) => s.id === sessionId)
          : state.currentSession

        if (!session) return null

        const exportData = {
          id: session.id,
          title: session.title,
          flowId: session.flowId,
          messages: session.messages.map((m) => ({
            id: m.id,
            role: m.role,
            content: m.content,
            timestamp: m.timestamp,
            structuredData: m.structuredData,
          })),
          createdAt: session.createdAt,
          updatedAt: session.updatedAt,
          exportedAt: new Date().toISOString(),
          version: '1.0',
        }

        return JSON.stringify(exportData, null, 2)
      },

      importSession: (json) => {
        try {
          const data = JSON.parse(json)

          if (!data.title || !Array.isArray(data.messages)) {
            logger.error('Invalid session format')
            return false
          }

          const session: ConversationSession = {
            id: generateId(),
            flowId: data.flowId,
            title: `${data.title} (${i18n.t('ai.imported')})`,
            messages: data.messages.map((m: ChatMessage) => ({
              ...m,
              id: m.id || generateId(),
              timestamp: new Date(m.timestamp),
            })),
            createdAt: new Date(),
            updatedAt: new Date(),
          }

          set((state) => ({
            currentSession: session,
            sessions: [session, ...state.sessions].slice(0, 20),
            pendingChanges: [],
            error: null,
          }))

          return true
        } catch (e) {
          logger.error('Failed to import session:', e)
          return false
        }
      },
    }),
    {
      name: 'n3n-ai-assistant',
      partialize: (state) => ({
        sessions: state.sessions,
        panelWidth: state.panelWidth,
      }),
    }
  )
)
