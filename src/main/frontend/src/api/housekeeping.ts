import apiClient from './client'

// ==================== Types ====================

export interface HousekeepingStats {
  totalExecutions: number
  archivedExecutions: number
  oldExecutions: number
  retentionDays: number
  lastCleanupAt: string | null
  nextScheduledCleanup: string | null
}

export interface HousekeepingJob {
  id: string
  status: string
  recordsProcessed: number
  recordsArchived: number
  recordsDeleted: number
  startedAt: string
  completedAt: string | null
  errorMessage: string | null
}

export interface HousekeepingJobPage {
  content: HousekeepingJob[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ==================== API ====================

export const housekeepingApi = {
  getStats: async (): Promise<HousekeepingStats> => {
    const response = await apiClient.get('/admin/housekeeping/stats')
    return response.data
  },

  runCleanup: async (): Promise<HousekeepingJob> => {
    const response = await apiClient.post('/admin/housekeeping/run')
    return response.data
  },

  getJobHistory: async (page = 0, size = 20): Promise<HousekeepingJobPage> => {
    const response = await apiClient.get('/admin/housekeeping/jobs', {
      params: { page, size },
    })
    return response.data
  },

  getJob: async (id: string): Promise<HousekeepingJob> => {
    const response = await apiClient.get(`/admin/housekeeping/jobs/${id}`)
    return response.data
  },

  cleanupHistory: async (): Promise<{ recordsDeleted: number; message: string }> => {
    const response = await apiClient.post('/admin/housekeeping/cleanup-history')
    return response.data
  },
}
