import apiClient from './client'

// ==================== Types ====================

export interface ApprovalSummary {
  id: string
  executionId: string
  nodeId: string
  message: string
  approvalMode: string
  requiredApprovers: number
  approvedCount: number
  rejectedCount: number
  expiresAt: string | null
  createdAt: string
}

export interface ApprovalAction {
  id: string
  userId: string
  action: string
  comment: string | null
  createdAt: string
}

export interface ApprovalDetail {
  id: string
  executionId: string
  nodeId: string
  approvalType: string
  message: string
  requiredApprovers: number
  approvalMode: string
  status: string
  approvedCount: number
  rejectedCount: number
  expiresAt: string | null
  createdAt: string
  resolvedAt: string | null
  metadata: Record<string, unknown> | null
  actions: ApprovalAction[]
}

// ==================== API ====================

export const approvalApi = {
  getPending: async (): Promise<ApprovalSummary[]> => {
    const response = await apiClient.get('/approvals/pending')
    return response.data
  },

  getApproval: async (approvalId: string): Promise<ApprovalDetail> => {
    const response = await apiClient.get(`/approvals/${approvalId}`)
    return response.data
  },

  submitApproval: async (
    approvalId: string,
    action: string,
    comment?: string
  ): Promise<ApprovalDetail> => {
    const response = await apiClient.post(`/approvals/${approvalId}`, {
      action,
      comment,
    })
    return response.data
  },
}
