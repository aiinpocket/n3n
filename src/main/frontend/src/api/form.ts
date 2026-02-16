import apiClient from './client'

export interface FormField {
  name: string
  type: string // text, number, email, textarea, select, checkbox, date, file
  label: string
  required: boolean
  placeholder?: string
  options?: string[] // for select type
  defaultValue?: unknown
  description?: string
}

export interface FormDefinition {
  token: string
  title: string
  description?: string
  fields: FormField[]
  submitButtonText?: string
  successMessage?: string
}

export interface FormSubmitResponse {
  success: boolean
  executionId?: string
  message?: string
  redirectUrl?: string
}

export interface FormTrigger {
  id: string
  flowId: string
  nodeId: string
  formToken: string
  isActive: boolean
  expiresAt: string | null
  maxSubmissions: number
  submissionCount: number
  config: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export const formApi = {
  // Get form definition (public, no auth)
  getForm: async (token: string): Promise<FormDefinition> => {
    const response = await apiClient.get<FormDefinition>(`/forms/${token}`)
    return response.data
  },

  // Submit form (public, no auth)
  submitForm: async (token: string, data: Record<string, unknown>): Promise<FormSubmitResponse> => {
    const response = await apiClient.post<FormSubmitResponse>(`/forms/${token}/submit`, data)
    return response.data
  },

  // Submit form in execution context (authenticated)
  submitExecutionForm: async (executionId: string, nodeId: string, formData: Record<string, unknown>): Promise<FormSubmitResponse> => {
    const response = await apiClient.post<FormSubmitResponse>(`/forms/execution/${executionId}/submit`, { nodeId, formData })
    return response.data
  },

  // Get form URL for a flow (authenticated)
  getFormUrl: async (flowId: string, nodeId: string): Promise<{ formUrl: string; formToken: string; isActive: boolean }> => {
    const response = await apiClient.get<{ formUrl: string; formToken: string; isActive: boolean }>(`/forms/flow/${flowId}/url`, {
      params: { nodeId },
    })
    return response.data
  },

  // ===== Form Trigger Management (authenticated) =====

  // List all form triggers for current user
  listMyTriggers: async (): Promise<FormTrigger[]> => {
    const response = await apiClient.get<FormTrigger[]>('/forms/triggers')
    return response.data
  },

  // List form triggers for a specific flow
  listTriggersForFlow: async (flowId: string): Promise<FormTrigger[]> => {
    const response = await apiClient.get<FormTrigger[]>(`/forms/triggers/flow/${flowId}`)
    return response.data
  },

  // Get a specific form trigger
  getTrigger: async (triggerId: string): Promise<FormTrigger> => {
    const response = await apiClient.get<FormTrigger>(`/forms/triggers/${triggerId}`)
    return response.data
  },

  // Deactivate a form trigger
  deactivateTrigger: async (triggerId: string): Promise<void> => {
    await apiClient.post(`/forms/triggers/${triggerId}/deactivate`)
  },

  // Activate a form trigger
  activateTrigger: async (triggerId: string): Promise<FormTrigger> => {
    const response = await apiClient.post<FormTrigger>(`/forms/triggers/${triggerId}/activate`)
    return response.data
  },

  // Regenerate form token
  regenerateToken: async (triggerId: string): Promise<FormTrigger> => {
    const response = await apiClient.post<FormTrigger>(`/forms/triggers/${triggerId}/regenerate-token`)
    return response.data
  },
}

export default formApi
