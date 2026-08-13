import apiClient from './client'

export interface SiteItem {
  id: string
  slug: string
  name: string
  description: string | null
  isPublished: boolean
  url: string
  customDomain: string | null
  customDomainVerified: boolean
  fileCount: number
  totalSizeBytes: number
  createdAt: string
  updatedAt: string
}

export interface SiteDnsRecord {
  type: string
  host: string
  value: string
}

export interface SiteCustomDomain {
  domain: string | null
  verified: boolean
  records: SiteDnsRecord[]
}

export interface SiteFileMeta {
  path: string
  contentType: string | null
  sizeBytes: number
  updatedAt: string
}

export interface SiteDetail {
  site: SiteItem
  files: SiteFileMeta[]
}

export interface SiteFileContent {
  path: string
  contentType: string
  sizeBytes: number
  content: string
}

export interface SiteFileUpsert {
  path: string
  content?: string
  contentBase64?: string
  contentType?: string
}

export const sitesApi = {
  list: async (): Promise<SiteItem[]> => {
    const { data } = await apiClient.get<SiteItem[]>('/sites')
    return data
  },

  create: async (name: string, description?: string): Promise<SiteItem> => {
    const { data } = await apiClient.post<SiteItem>('/sites', { name, description })
    return data
  },

  get: async (id: string): Promise<SiteDetail> => {
    const { data } = await apiClient.get<SiteDetail>(`/sites/${id}`)
    return data
  },

  update: async (
    id: string,
    changes: { name?: string; description?: string; isPublished?: boolean },
  ): Promise<SiteItem> => {
    const { data } = await apiClient.put<SiteItem>(`/sites/${id}`, changes)
    return data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/sites/${id}`)
  },

  upsertFiles: async (id: string, files: SiteFileUpsert[]): Promise<SiteFileMeta[]> => {
    const { data } = await apiClient.put<SiteFileMeta[]>(`/sites/${id}/files`, { files })
    return data
  },

  deleteFile: async (id: string, path: string): Promise<void> => {
    await apiClient.delete(`/sites/${id}/files`, { params: { path } })
  },

  fileContent: async (id: string, path: string): Promise<SiteFileContent> => {
    const { data } = await apiClient.get<SiteFileContent>(`/sites/${id}/files/content`, {
      params: { path },
    })
    return data
  },

  uploadZip: async (id: string, file: File): Promise<SiteFileMeta[]> => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<SiteFileMeta[]>(`/sites/${id}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  getCustomDomain: async (id: string): Promise<SiteCustomDomain> => {
    const { data } = await apiClient.get<SiteCustomDomain>(`/sites/${id}/custom-domain`)
    return data
  },

  setCustomDomain: async (id: string, domain: string): Promise<SiteCustomDomain> => {
    const { data } = await apiClient.put<SiteCustomDomain>(`/sites/${id}/custom-domain`, {
      domain,
    })
    return data
  },

  verifyCustomDomain: async (id: string): Promise<SiteCustomDomain> => {
    const { data } = await apiClient.post<SiteCustomDomain>(`/sites/${id}/custom-domain/verify`)
    return data
  },

  removeCustomDomain: async (id: string): Promise<void> => {
    await apiClient.delete(`/sites/${id}/custom-domain`)
  },
}
