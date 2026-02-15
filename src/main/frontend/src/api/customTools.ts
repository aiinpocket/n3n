import apiClient from './client'

// Types
export interface CustomToolPlugin {
  id: string
  name: string
  displayName: string
  description: string
  category: string
  author: string
  authorUrl: string | null
  version: string
  downloads: number
  rating: number
  ratingCount: number
  iconUrl: string | null
  tags: string[]
  pricing: 'free' | 'paid' | 'freemium'
  price: number | null
  isInstalled: boolean
  installedVersion: string | null
  repositoryUrl: string | null
  documentationUrl: string | null
  publishedAt: string
  updatedAt: string
}

/** @deprecated Use CustomToolPlugin instead */
export type MarketplacePlugin = CustomToolPlugin

export interface CustomToolCategory {
  id: string
  name: string
  displayName: string
  description: string
  icon: string
  count: number
}

/** @deprecated Use CustomToolCategory instead */
export type MarketplaceCategory = CustomToolCategory

export interface PluginVersion {
  version: string
  changelog: string
  publishedAt: string
}

export interface PluginDetail extends CustomToolPlugin {
  readme: string
  changelog: string
  configSchema: Record<string, unknown>
  capabilities: string[]
  nodeDefinitions?: Record<string, unknown>
  versions?: PluginVersion[]
}

export interface InstallationResult {
  success: boolean
  message: string
  installedVersion: string
}

export interface SearchFilters {
  category?: string
  pricing?: 'free' | 'paid' | 'freemium' | 'all'
  sortBy?: 'popular' | 'recent' | 'rating' | 'name'
  query?: string
  page?: number
  pageSize?: number
}

export interface SearchResult {
  plugins: CustomToolPlugin[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

// Installation task tracking types
export interface InstallTask {
  id: string
  pluginId: string
  nodeType: string
  source: string
  sourceReference: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progressPercent: number | null
  currentStage: string | null
  errorMessage: string | null
  containerId: string | null
  containerPort: number | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

// API Functions
export async function getCategories(): Promise<CustomToolCategory[]> {
  const response = await apiClient.get<CustomToolCategory[]>('/custom-tools/categories')
  return response.data
}

export async function searchPlugins(filters: SearchFilters = {}): Promise<SearchResult> {
  const params = new URLSearchParams()
  if (filters.category) params.append('category', filters.category)
  if (filters.pricing && filters.pricing !== 'all') params.append('pricing', filters.pricing)
  if (filters.sortBy) params.append('sortBy', filters.sortBy)
  if (filters.query) params.append('q', filters.query)
  if (filters.page) params.append('page', filters.page.toString())
  if (filters.pageSize) params.append('pageSize', filters.pageSize.toString())

  const response = await apiClient.get<SearchResult>(`/custom-tools/plugins?${params.toString()}`)
  return response.data
}

export async function getFeaturedPlugins(): Promise<CustomToolPlugin[]> {
  const response = await apiClient.get<CustomToolPlugin[]>('/custom-tools/plugins/featured')
  return response.data
}

export async function getPluginDetail(id: string): Promise<PluginDetail> {
  const response = await apiClient.get<PluginDetail>(`/custom-tools/plugins/${id}`)
  return response.data
}

export async function installPlugin(id: string): Promise<InstallationResult> {
  const response = await apiClient.post<InstallationResult>(`/custom-tools/plugins/${id}/install`)
  return response.data
}

export async function uninstallPlugin(id: string): Promise<void> {
  await apiClient.delete(`/custom-tools/plugins/${id}/uninstall`)
}

export async function updatePlugin(id: string): Promise<InstallationResult> {
  const response = await apiClient.post<InstallationResult>(`/custom-tools/plugins/${id}/update`)
  return response.data
}

export async function getInstalledPlugins(): Promise<CustomToolPlugin[]> {
  const response = await apiClient.get<CustomToolPlugin[]>('/custom-tools/plugins/installed')
  return response.data
}

export async function ratePlugin(id: string, rating: number, review?: string): Promise<{ success: boolean; message: string }> {
  const response = await apiClient.post<{ success: boolean; message: string }>(
    `/custom-tools/plugins/${id}/rate`,
    { rating, review }
  )
  return response.data
}

// Installation task API
export async function getActiveInstallTasks(): Promise<InstallTask[]> {
  const response = await apiClient.get<InstallTask[]>('/plugins/install/tasks')
  return response.data
}

export async function getInstallTaskStatus(taskId: string): Promise<InstallTask> {
  const response = await apiClient.get<InstallTask>(`/plugins/install/tasks/${taskId}`)
  return response.data
}

export async function cancelInstallTask(taskId: string): Promise<void> {
  await apiClient.delete(`/plugins/install/tasks/${taskId}`)
}
