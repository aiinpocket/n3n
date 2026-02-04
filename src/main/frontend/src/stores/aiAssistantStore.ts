import { create } from 'zustand'
import { persist } from 'zustand/middleware'

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

  // Error handling
  setError: (error: string | null) => void
  clearError: () => void

  // Import/Export
  exportSession: (sessionId?: string) => string | null
  importSession: (json: string) => boolean
}

const generateId = () => `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`

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
          title: flowId ? `流程對話 ${new Date().toLocaleTimeString()}` : `新對話 ${new Date().toLocaleTimeString()}`,
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
        streamingStage: isStreaming ? '思考中...' : '',
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
      setFlowContext: (flowId, definition) => set({
        currentFlowId: flowId,
        currentFlowDefinition: definition,
      }),

      updateFlowDefinition: (definition) => set({
        currentFlowDefinition: definition,
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
            console.error('Invalid session format')
            return false
          }

          const session: ConversationSession = {
            id: generateId(),
            flowId: data.flowId,
            title: `${data.title} (匯入)`,
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
          console.error('Failed to import session:', e)
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
