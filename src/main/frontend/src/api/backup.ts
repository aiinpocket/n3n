import api from './client'

export interface BackupSettings {
  enabled: boolean
  provider: string | null
  endpoint: string | null
  bucket: string | null
  basePath: string | null
  region: string | null
  hasAccessKey: boolean
  hasSecretKey: boolean
  hasServiceAccountJson: boolean
  sftpHost: string | null
  sftpPort: number | null
  sftpUsername: string | null
  hasSftpPassword: boolean
  hasSftpPrivateKey: boolean
  sftpPath: string | null
  schedule: string | null
  lastBackupAt: string | null
  updatedAt: string | null
}

export interface UpdateBackupSettingsRequest {
  enabled?: boolean
  provider?: string
  endpoint?: string
  bucket?: string
  basePath?: string
  accessKey?: string
  secretKey?: string
  region?: string
  serviceAccountJson?: string
  sftpHost?: string
  sftpPort?: number
  sftpUsername?: string
  sftpPassword?: string
  sftpPrivateKey?: string
  sftpPath?: string
  schedule?: string
}

export interface BackupHistory {
  id: string
  filename: string
  fileSize: number
  provider: string
  checksum: string
  status: string
  errorMessage: string | null
  createdAt: string
}

export interface RemoteBackupInfo {
  filename: string
  size: number
  lastModified: string
}

export const backupApi = {
  getSettings: () =>
    api.get<BackupSettings>('/admin/backup/settings'),

  updateSettings: (data: UpdateBackupSettingsRequest) =>
    api.put<BackupSettings>('/admin/backup/settings', data),

  testConnection: () =>
    api.post<{ success: boolean; message: string }>('/admin/backup/test-connection'),

  createBackup: () =>
    api.post<BackupHistory>('/admin/backup/create'),

  getHistory: (page = 0, size = 20) =>
    api.get<{ content: BackupHistory[]; totalElements: number }>('/admin/backup/history', {
      params: { page, size },
    }),

  listRemoteBackups: (recoveryKeyPhrase: string) =>
    api.post<RemoteBackupInfo[]>('/admin/backup/list-remote', { recoveryKeyPhrase }),

  restoreBackup: (recoveryKeyPhrase: string, filename: string) =>
    api.post<{ message: string }>('/admin/backup/restore', { recoveryKeyPhrase, filename }),
}

export default backupApi
