import apiClient from './client'

export interface DashboardStats {
  totalFlows: number
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  runningExecutions: number
}

export const dashboardApi = {
  getStats: async (): Promise<DashboardStats> => {
    const response = await apiClient.get<DashboardStats>('/dashboard/stats')
    return response.data
  },
}

export default dashboardApi
