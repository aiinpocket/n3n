import apiClient from './client'

// ==================== Types ====================

export interface DeviceInfo {
  deviceId: string
  deviceName: string
  platform: string
  pairedAt: number
  lastActiveAt: number
  directConnectionEnabled: boolean
  externalAddress: string
  revoked: boolean
}

export interface DeviceUpdateRequest {
  externalAddress?: string
  directConnectionEnabled?: boolean
  allowedIps?: string[]
}

export interface PairingSession {
  pairingCode: string
  expiresAt: number
  expiresIn: number
}

// ==================== API Functions ====================

/**
 * List paired devices for the current user
 */
export async function listDevices(): Promise<DeviceInfo[]> {
  const response = await apiClient.get('/agent/devices')
  return response.data.devices || response.data
}

/**
 * Unpair a specific device
 */
export async function unpairDevice(deviceId: string): Promise<void> {
  await apiClient.delete(`/agent/devices/${deviceId}`)
}

/**
 * Revoke all paired devices (emergency logout)
 */
export async function revokeAllDevices(): Promise<{ revokedCount: number }> {
  const response = await apiClient.post('/agent/devices/revoke-all')
  return response.data
}
