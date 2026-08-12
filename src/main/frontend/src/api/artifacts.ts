import apiClient from './client'

export interface ArtifactItem {
  id: string
  filename: string
  mimeType: string
  sizeBytes: number
  sourceNodeType: string | null
  flowId: string | null
  executionId: string | null
  nodeId: string | null
  createdAt: string
}

export interface ArtifactListResponse {
  items: ArtifactItem[]
  total: number
  totalSizeBytes: number
}

export const artifactApi = {
  list: async (page = 0, size = 20, type?: string): Promise<ArtifactListResponse> => {
    const params: Record<string, string | number> = { page, size }
    if (type && type !== 'all') {
      params.type = type
    }
    const { data } = await apiClient.get<ArtifactListResponse>('/artifacts', { params })
    return data
  },

  /** 以帶授權的請求下載檔案並觸發瀏覽器儲存 */
  download: async (artifact: ArtifactItem): Promise<void> => {
    const { data } = await apiClient.get<Blob>(`/artifacts/${artifact.id}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = artifact.filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  },

  /** 取得原始 Blob，供內嵌預覽（objectURL）或直接讀取文字內容 */
  previewBlob: async (id: string): Promise<Blob> => {
    const { data } = await apiClient.get<Blob>(`/artifacts/${id}/raw`, {
      responseType: 'blob',
    })
    return data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/artifacts/${id}`)
  },
}
