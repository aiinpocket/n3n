import apiClient from './client'

export interface AppParamSpec {
  name: string
  defaultValue: string | null
  required: boolean
  secret: boolean
}

export interface AppServiceSpec {
  name: string
  image: string | null
  build: string | null
  ports: number[] | null
  environment: Record<string, string> | null
  dependsOn: string[] | null
}

export interface AppManifest {
  type: 'compose' | 'dockerfile'
  services: AppServiceSpec[]
  params: AppParamSpec[]
  webService: string | null
  internalPort: number | null
}

export type AppStatus = 'created' | 'deploying' | 'running' | 'stopped' | 'failed'

export interface HostedAppItem {
  id: string
  name: string
  slug: string
  appType: string
  status: AppStatus
  manifest: AppManifest | null
  filledParams: string[]
  hostPort: number | null
  internalPort: number | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface AppsAvailability {
  enabled: boolean
  baseDomain: string | null
  /** slug 之後的完整字尾（含 "." 或 "-" 分隔符），例如 "-n3n.example.com" */
  hostSuffix?: string | null
}

export const appsApi = {
  availability: async (): Promise<AppsAvailability> => {
    const { data } = await apiClient.get<AppsAvailability>('/apps/availability')
    return data
  },

  list: async (): Promise<HostedAppItem[]> => {
    const { data } = await apiClient.get<HostedAppItem[]>('/apps')
    return data
  },

  analyze: async (file: File): Promise<AppManifest> => {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<AppManifest>('/apps/analyze', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  create: async (file: File, name: string): Promise<HostedAppItem> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', name)
    const { data } = await apiClient.post<HostedAppItem>('/apps', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  get: async (id: string): Promise<HostedAppItem> => {
    const { data } = await apiClient.get<HostedAppItem>(`/apps/${id}`)
    return data
  },

  deploy: async (id: string, params: Record<string, string>): Promise<HostedAppItem> => {
    const { data } = await apiClient.post<HostedAppItem>(`/apps/${id}/deploy`, { params })
    return data
  },

  stop: async (id: string): Promise<HostedAppItem> => {
    const { data } = await apiClient.post<HostedAppItem>(`/apps/${id}/stop`)
    return data
  },

  start: async (id: string): Promise<HostedAppItem> => {
    const { data } = await apiClient.post<HostedAppItem>(`/apps/${id}/start`)
    return data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/apps/${id}`)
  },

  logs: async (id: string, lines = 200): Promise<string> => {
    const { data } = await apiClient.get<{ logs: string }>(`/apps/${id}/logs`, {
      params: { lines },
    })
    return data.logs
  },
}
