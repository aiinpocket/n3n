import apiClient from './client'

export interface FlowShare {
  id: string
  flowId: string
  userId?: string
  userName?: string
  userEmail?: string
  invitedEmail?: string
  permission: 'view' | 'edit' | 'admin'
  sharedBy: string
  sharedByName?: string
  sharedAt: string
  acceptedAt?: string
  pending: boolean
}

export interface ShareFlowRequest {
  userId?: string
  email?: string
  permission: 'view' | 'edit' | 'admin'
}

export const flowShareApi = {
  // Get flow shares
  getShares: async (flowId: string): Promise<FlowShare[]> => {
    const response = await apiClient.get(`/flows/${flowId}/shares`)
    return response.data
  },

  // Share flow
  share: async (flowId: string, request: ShareFlowRequest): Promise<FlowShare> => {
    const response = await apiClient.post(`/flows/${flowId}/shares`, request)
    return response.data
  },

  // Update share permission
  updatePermission: async (flowId: string, shareId: string, permission: string): Promise<FlowShare> => {
    const response = await apiClient.put(`/flows/${flowId}/shares/${shareId}`, null, {
      params: { permission }
    })
    return response.data
  },

  // Remove share
  removeShare: async (flowId: string, shareId: string): Promise<void> => {
    await apiClient.delete(`/flows/${flowId}/shares/${shareId}`)
  },

  // Get flows shared with me
  getSharedWithMe: async (): Promise<FlowShare[]> => {
    const response = await apiClient.get('/flows/shared-with-me')
    return response.data
  }
}

export default flowShareApi
