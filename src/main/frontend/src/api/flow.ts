import apiClient from './client'

export interface Flow {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  createdBy: string
  latestVersion: string | null
  publishedVersion: string | null
  userPermission?: 'owner' | 'admin' | 'edit' | 'view'
}

export interface FlowVersion {
  id: string
  flowId: string
  version: string
  definition: FlowDefinition
  settings: Record<string, unknown>
  pinnedData: Record<string, unknown>
  status: 'draft' | 'published' | 'deprecated'
  createdAt: string
  createdBy: string
}

export interface PinDataRequest {
  nodeId: string
  data: Record<string, unknown>
}

export interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
  viewport?: { x: number; y: number; zoom: number }
}

export interface FlowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: Record<string, unknown>
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  edgeType?: string
  label?: string
}

export interface CreateFlowRequest {
  name: string
  description?: string
}

export interface UpdateFlowRequest {
  name?: string
  description?: string
}

export interface SaveVersionRequest {
  version: string
  definition: FlowDefinition
  settings?: Record<string, unknown>
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface FlowValidationResponse {
  valid: boolean
  errors: string[]
  warnings: string[]
  entryPoints: string[]
  exitPoints: string[]
  executionOrder: string[]
  dependencies: Record<string, string[]>
}

export interface UpstreamNodeOutput {
  nodeId: string
  nodeLabel: string
  nodeType: string
  outputSchema: Record<string, unknown>
  flattenedFields: OutputField[]
}

export interface OutputField {
  path: string
  type: string
  description?: string
  expression: string
}

export const flowApi = {
  listFlows: async (page = 0, size = 20, search?: string): Promise<PageResponse<Flow>> => {
    const params: Record<string, unknown> = { page, size }
    if (search) params.search = search
    const response = await apiClient.get('/flows', { params })
    return response.data
  },

  listEditableFlows: async (): Promise<Flow[]> => {
    const response = await apiClient.get('/flows/editable')
    return response.data
  },

  getFlow: async (id: string): Promise<Flow> => {
    const response = await apiClient.get(`/flows/${id}`)
    return response.data
  },

  createFlow: async (data: CreateFlowRequest): Promise<Flow> => {
    const response = await apiClient.post('/flows', data)
    return response.data
  },

  updateFlow: async (id: string, data: UpdateFlowRequest): Promise<Flow> => {
    const response = await apiClient.put(`/flows/${id}`, data)
    return response.data
  },

  deleteFlow: async (id: string): Promise<void> => {
    await apiClient.delete(`/flows/${id}`)
  },

  cloneFlow: async (id: string, name?: string): Promise<Flow> => {
    const params: Record<string, string> = {}
    if (name) params.name = name
    const response = await apiClient.post(`/flows/${id}/clone`, null, { params })
    return response.data
  },

  listVersions: async (flowId: string): Promise<FlowVersion[]> => {
    const response = await apiClient.get(`/flows/${flowId}/versions`)
    return response.data
  },

  getVersion: async (flowId: string, version: string): Promise<FlowVersion> => {
    const response = await apiClient.get(`/flows/${flowId}/versions/${version}`)
    return response.data
  },

  saveVersion: async (flowId: string, data: SaveVersionRequest): Promise<FlowVersion> => {
    const response = await apiClient.post(`/flows/${flowId}/versions`, data)
    return response.data
  },

  publishVersion: async (flowId: string, version: string): Promise<FlowVersion> => {
    const response = await apiClient.post(`/flows/${flowId}/versions/${version}/publish`)
    return response.data
  },

  validateVersion: async (flowId: string, version: string): Promise<FlowValidationResponse> => {
    const response = await apiClient.get(`/flows/${flowId}/versions/${version}/validate`)
    return response.data
  },

  /**
   * Get upstream node outputs for input mapping in the flow editor.
   */
  getUpstreamOutputs: async (
    flowId: string,
    version: string,
    nodeId: string
  ): Promise<UpstreamNodeOutput[]> => {
    const response = await apiClient.get(
      `/flows/${flowId}/versions/${version}/nodes/${nodeId}/upstream-outputs`
    )
    return response.data
  },

  /**
   * Export a flow version to JSON format
   */
  exportFlow: async (flowId: string, version: string): Promise<FlowExportData> => {
    const response = await apiClient.get(`/flows/${flowId}/versions/${version}/export`)
    return response.data
  },

  /**
   * Preview an import without creating the flow
   */
  previewImport: async (data: FlowExportData): Promise<FlowImportPreview> => {
    const response = await apiClient.post('/flows/import/preview', data)
    return response.data
  },

  /**
   * Import a flow from exported data
   */
  importFlow: async (
    data: FlowExportData,
    newFlowName?: string,
    credentialMappings?: Record<string, string>,
  ): Promise<Flow> => {
    const response = await apiClient.post('/flows/import', {
      packageData: data,
      newFlowName: newFlowName || undefined,
      credentialMappings: credentialMappings || undefined,
      autoInstallMissingComponents: false,
    })
    return response.data
  },

  // ========== Data Pinning APIs ==========

  /**
   * Pin data to a specific node
   */
  pinNodeData: async (flowId: string, version: string, request: PinDataRequest): Promise<void> => {
    await apiClient.post(`/flows/${flowId}/versions/${version}/pin`, request)
  },

  /**
   * Unpin data from a specific node
   */
  unpinNodeData: async (flowId: string, version: string, nodeId: string): Promise<void> => {
    await apiClient.delete(`/flows/${flowId}/versions/${version}/pin/${nodeId}`)
  },

  batchDelete: async (ids: string[]): Promise<{ deleted: number; total: number }> => {
    const response = await apiClient.delete('/flows/batch', { data: { ids } })
    return response.data
  },

}

// Matches backend FlowExportPackage
export interface FlowExportData {
  version: string
  exportedAt: string
  exportedBy?: string
  flow: {
    name: string
    description: string | null
    definition: Record<string, unknown>
    settings: Record<string, unknown>
  }
  dependencies?: {
    components: Array<{
      name: string
      version: string
      image?: string
    }>
    credentialPlaceholders: Array<{
      nodeId: string
      credentialType: string
      originalName: string
    }>
  }
  checksum?: string
}

// Matches backend FlowImportPreviewResponse
export interface FlowImportPreview {
  flowName: string
  description: string | null
  nodeCount: number
  edgeCount: number
  canImport: boolean
  blockers: string[]
  componentStatuses: Array<{
    name: string
    version: string
    image?: string
    installed: boolean
    versionMatch: boolean
    installedVersion?: string
    canAutoInstall: boolean
  }>
  credentialRequirements: Array<{
    nodeId: string
    nodeName: string
    credentialType: string
    originalCredentialName: string
    compatibleCredentials: Array<{
      id: string
      name: string
      type: string
    }>
  }>
}
