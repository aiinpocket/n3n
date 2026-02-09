import apiClient from './client'

export interface Schedule {
  id: string
  flowId: string
  flowName: string | null
  name: string
  cronExpression: string
  timezone: string
  isActive: boolean
  input: Record<string, unknown> | null
  lastRunAt: string | null
  nextRunAt: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface CreateScheduleRequest {
  flowId: string
  name: string
  cronExpression: string
  timezone?: string
  input?: Record<string, unknown>
}

export interface UpdateScheduleRequest {
  name?: string
  cronExpression?: string
  timezone?: string
  input?: Record<string, unknown>
}

export const schedulerApi = {
  list: async (): Promise<Schedule[]> => {
    const response = await apiClient.get<Schedule[]>('/schedules')
    return response.data
  },

  get: async (id: string): Promise<Schedule> => {
    const response = await apiClient.get<Schedule>(`/schedules/${id}`)
    return response.data
  },

  create: async (request: CreateScheduleRequest): Promise<Schedule> => {
    const response = await apiClient.post<Schedule>('/schedules', request)
    return response.data
  },

  update: async (id: string, data: UpdateScheduleRequest): Promise<Schedule> => {
    const response = await apiClient.put<Schedule>(`/schedules/${id}`, data)
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/schedules/${id}`)
  },

  pause: async (id: string): Promise<Schedule> => {
    const response = await apiClient.post<Schedule>(`/schedules/${id}/pause`)
    return response.data
  },

  resume: async (id: string): Promise<Schedule> => {
    const response = await apiClient.post<Schedule>(`/schedules/${id}/resume`)
    return response.data
  },

  trigger: async (id: string): Promise<{ success: boolean; message?: string }> => {
    const response = await apiClient.post<{ success: boolean; message?: string }>(`/schedules/${id}/trigger`)
    return response.data
  },
}

export default schedulerApi
