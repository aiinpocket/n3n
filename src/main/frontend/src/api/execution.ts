import apiClient from './client';

export interface CreateExecutionRequest {
  flowId: string;
  version?: number;
  input?: Record<string, unknown>;
  context?: Record<string, unknown>;
}

export interface ExecutionResponse {
  id: string;
  flowVersionId: string;
  flowName?: string;
  version?: number;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
  triggerType: string;
  triggeredBy: string;
  triggerInput?: Record<string, unknown>;
  triggerContext?: Record<string, unknown>;
  cancelledBy?: string;
  cancelledAt?: string;
  cancelReason?: string;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  createdAt: string;
}

export interface NodeExecutionResponse {
  id: string;
  executionId: string;
  nodeId: string;
  componentName: string;
  componentVersion: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  retryCount: number;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  errorMessage?: string;
  errorStack?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export const executionApi = {
  list: async (page = 0, size = 20): Promise<Page<ExecutionResponse>> => {
    const response = await apiClient.get<Page<ExecutionResponse>>('/api/executions', {
      params: { page, size },
    });
    return response.data;
  },

  listByFlow: async (flowId: string, page = 0, size = 20): Promise<Page<ExecutionResponse>> => {
    const response = await apiClient.get<Page<ExecutionResponse>>(`/api/executions/by-flow/${flowId}`, {
      params: { page, size },
    });
    return response.data;
  },

  get: async (id: string): Promise<ExecutionResponse> => {
    const response = await apiClient.get<ExecutionResponse>(`/api/executions/${id}`);
    return response.data;
  },

  create: async (request: CreateExecutionRequest): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>('/api/executions', request);
    return response.data;
  },

  cancel: async (id: string, reason?: string): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>(`/api/executions/${id}/cancel`, null, {
      params: reason ? { reason } : undefined,
    });
    return response.data;
  },

  getNodeExecutions: async (executionId: string): Promise<NodeExecutionResponse[]> => {
    const response = await apiClient.get<NodeExecutionResponse[]>(
      `/api/executions/${executionId}/nodes`
    );
    return response.data;
  },

  getOutput: async (executionId: string): Promise<Record<string, unknown>> => {
    const response = await apiClient.get<Record<string, unknown>>(
      `/api/executions/${executionId}/output`
    );
    return response.data;
  },
};
