import apiClient from './client'

export interface OAuth2Status {
  connected: boolean
  provider?: string
  expired?: boolean
  expiringSoon?: boolean
  expiresAt?: string
  scope?: string
}

export interface OAuth2AuthUrl {
  authorizationUrl: string
}

export interface OAuth2CallbackResult {
  success?: boolean
  provider?: string
  credentialId?: string
  expiresAt?: string
  error?: string
  description?: string
}

export const oauth2Api = {
  /**
   * Get OAuth2 authorization URL for a provider.
   * Redirects user to the provider's consent page.
   */
  getAuthUrl: async (provider: string, credentialId: string, scope?: string): Promise<OAuth2AuthUrl> => {
    const params: Record<string, string> = { credentialId }
    if (scope) params.scope = scope
    const response = await apiClient.get(`/oauth2/authorize/${provider}`, { params })
    return response.data
  },

  /**
   * Check OAuth2 connection status for a credential.
   */
  getStatus: async (credentialId: string): Promise<OAuth2Status> => {
    const response = await apiClient.get(`/oauth2/status/${credentialId}`)
    return response.data
  },

  /**
   * Disconnect OAuth2 for a credential.
   */
  disconnect: async (credentialId: string): Promise<void> => {
    await apiClient.delete(`/oauth2/disconnect/${credentialId}`)
  }
}

export default oauth2Api
