import apiClient from './client'

export interface ProviderBalance {
  credentialId: string
  credentialName: string
  provider: string
  kind: 'BALANCE' | 'QUOTA' | 'USAGE_ONLY' | 'ERROR'
  balance: number | null
  currency: string | null
  quotaUsed: number | null
  quotaLimit: number | null
  quotaUnit: string | null
  localSpentUsd: number | null
  error: string | null
}

export interface UsageSummaryRow {
  provider: string
  model: string
  callCount: number
  inputTokens: number
  outputTokens: number
  estimatedCostUsd: number
}

export const aiBillingApi = {
  getBalances: async (): Promise<ProviderBalance[]> => {
    const response = await apiClient.get<ProviderBalance[]>('/ai/billing/balances')
    return response.data
  },

  getUsage: async (days = 30): Promise<UsageSummaryRow[]> => {
    const response = await apiClient.get<UsageSummaryRow[]>('/ai/billing/usage', {
      params: { days },
    })
    return response.data
  },
}
