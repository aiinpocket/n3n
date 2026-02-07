import apiClient from './client'
import { getLocale } from '../utils/locale'

// ==================== Types ====================

export interface AgentRegistration {
  id: string
  deviceId: string | null
  deviceName: string | null
  platform: string | null
  status: 'PENDING' | 'REGISTERED' | 'BLOCKED'
  createdAt: number
  registeredAt: number | null
  blockedAt: number | null
  blockedReason: string | null
  lastSeenAt: number | null
}

export interface GatewaySettings {
  domain: string
  port: number
  enabled: boolean
  webSocketUrl: string
  httpUrl: string
  updatedAt: number
}

export interface AgentConfig {
  version: number
  gateway: {
    url: string
    domain: string
    port: number
  }
  registration: {
    token: string
    agentId: string
  }
}

export interface TokenGenerationResult {
  registrationId: string
  agentId: string
  config: AgentConfig
}

// ==================== Agent Registration API ====================

/**
 * Generate a new agent registration token
 * Returns JSON config (doesn't download)
 */
export async function generateAgentToken(): Promise<TokenGenerationResult> {
  const response = await apiClient.post<TokenGenerationResult>('/agents/tokens/json')
  return response.data
}

/**
 * Generate and download agent config file
 */
export async function downloadAgentConfig(): Promise<Blob> {
  const response = await apiClient.post('/agents/tokens', {}, {
    responseType: 'blob'
  })
  return response.data
}

/**
 * Download agent package (App + config) as ZIP
 * This is the one-click install experience
 */
export async function downloadAgentPackage(platform: 'macos' | 'windows'): Promise<Blob> {
  const response = await apiClient.post(`/agents/download/${platform}`, {}, {
    responseType: 'blob'
  })
  return response.data
}

export interface InstallCommandResult {
  command: string
  registrationId: string
  agentId: string
}

/**
 * Generate install command for one-line installation
 * Usage: curl -fsSL <url>/install.sh | bash
 */
export async function generateInstallCommand(): Promise<InstallCommandResult> {
  const response = await apiClient.post<InstallCommandResult>('/agents/install-command')
  return response.data
}

/**
 * Trigger browser download for a blob
 */
export function triggerDownload(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * List all agent registrations
 */
export async function listRegistrations(): Promise<AgentRegistration[]> {
  const response = await apiClient.get<{ registrations: AgentRegistration[] }>('/agents/registrations')
  return response.data.registrations
}

/**
 * Block an agent
 */
export async function blockAgent(id: string, reason?: string): Promise<void> {
  await apiClient.put(`/agents/${id}/block`, { reason })
}

/**
 * Unblock an agent
 */
export async function unblockAgent(id: string): Promise<void> {
  await apiClient.put(`/agents/${id}/unblock`)
}

/**
 * Delete an agent registration
 */
export async function deleteRegistration(id: string): Promise<void> {
  await apiClient.delete(`/agents/${id}`)
}

// ==================== Gateway Settings API ====================

/**
 * Get gateway settings (admin only)
 */
export async function getGatewaySettings(): Promise<GatewaySettings> {
  const response = await apiClient.get<GatewaySettings>('/settings/gateway')
  return response.data
}

/**
 * Update gateway settings (admin only)
 */
export async function updateGatewaySettings(
  settings: Partial<Pick<GatewaySettings, 'domain' | 'port' | 'enabled'>>
): Promise<{ success: boolean; settings: GatewaySettings; message: string }> {
  const response = await apiClient.put<{ success: boolean; settings: GatewaySettings; message: string }>(
    '/settings/gateway',
    settings
  )
  return response.data
}

// ==================== Helpers ====================

/**
 * Get status display info
 */
export function getStatusInfo(status: AgentRegistration['status']): {
  label: string
  color: string
} {
  switch (status) {
    case 'PENDING':
      return { label: 'agent.status.pending', color: 'orange' }
    case 'REGISTERED':
      return { label: 'agent.status.registered', color: 'green' }
    case 'BLOCKED':
      return { label: 'agent.status.blocked', color: 'red' }
    default:
      return { label: status, color: 'default' }
  }
}

/**
 * Format timestamp
 */
export function formatTime(timestamp: number | null): string {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString(getLocale())
}
