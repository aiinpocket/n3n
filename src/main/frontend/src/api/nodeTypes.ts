import axios from 'axios'

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

const api = axios.create({
  baseURL: '/api',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export async function fetchNodeTypes(): Promise<NodeTypeInfo[]> {
  const response = await api.get<NodeTypeInfo[]>('/node-types')
  return response.data
}

export async function fetchNodeType(type: string): Promise<NodeTypeInfo> {
  const response = await api.get<NodeTypeInfo>(`/node-types/${type}`)
  return response.data
}

export async function fetchNodeTypeSchema(type: string): Promise<Record<string, unknown>> {
  const response = await api.get<Record<string, unknown>>(`/node-types/${type}/schema`)
  return response.data
}

export async function fetchTriggerTypes(): Promise<NodeTypeInfo[]> {
  const response = await api.get<NodeTypeInfo[]>('/node-types/triggers')
  return response.data
}
