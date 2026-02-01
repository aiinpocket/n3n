import apiClient from './client'

// Types
export interface MarketplacePlugin {
  id: string
  name: string
  displayName: string
  description: string
  category: string
  author: string
  version: string
  downloads: number
  rating: number
  ratingCount: number
  icon: string | null
  screenshots: string[]
  tags: string[]
  pricing: 'free' | 'paid' | 'freemium'
  price: number | null
  isInstalled: boolean
  installedVersion: string | null
  publishedAt: string
  updatedAt: string
}

export interface MarketplaceCategory {
  id: string
  name: string
  displayName: string
  description: string
  icon: string
  count: number
}

export interface PluginDetail extends MarketplacePlugin {
  readme: string
  changelog: string
  dependencies: string[]
  configSchema: Record<string, unknown>
  capabilities: string[]
  supportUrl: string | null
  documentationUrl: string | null
  repositoryUrl: string | null
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
  plugins: MarketplacePlugin[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

// API Functions
export async function getCategories(): Promise<MarketplaceCategory[]> {
  const response = await apiClient.get<MarketplaceCategory[]>('/marketplace/categories')
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

  const response = await apiClient.get<SearchResult>(`/marketplace/plugins?${params.toString()}`)
  return response.data
}

export async function getFeaturedPlugins(): Promise<MarketplacePlugin[]> {
  const response = await apiClient.get<MarketplacePlugin[]>('/marketplace/plugins/featured')
  return response.data
}

export async function getPluginDetail(id: string): Promise<PluginDetail> {
  const response = await apiClient.get<PluginDetail>(`/marketplace/plugins/${id}`)
  return response.data
}

export async function installPlugin(id: string): Promise<InstallationResult> {
  const response = await apiClient.post<InstallationResult>(`/marketplace/plugins/${id}/install`)
  return response.data
}

export async function uninstallPlugin(id: string): Promise<{ success: boolean; message: string }> {
  const response = await apiClient.delete<{ success: boolean; message: string }>(`/marketplace/plugins/${id}/uninstall`)
  return response.data
}

export async function updatePlugin(id: string): Promise<InstallationResult> {
  const response = await apiClient.post<InstallationResult>(`/marketplace/plugins/${id}/update`)
  return response.data
}

export async function getInstalledPlugins(): Promise<MarketplacePlugin[]> {
  const response = await apiClient.get<MarketplacePlugin[]>('/marketplace/plugins/installed')
  return response.data
}
