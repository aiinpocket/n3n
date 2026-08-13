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

export async function fetchNodeType(type: string): Promise<NodeTypeInfo> {
  const response = await apiClient.get<NodeTypeInfo>(`/node-types/${type}`)
  return response.data
}


