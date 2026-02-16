import apiClient from './client'
import { useAuthStore } from '../stores/authStore'
import logger from '../utils/logger'

export interface LogEntry {
  timestamp: string
  level: string
  logger: string
  message: string
  traceId: string | null
  executionId: string | null
  flowId: string | null
  nodeId: string | null
  userId: string | null
  threadName: string
}

export const logsApi = {
  getLogs: async (level?: string, search?: string, limit = 100): Promise<LogEntry[]> => {
    const params: Record<string, string | number> = { limit }
    if (level && level !== 'ALL') params.level = level
    if (search) params.search = search
    const response = await apiClient.get<LogEntry[]>('/logs', { params })
    return response.data
  },
}

export interface LogStreamHandle {
  close(): void
}

const MAX_RECONNECT_ATTEMPTS = 3
const RECONNECT_DELAY_MS = 2000

export function createLogStream(
  onMessage: (entry: LogEntry) => void,
  onError?: (error: Event) => void,
): LogStreamHandle {
  let closed = false
  let reconnectAttempts = 0
  let currentEventSource: EventSource | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function connect() {
    if (closed) return
    const token = useAuthStore.getState().accessToken || ''
    const es = new EventSource(`/api/logs/stream?token=${encodeURIComponent(token)}`)
    currentEventSource = es

    es.onmessage = (event) => {
      if (closed) return
      try {
        const entry: LogEntry = JSON.parse(event.data)
        onMessage(entry)
        reconnectAttempts = 0
      } catch {
        // ignore parse errors
      }
    }

    es.onerror = (event) => {
      if (closed) return
      es.close()
      currentEventSource = null

      if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
        reconnectAttempts++
        logger.warn(`SSE connection lost, reconnecting (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`)
        reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      } else {
        logger.warn('SSE reconnection failed after max attempts')
        if (onError) onError(event)
      }
    }
  }

  connect()

  return {
    close() {
      closed = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      currentEventSource?.close()
      currentEventSource = null
    },
  }
}
