import apiClient from './client';

export interface ComponentResponse {
  id: string;
  name: string;
  displayName: string;
  description: string;
  category: string;
  iconUrl?: string;
  latestVersion?: string;
  activeVersionCount?: number;
  createdAt: string;
  createdBy: string;
}

export interface ComponentVersionResponse {
  id: string;
  componentId: string;
  version: string;
  image: string;
  interfaceDef: Record<string, unknown>;
  configSchema?: Record<string, unknown>;
  resources: Record<string, unknown>;
  healthCheck?: Record<string, unknown>;
  status: 'active' | 'deprecated' | 'disabled';
  createdAt: string;
  createdBy: string;
}

export interface CreateComponentRequest {
  name: string;
  displayName: string;
  description?: string;
  category?: string;
  iconUrl?: string;
}

export interface UpdateComponentRequest {
  displayName?: string;
  description?: string;
  category?: string;
  iconUrl?: string;
}

export interface CreateVersionRequest {
  version: string;
  image: string;
  interfaceDef: Record<string, unknown>;
  configSchema?: Record<string, unknown>;
  resources?: Record<string, unknown>;
  healthCheck?: Record<string, unknown>;
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

export const componentApi = {
  list: async (page = 0, size = 20): Promise<Page<ComponentResponse>> => {
    const response = await apiClient.get<Page<ComponentResponse>>('/components', {
      params: { page, size },
    });
    return response.data;
  },

  get: async (id: string): Promise<ComponentResponse> => {
    const response = await apiClient.get<ComponentResponse>(`/components/${id}`);
    return response.data;
  },

  create: async (request: CreateComponentRequest): Promise<ComponentResponse> => {
    const response = await apiClient.post<ComponentResponse>('/components', request);
    return response.data;
  },

  update: async (id: string, request: UpdateComponentRequest): Promise<ComponentResponse> => {
    const response = await apiClient.put<ComponentResponse>(`/components/${id}`, request);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/components/${id}`);
  },

  // Version APIs
  listVersions: async (componentId: string): Promise<ComponentVersionResponse[]> => {
    const response = await apiClient.get<ComponentVersionResponse[]>(
      `/components/${componentId}/versions`
    );
    return response.data;
  },

  createVersion: async (
    componentId: string,
    request: CreateVersionRequest
  ): Promise<ComponentVersionResponse> => {
    const response = await apiClient.post<ComponentVersionResponse>(
      `/components/${componentId}/versions`,
      request
    );
    return response.data;
  },

  activateVersion: async (
    componentId: string,
    version: string
  ): Promise<ComponentVersionResponse> => {
    const response = await apiClient.post<ComponentVersionResponse>(
      `/components/${componentId}/versions/${version}/activate`
    );
    return response.data;
  },

  deprecateVersion: async (
    componentId: string,
    version: string
  ): Promise<ComponentVersionResponse> => {
    const response = await apiClient.post<ComponentVersionResponse>(
      `/components/${componentId}/versions/${version}/deprecate`
    );
    return response.data;
  },
};
