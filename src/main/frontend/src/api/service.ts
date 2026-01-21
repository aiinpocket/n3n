import apiClient from './client'
import type {
  ExternalService,
  ExternalServiceDetail,
  ServiceEndpoint,
  CreateServiceRequest,
  UpdateServiceRequest,
  CreateEndpointRequest,
  SchemaRefreshResult,
  ConnectionTestResult,
} from '../types'

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const serviceApi = {
  listServices: async (page = 0, size = 20): Promise<PageResponse<ExternalService>> => {
    const response = await apiClient.get('/services', { params: { page, size } })
    return response.data
  },

  getService: async (id: string): Promise<ExternalServiceDetail> => {
    const response = await apiClient.get(`/services/${id}`)
    return response.data
  },

  createService: async (data: CreateServiceRequest): Promise<ExternalService> => {
    const response = await apiClient.post('/services', data)
    return response.data
  },

  updateService: async (id: string, data: UpdateServiceRequest): Promise<ExternalService> => {
    const response = await apiClient.put(`/services/${id}`, data)
    return response.data
  },

  deleteService: async (id: string): Promise<void> => {
    await apiClient.delete(`/services/${id}`)
  },

  refreshSchema: async (id: string): Promise<SchemaRefreshResult> => {
    const response = await apiClient.post(`/services/${id}/refresh-schema`)
    return response.data
  },

  getEndpoints: async (serviceId: string): Promise<ServiceEndpoint[]> => {
    const response = await apiClient.get(`/services/${serviceId}/endpoints`)
    return response.data
  },

  createEndpoint: async (serviceId: string, data: CreateEndpointRequest): Promise<ServiceEndpoint> => {
    const response = await apiClient.post(`/services/${serviceId}/endpoints`, data)
    return response.data
  },

  updateEndpoint: async (
    serviceId: string,
    endpointId: string,
    data: CreateEndpointRequest
  ): Promise<ServiceEndpoint> => {
    const response = await apiClient.put(`/services/${serviceId}/endpoints/${endpointId}`, data)
    return response.data
  },

  deleteEndpoint: async (serviceId: string, endpointId: string): Promise<void> => {
    await apiClient.delete(`/services/${serviceId}/endpoints/${endpointId}`)
  },

  testConnection: async (id: string): Promise<ConnectionTestResult> => {
    const response = await apiClient.post(`/services/${id}/test`)
    return response.data
  },
}
