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
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled' | 'waiting';
  triggerType: string;
  triggeredBy: string;
  triggerInput?: Record<string, unknown>;
  triggerContext?: Record<string, unknown>;
  cancelledBy?: string;
  cancelledAt?: string;
  cancelReason?: string;
  pausedAt?: string;
  waitingNodeId?: string;
  pauseReason?: string;
  resumeCondition?: string;
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
  inputData: Record<string, unknown> | null;
  outputData: Record<string, unknown> | null;
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

export interface ExecutionListParams {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
}

export const executionApi = {
  list: async (page = 0, size = 20, status?: string, search?: string): Promise<Page<ExecutionResponse>> => {
    const params: Record<string, unknown> = { page, size };
    if (status && status !== 'all') {
      params.status = status;
    }
    if (search && search.trim()) {
      params.search = search.trim();
    }
    const response = await apiClient.get<Page<ExecutionResponse>>('/executions', {
      params,
    });
    return response.data;
  },

  listByFlow: async (flowId: string, page = 0, size = 20): Promise<Page<ExecutionResponse>> => {
    const response = await apiClient.get<Page<ExecutionResponse>>(`/executions/by-flow/${flowId}`, {
      params: { page, size },
    });
    return response.data;
  },

  get: async (id: string): Promise<ExecutionResponse> => {
    const response = await apiClient.get<ExecutionResponse>(`/executions/${id}`);
    return response.data;
  },

  create: async (request: CreateExecutionRequest): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>('/executions', request);
    return response.data;
  },

  cancel: async (id: string, reason?: string): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>(`/executions/${id}/cancel`, null, {
      params: reason ? { reason } : undefined,
    });
    return response.data;
  },

  getNodeExecutions: async (executionId: string): Promise<NodeExecutionResponse[]> => {
    const response = await apiClient.get<NodeExecutionResponse[]>(
      `/executions/${executionId}/nodes`
    );
    return response.data;
  },

  getOutput: async (executionId: string): Promise<Record<string, unknown>> => {
    const response = await apiClient.get<Record<string, unknown>>(
      `/executions/${executionId}/output`
    );
    return response.data;
  },

  retry: async (id: string): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>(`/executions/${id}/retry`);
    return response.data;
  },

  getNodeData: async (executionId: string, nodeId: string): Promise<{input: Record<string, unknown>, output: Record<string, unknown>}> => {
    const response = await apiClient.get(`/executions/${executionId}/nodes/${nodeId}/data`);
    return response.data;
  },

  resume: async (id: string, resumeData?: Record<string, unknown>): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>(
      `/executions/${id}/resume`,
      resumeData || {}
    );
    return response.data;
  },

  pause: async (id: string, reason?: string): Promise<ExecutionResponse> => {
    const response = await apiClient.post<ExecutionResponse>(`/executions/${id}/pause`, null, {
      params: reason ? { reason } : undefined,
    });
    return response.data;
  },
};
