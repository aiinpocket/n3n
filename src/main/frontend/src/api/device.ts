import apiClient from './client'
import axios from 'axios'

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

export interface AgentDownload {
  version: string
  filename: string
  sha256: string
  size: number
  minOS?: string
  status?: 'coming-soon'
}

export interface DownloadInfo {
  agents: {
    macos?: {
      arm64?: AgentDownload
      x86_64?: AgentDownload
    }
    windows?: {
      x86_64?: AgentDownload
    }
    linux?: {
      x86_64?: AgentDownload
    }
  }
  latestVersion: string
  releaseDate: string
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
 * Unpair a device
 */
export async function unpairDevice(deviceId: string): Promise<void> {
  await apiClient.delete(`/agents/${deviceId}`)
}

/**
 * Revoke all devices
 */
export async function revokeAllDevices(): Promise<void> {
  // Revoke all is not directly supported - will need backend endpoint
  await apiClient.post('/agents/revoke-all')
}

/**
 * Get agent download info
 */
export async function getDownloadInfo(): Promise<DownloadInfo> {
  const response = await axios.get<DownloadInfo>('/downloads/versions.json')
  return response.data
}

/**
 * Get download URL for a specific platform/arch
 */
export function getDownloadUrl(platform: string, arch: string): string {
  return `/downloads/n3n-agent-${platform}-${arch}`
}

/**
 * Format file size
 */
export function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
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
