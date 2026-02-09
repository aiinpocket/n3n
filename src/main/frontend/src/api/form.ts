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
}

export default formApi
