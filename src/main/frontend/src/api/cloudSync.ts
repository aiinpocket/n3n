import api from './client'

export interface SyncEntityInfo {
  type: string
  id: string
  name: string | null
  updatedAt: string | null
}

export interface CloudSyncManifest {
  fingerprint: string
  flowCount: number
  credentialCount: number
  aiProviderCount: number
  entities: SyncEntityInfo[]
}

export interface CloudSyncImportResult {
  flowsImported: number
  credentialsImported: number
  aiProvidersImported: number
  skipped: number
  failed: number
  errors: string[]
}

export interface CloudSyncStatus {
  enabled: boolean
  provider: string | null
  fingerprint: string | null
}

export const cloudSyncApi = {
  scan: (recoveryKeyPhrase: string) =>
    api.post<CloudSyncManifest>('/cloud-sync/scan', { recoveryKeyPhrase }),

  importEntities: (recoveryKeyPhrase: string) =>
    api.post<CloudSyncImportResult>('/cloud-sync/import', { recoveryKeyPhrase }),

  getStatus: () =>
    api.get<CloudSyncStatus>('/cloud-sync/status'),
}

export default cloudSyncApi
