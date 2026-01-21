import { useEffect, useState } from 'react'
import { Drawer, Input, Tree, Tag, Empty, Spin, Space, Typography, Tooltip } from 'antd'
import { ApiOutlined, SearchOutlined } from '@ant-design/icons'
import type { DataNode } from 'antd/es/tree'
import { serviceApi } from '../../api/service'
import type { ExternalService, ServiceEndpoint } from '../../types'

const { Text } = Typography

interface ServiceNodePanelProps {
  open: boolean
  onClose: () => void
  onSelectEndpoint: (service: ExternalService, endpoint: ServiceEndpoint) => void
}

export default function ServiceNodePanel({ open, onClose, onSelectEndpoint }: ServiceNodePanelProps) {
  const [services, setServices] = useState<ExternalService[]>([])
  const [endpoints, setEndpoints] = useState<Record<string, ServiceEndpoint[]>>({})
  const [loading, setLoading] = useState(false)
  const [searchValue, setSearchValue] = useState('')
  const [expandedKeys, setExpandedKeys] = useState<string[]>([])

  useEffect(() => {
    if (open) {
      loadServices()
    }
  }, [open])

  const loadServices = async () => {
    setLoading(true)
    try {
      const response = await serviceApi.listServices(0, 100)
      setServices(response.content)

      // Load endpoints for each service
      const endpointsMap: Record<string, ServiceEndpoint[]> = {}
      for (const service of response.content) {
        try {
          const eps = await serviceApi.getEndpoints(service.id)
          endpointsMap[service.id] = eps
        } catch {
          endpointsMap[service.id] = []
        }
      }
      setEndpoints(endpointsMap)
    } catch (error) {
      console.error('Failed to load services:', error)
    } finally {
      setLoading(false)
    }
  }

  const getMethodColor = (method: string) => {
    const colors: Record<string, string> = {
      GET: 'green',
      POST: 'blue',
      PUT: 'orange',
      PATCH: 'cyan',
      DELETE: 'red',
    }
    return colors[method] || 'default'
  }

  const filterEndpoints = (eps: ServiceEndpoint[]) => {
    if (!searchValue) return eps
    const search = searchValue.toLowerCase()
    return eps.filter(
      (ep) =>
        ep.name.toLowerCase().includes(search) ||
        ep.path.toLowerCase().includes(search) ||
        ep.description?.toLowerCase().includes(search)
    )
  }

  const treeData: DataNode[] = services
    .filter((service) => {
      if (!searchValue) return true
      const search = searchValue.toLowerCase()
      const serviceMatch =
        service.name.toLowerCase().includes(search) ||
        service.displayName.toLowerCase().includes(search)
      const endpointMatch = filterEndpoints(endpoints[service.id] || []).length > 0
      return serviceMatch || endpointMatch
    })
    .map((service) => ({
      key: service.id,
      title: (
        <Space>
          <ApiOutlined />
          <Text strong>{service.displayName}</Text>
          <Tag color={service.status === 'active' ? 'green' : 'red'} style={{ marginLeft: 8 }}>
            {service.protocol}
          </Tag>
        </Space>
      ),
      children: filterEndpoints(endpoints[service.id] || []).map((endpoint) => ({
        key: `${service.id}:${endpoint.id}`,
        title: (
          <Tooltip title={endpoint.description || endpoint.path}>
            <div
              style={{ cursor: 'pointer' }}
              onClick={() => onSelectEndpoint(service, endpoint)}
            >
              <Tag color={getMethodColor(endpoint.method)} style={{ marginRight: 8 }}>
                {endpoint.method}
              </Tag>
              <Text>{endpoint.name}</Text>
              <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                {endpoint.path}
              </Text>
            </div>
          </Tooltip>
        ),
        isLeaf: true,
      })),
    }))

  return (
    <Drawer
      title={
        <Space>
          <ApiOutlined />
          選擇外部服務端點
        </Space>
      }
      placement="left"
      width={400}
      open={open}
      onClose={onClose}
    >
      <Input
        placeholder="搜尋服務或端點..."
        prefix={<SearchOutlined />}
        value={searchValue}
        onChange={(e) => setSearchValue(e.target.value)}
        allowClear
        style={{ marginBottom: 16 }}
      />

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : services.length === 0 ? (
        <Empty description="尚無已註冊的外部服務" />
      ) : (
        <Tree
          treeData={treeData}
          expandedKeys={expandedKeys}
          onExpand={(keys) => setExpandedKeys(keys as string[])}
          defaultExpandAll={searchValue.length > 0}
          showLine
        />
      )}

      <div style={{ marginTop: 16, padding: 12, background: '#f5f5f5', borderRadius: 8 }}>
        <Text type="secondary">
          點擊端點即可將其加入流程中作為節點使用。
        </Text>
      </div>
    </Drawer>
  )
}
