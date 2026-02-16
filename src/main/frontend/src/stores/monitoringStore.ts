import { create } from 'zustand'
import { monitoringApi, type SystemMetrics, type FlowExecutionStats, type HealthStatus } from '../api/monitoring'
import { extractApiError } from '../utils/errorMessages'
import { logger } from '../utils/logger'
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
  clearError: () => void
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
      set({ systemMetrics: data, error: null })
    } catch (error) {
      logger.error('Failed to fetch system metrics:', error)
      set({ error: extractApiError(error, i18n.t('errorMessage.defaultMessage')) })
    }
  },

  fetchFlowStats: async () => {
    try {
      const data = await monitoringApi.getFlowStats()
      set({ flowStats: data, error: null })
    } catch (error) {
      logger.error('Failed to fetch flow stats:', error)
      set({ error: extractApiError(error, i18n.t('errorMessage.defaultMessage')) })
    }
  },

  fetchHealthStatus: async () => {
    try {
      const data = await monitoringApi.getHealthStatus()
      set({ healthStatus: data, error: null })
    } catch (error) {
      logger.error('Failed to fetch health status:', error)
      set({ error: extractApiError(error, i18n.t('errorMessage.defaultMessage')) })
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
      const rejected = [system, flows, health].find(r => r.status === 'rejected') as PromiseRejectedResult | undefined
      set({
        systemMetrics: system.status === 'fulfilled' ? system.value : null,
        flowStats: flows.status === 'fulfilled' ? flows.value : null,
        healthStatus: health.status === 'fulfilled' ? health.value : null,
        loading: false,
        error: rejected ? extractApiError(rejected.reason, i18n.t('errorMessage.defaultMessage')) : null,
      })
    } catch (error) {
      set({ loading: false, error: extractApiError(error, i18n.t('errorMessage.defaultMessage')) })
    }
  },

  clearError: () => set({ error: null }),
}))
