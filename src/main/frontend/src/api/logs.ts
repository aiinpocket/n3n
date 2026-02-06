import apiClient from './client'

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

export function createLogStream(
  onMessage: (entry: LogEntry) => void,
  onError?: (error: Event) => void,
): EventSource {
  const token = localStorage.getItem('accessToken') || ''
  const eventSource = new EventSource(`/api/logs/stream?token=${encodeURIComponent(token)}`)

  eventSource.onmessage = (event) => {
    try {
      const entry: LogEntry = JSON.parse(event.data)
      onMessage(entry)
    } catch {
      // ignore parse errors
    }
  }

  eventSource.onerror = (event) => {
    if (onError) onError(event)
  }

  return eventSource
}
