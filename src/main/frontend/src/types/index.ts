// Flow types
export interface Flow {
  id: string
  name: string
  description: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface FlowVersion {
  id: string
  flowId: string
  version: string
  definition: FlowDefinition
  settings: FlowSettings
  status: 'draft' | 'published' | 'deprecated'
  createdAt: string
}

export interface FlowDefinition {
  nodes: FlowNode[]
  edges: FlowEdge[]
}

export interface FlowNode {
  id: string
  component: string
  version: string
  position: { x: number; y: number }
  config: Record<string, unknown>
  inputMappings: Record<string, string>
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle: string
  targetHandle: string
}

export interface FlowSettings {
  concurrency: {
    mode: 'allow' | 'deny' | 'queue' | 'replace'
    scope: 'flow' | 'trigger_key'
    maxInstances: number
    scopeKey: string | null
  }
  timeout: {
    flowTimeoutMs: number
    nodeTimeoutMs: number
  }
  retry: {
    maxAttempts: number
    backoffMs: number
    backoffMultiplier: number
  }
}

// Execution types
export interface Execution {
  id: string
  flowVersionId: string
  status: ExecutionStatus
  triggerInput: Record<string, unknown>
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  triggeredBy: string
}

export type ExecutionStatus =
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled'

export interface NodeExecution {
  id: string
  executionId: string
  nodeId: string
  componentName: string
  componentVersion: string
  status: NodeStatus
  startedAt: string | null
  completedAt: string | null
  durationMs: number | null
  errorMessage: string | null
}

export type NodeStatus =
  | 'pending'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'skipped'

// Component types
export interface Component {
  id: string
  name: string
  displayName: string
  description: string
  category: string
  createdBy: string
  createdAt: string
}

export interface ComponentVersion {
  id: string
  componentId: string
  version: string
  image: string
  interfaceDef: ComponentInterface
  configSchema: Record<string, unknown>
  resources: { memory: string; cpu: string }
  status: 'active' | 'deprecated' | 'disabled'
}

export interface ComponentInterface {
  inputs: ComponentPort[]
  outputs: ComponentPort[]
}

export interface ComponentPort {
  name: string
  type: string
  required: boolean
  default?: unknown
  description: string
}

// External Service types
export interface ExternalService {
  id: string
  name: string
  displayName: string
  description: string
  baseUrl: string
  protocol: 'REST' | 'GraphQL' | 'gRPC'
  schemaUrl: string | null
  authType: string | null
  authConfig: Record<string, unknown> | null
  status: 'active' | 'inactive' | 'error'
  endpointCount: number
  createdAt: string
  updatedAt: string
}

export interface ExternalServiceDetail extends ExternalService {
  healthCheck: Record<string, unknown> | null
  endpoints: ServiceEndpoint[]
}

export interface ServiceEndpoint {
  id: string
  serviceId: string
  name: string
  description: string | null
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'
  path: string
  pathParams: JsonSchema | null
  queryParams: JsonSchema | null
  requestBody: JsonSchema | null
  responseSchema: JsonSchema | null
  tags: string[] | null
  isEnabled: boolean
  createdAt: string
  updatedAt: string
}

export interface JsonSchema {
  type?: string
  properties?: Record<string, JsonSchema>
  required?: string[]
  items?: JsonSchema
  description?: string
  default?: unknown
  enum?: unknown[]
  [key: string]: unknown
}

export interface CreateServiceRequest {
  name: string
  displayName: string
  description?: string
  baseUrl: string
  protocol?: 'REST' | 'GraphQL' | 'gRPC'
  schemaUrl?: string
  authType?: string
  authConfig?: Record<string, unknown>
  healthCheck?: Record<string, unknown>
  endpoints?: CreateEndpointRequest[]
}

export interface UpdateServiceRequest {
  displayName?: string
  description?: string
  baseUrl?: string
  schemaUrl?: string
  authType?: string
  authConfig?: Record<string, unknown>
  healthCheck?: Record<string, unknown>
  status?: 'active' | 'inactive'
}

export interface CreateEndpointRequest {
  name: string
  description?: string
  method: string
  path: string
  pathParams?: JsonSchema
  queryParams?: JsonSchema
  requestBody?: JsonSchema
  responseSchema?: JsonSchema
  tags?: string[]
}

export interface SchemaRefreshResult {
  message: string
  addedEndpoints: number
  updatedEndpoints: number
  totalEndpoints: number
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  message: string
}
