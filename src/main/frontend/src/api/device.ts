import apiClient from './client'

// ==================== Types ====================

export interface Device {
  deviceId: string
  deviceName: string
  platform: 'macos' | 'windows' | 'linux'
  pairedAt: number
  lastActiveAt: number
  directConnectionEnabled: boolean
  externalAddress: string | null
  revoked: boolean
}

export interface PairingInitiation {
  pairingCode: string
  expiresAt: number
  expiresIn: number
}

export interface DeviceUpdateRequest {
  externalAddress?: string | null
  directConnectionEnabled?: boolean
  allowedIps?: string[]
}

// ==================== API Functions ====================

/**
 * Initiate a new pairing session
 */
export async function initiatePairing(): Promise<PairingInitiation> {
  const response = await apiClient.post<PairingInitiation>('/agents/tokens/json')
  return response.data
}

/**
 * List all paired devices
 */
export async function listDevices(): Promise<Device[]> {
  const response = await apiClient.get<{ registrations: Device[] }>('/agents/registrations')
  return response.data.registrations || []
}

/**
 * Update device settings
 */
export async function updateDevice(
  deviceId: string,
  update: DeviceUpdateRequest
): Promise<void> {
  await apiClient.put(`/agents/${deviceId}`, update)
}

/**
 * Get platform display name
 */
export function getPlatformName(platform: string): string {
  const names: Record<string, string> = {
    macos: 'macOS',
    windows: 'Windows',
    linux: 'Linux',
  }
  return names[platform] || platform
}

/**
 * Get platform icon
 */
export function getPlatformIcon(platform: string): string {
  const icons: Record<string, string> = {
    macos: '🍎',
    windows: '🪟',
    linux: '🐧',
  }
  return icons[platform] || '💻'
}
