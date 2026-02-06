import apiClient from './client'

export interface SystemMetrics {
  heapUsed: number
  heapMax: number
  nonHeapUsed: number
  threadCount: number
  threadPeak: number
  cpuUsage: number
  gcCount: number
  gcTimeMs: number
  uptimeMs: number
  availableProcessors: number
  totalMemory: number
  freeMemory: number
}

export interface FlowExecutionStats {
  total24h: number
  running: number
  completed: number
  failed: number
  cancelled: number
  avgDurationMs: number | null
  totalAllTime: number
}

export interface HealthStatus {
  database: string
  dbResponseMs: number
  redis: string
  redisResponseMs: number
  overall: string
}

export const monitoringApi = {
  getSystemMetrics: async (): Promise<SystemMetrics> => {
    const response = await apiClient.get<SystemMetrics>('/monitoring/system')
    return response.data
  },

  getFlowStats: async (): Promise<FlowExecutionStats> => {
    const response = await apiClient.get<FlowExecutionStats>('/monitoring/flows')
    return response.data
  },

  getHealthStatus: async (): Promise<HealthStatus> => {
    const response = await apiClient.get<HealthStatus>('/monitoring/health')
    return response.data
  },
}
