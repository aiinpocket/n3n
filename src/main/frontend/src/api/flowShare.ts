import apiClient from './client'

export interface FlowShare {
  id: string
  flowId: string
  flowName?: string
  flowDescription?: string
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

export interface ShareLink {
  id: string
  token: string
  permission: 'view' | 'edit'
  createdAt: string
  expiresAt?: string
  url: string
}

export interface CreateShareLinkRequest {
  permission: 'view' | 'edit'
  expiresInDays?: number
}

export interface ClaimShareLinkResult {
  flowId: string
  permission: string
  flowName: string
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
  },

  // Create a share link (owner/admin only)
  createShareLink: async (flowId: string, request: CreateShareLinkRequest): Promise<ShareLink> => {
    const response = await apiClient.post(`/flows/${flowId}/share-links`, request)
    return response.data
  },

  // List active share links (owner/admin only)
  listShareLinks: async (flowId: string): Promise<ShareLink[]> => {
    const response = await apiClient.get(`/flows/${flowId}/share-links`)
    return response.data
  },

  // Revoke a share link
  revokeShareLink: async (flowId: string, linkId: string): Promise<void> => {
    await apiClient.delete(`/flows/${flowId}/share-links/${linkId}`)
  },

  // Claim a share link (any authenticated user)
  claimShareLink: async (token: string): Promise<ClaimShareLinkResult> => {
    const response = await apiClient.post(`/flows/share-links/${encodeURIComponent(token)}/claim`)
    return response.data
  }
}

export default flowShareApi
