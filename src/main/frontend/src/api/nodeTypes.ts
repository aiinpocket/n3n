import apiClient from './client'

export interface NodeTypeInfo {
  type: string
  displayName: string
  description: string
  category: string
  icon: string
  trigger: boolean
  supportsAsync: boolean
  configSchema: Record<string, unknown>
  interfaceDefinition: {
    inputs: Array<{ name: string; type: string; required?: boolean }>
    outputs: Array<{ name: string; type: string }>
  }
}

export async function fetchNodeTypes(): Promise<NodeTypeInfo[]> {
  const response = await apiClient.get<NodeTypeInfo[]>('/node-types')
  return response.data
}

export async function fetchNodeType(type: string): Promise<NodeTypeInfo> {
  const response = await apiClient.get<NodeTypeInfo>(`/node-types/${type}`)
  return response.data
}

export async function fetchNodeSchema(type: string): Promise<Record<string, unknown>> {
  const response = await apiClient.get<Record<string, unknown>>(`/node-types/${type}/schema`)
  return response.data
}

export async function fetchRegisteredTypes(): Promise<string[]> {
  const response = await apiClient.get<string[]>('/node-types/types')
  return response.data
}

export async function fetchNodeTypesByCategory(category: string): Promise<NodeTypeInfo[]> {
  const response = await apiClient.get<NodeTypeInfo[]>(`/node-types/category/${category}`)
  return response.data
}

export async function fetchTriggerNodeTypes(): Promise<NodeTypeInfo[]> {
  const response = await apiClient.get<NodeTypeInfo[]>('/node-types/triggers')
  return response.data
}

