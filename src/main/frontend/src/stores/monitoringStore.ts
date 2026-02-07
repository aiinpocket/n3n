import { create } from 'zustand'
import { monitoringApi, type SystemMetrics, type FlowExecutionStats, type HealthStatus } from '../api/monitoring'
import i18n from '../i18n'

interface MonitoringState {
  systemMetrics: SystemMetrics | null
  flowStats: FlowExecutionStats | null
  healthStatus: HealthStatus | null
  loading: boolean
  error: string | null

  fetchSystemMetrics: () => Promise<void>
  fetchFlowStats: () => Promise<void>
  fetchHealthStatus: () => Promise<void>
  fetchAll: () => Promise<void>
}

export const useMonitoringStore = create<MonitoringState>((set) => ({
  systemMetrics: null,
  flowStats: null,
  healthStatus: null,
  loading: false,
  error: null,

  fetchSystemMetrics: async () => {
    try {
      const data = await monitoringApi.getSystemMetrics()
      set({ systemMetrics: data })
    } catch {
      // silent fail for individual metrics
    }
  },

  fetchFlowStats: async () => {
    try {
      const data = await monitoringApi.getFlowStats()
      set({ flowStats: data })
    } catch {
      // silent fail
    }
  },

  fetchHealthStatus: async () => {
    try {
      const data = await monitoringApi.getHealthStatus()
      set({ healthStatus: data })
    } catch {
      // silent fail
    }
  },

  fetchAll: async () => {
    set({ loading: true, error: null })
    try {
      const [system, flows, health] = await Promise.allSettled([
        monitoringApi.getSystemMetrics(),
        monitoringApi.getFlowStats(),
        monitoringApi.getHealthStatus(),
      ])
      set({
        systemMetrics: system.status === 'fulfilled' ? system.value : null,
        flowStats: flows.status === 'fulfilled' ? flows.value : null,
        healthStatus: health.status === 'fulfilled' ? health.value : null,
        loading: false,
      })
    } catch {
      set({ loading: false, error: i18n.t('errorMessage.defaultMessage') })
    }
  },
}))
