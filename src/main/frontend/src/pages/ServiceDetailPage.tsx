import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Descriptions,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  message,
  Popconfirm,
  Spin,
  Typography,
  Tooltip,
  Collapse,
} from 'antd'
import {
  ArrowLeftOutlined,
  EditOutlined,
  DeleteOutlined,
  PlusOutlined,
  SyncOutlined,
  ThunderboltOutlined,
  ApiOutlined,
} from '@ant-design/icons'
import { useServiceStore } from '../stores/serviceStore'
import type { ServiceEndpoint, CreateEndpointRequest } from '../types'

const { TextArea } = Input
const { Option } = Select
const { Text } = Typography

export default function ServiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [endpointModalOpen, setEndpointModalOpen] = useState(false)
  const [editingEndpoint, setEditingEndpoint] = useState<ServiceEndpoint | null>(null)
  const [form] = Form.useForm()
  const [refreshing, setRefreshing] = useState(false)
  const [testing, setTesting] = useState(false)

  const {
    currentService,
    isLoading,
    fetchService,
    refreshSchema,
    testConnection,
    createEndpoint,
    updateEndpoint,
    deleteEndpoint,
    clearCurrentService,
  } = useServiceStore()

  useEffect(() => {
    if (id) {
      fetchService(id)
    }
    return () => clearCurrentService()
  }, [id, fetchService, clearCurrentService])

  const handleRefreshSchema = async () => {
    if (!id) return
    setRefreshing(true)
    try {
      const result = await refreshSchema(id)
      message.success(`已更新: 新增 ${result.addedEndpoints} 個, 更新 ${result.updatedEndpoints} 個端點`)
    } catch {
      message.error('Schema 更新失敗')
    } finally {
      setRefreshing(false)
    }
  }

  const handleTestConnection = async () => {
    if (!id) return
    setTesting(true)
    try {
      const result = await testConnection(id)
      if (result.success) {
        message.success(`連線成功 (${result.latencyMs}ms)`)
      } else {
        message.error(result.message)
      }
    } finally {
      setTesting(false)
    }
  }

  const handleEndpointSubmit = async (values: Record<string, unknown>) => {
    if (!id) return
    try {
      const data: CreateEndpointRequest = {
        name: values.name as string,
        description: values.description as string,
        method: values.method as string,
        path: values.path as string,
        pathParams: values.pathParams ? JSON.parse(values.pathParams as string) : undefined,
        queryParams: values.queryParams ? JSON.parse(values.queryParams as string) : undefined,
        requestBody: values.requestBody ? JSON.parse(values.requestBody as string) : undefined,
        responseSchema: values.responseSchema ? JSON.parse(values.responseSchema as string) : undefined,
        tags: values.tags ? (values.tags as string).split(',').map((t) => t.trim()) : undefined,
      }

      if (editingEndpoint) {
        await updateEndpoint(id, editingEndpoint.id, data)
        message.success('端點已更新')
      } else {
        await createEndpoint(id, data)
        message.success('端點已建立')
      }
      setEndpointModalOpen(false)
      form.resetFields()
      setEditingEndpoint(null)
    } catch (error: unknown) {
      const err = error as { message?: string }
      message.error(err.message || '操作失敗')
    }
  }

  const handleEditEndpoint = (endpoint: ServiceEndpoint) => {
    setEditingEndpoint(endpoint)
    form.setFieldsValue({
      name: endpoint.name,
      description: endpoint.description,
      method: endpoint.method,
      path: endpoint.path,
      pathParams: endpoint.pathParams ? JSON.stringify(endpoint.pathParams, null, 2) : '',
      queryParams: endpoint.queryParams ? JSON.stringify(endpoint.queryParams, null, 2) : '',
      requestBody: endpoint.requestBody ? JSON.stringify(endpoint.requestBody, null, 2) : '',
      responseSchema: endpoint.responseSchema ? JSON.stringify(endpoint.responseSchema, null, 2) : '',
      tags: endpoint.tags?.join(', ') || '',
    })
    setEndpointModalOpen(true)
  }

  const handleDeleteEndpoint = async (endpointId: string) => {
    if (!id) return
    try {
      await deleteEndpoint(id, endpointId)
      message.success('端點已刪除')
    } catch {
      message.error('刪除失敗')
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

  const columns = [
    {
      title: '方法',
      dataIndex: 'method',
      key: 'method',
      width: 80,
      render: (method: string) => <Tag color={getMethodColor(method)}>{method}</Tag>,
    },
    {
      title: '路徑',
      dataIndex: 'path',
      key: 'path',
      render: (path: string) => <code>{path}</code>,
    },
    {
      title: '名稱',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '標籤',
      dataIndex: 'tags',
      key: 'tags',
      render: (tags: string[] | null) =>
        tags?.map((tag) => <Tag key={tag}>{tag}</Tag>) || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: unknown, record: ServiceEndpoint) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEditEndpoint(record)}
          >
            編輯
          </Button>
          <Popconfirm
            title="確定要刪除此端點？"
            onConfirm={() => handleDeleteEndpoint(record.id)}
            okText="確定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              刪除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (isLoading || !currentService) {
    return (
      <div style={{ textAlign: 'center', padding: 50 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <>
      <Card
        title={
          <Space>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/services')}
            />
            <ApiOutlined />
            {currentService.displayName}
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<ThunderboltOutlined />}
              loading={testing}
              onClick={handleTestConnection}
            >
              測試連線
            </Button>
            {currentService.schemaUrl && (
              <Tooltip title="重新解析 OpenAPI 文檔">
                <Button
                  icon={<SyncOutlined spin={refreshing} />}
                  loading={refreshing}
                  onClick={handleRefreshSchema}
                >
                  更新 Schema
                </Button>
              </Tooltip>
            )}
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => navigate(`/services/${id}/edit`)}
            >
              編輯服務
            </Button>
          </Space>
        }
      >
        <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
          <Descriptions.Item label="識別名稱">
            <code>{currentService.name}</code>
          </Descriptions.Item>
          <Descriptions.Item label="協議">
            <Tag color="blue">{currentService.protocol}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="服務地址" span={2}>
            <Text copyable>{currentService.baseUrl}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="Schema URL">
            {currentService.schemaUrl || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="認證類型">
            {currentService.authType || '無'}
          </Descriptions.Item>
          <Descriptions.Item label="狀態">
            <Tag color={currentService.status === 'active' ? 'green' : 'red'}>
              {currentService.status === 'active' ? '運作中' : currentService.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="端點數量">
            {currentService.endpoints.length} 個
          </Descriptions.Item>
          {currentService.description && (
            <Descriptions.Item label="描述" span={2}>
              {currentService.description}
            </Descriptions.Item>
          )}
        </Descriptions>

        <Card
          title="API 端點"
          size="small"
          extra={
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingEndpoint(null)
                form.resetFields()
                setEndpointModalOpen(true)
              }}
            >
              手動新增端點
            </Button>
          }
        >
          <Table
            columns={columns}
            dataSource={currentService.endpoints}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            size="small"
          />
        </Card>
      </Card>

      <Modal
        title={editingEndpoint ? '編輯端點' : '新增端點'}
        open={endpointModalOpen}
        onCancel={() => {
          setEndpointModalOpen(false)
          setEditingEndpoint(null)
          form.resetFields()
        }}
        footer={null}
        width={700}
      >
        <Form form={form} layout="vertical" onFinish={handleEndpointSubmit}>
          <Form.Item
            name="name"
            label="端點名稱"
            rules={[{ required: true, message: '請輸入端點名稱' }]}
          >
            <Input placeholder="例如: createUser" />
          </Form.Item>

          <Form.Item name="description" label="描述">
            <Input placeholder="描述此端點的用途" />
          </Form.Item>

          <Space style={{ width: '100%' }} align="start">
            <Form.Item
              name="method"
              label="HTTP 方法"
              rules={[{ required: true }]}
              style={{ width: 150 }}
            >
              <Select>
                <Option value="GET">GET</Option>
                <Option value="POST">POST</Option>
                <Option value="PUT">PUT</Option>
                <Option value="PATCH">PATCH</Option>
                <Option value="DELETE">DELETE</Option>
              </Select>
            </Form.Item>

            <Form.Item
              name="path"
              label="路徑"
              rules={[{ required: true, message: '請輸入路徑' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="例如: /users/{userId}" />
            </Form.Item>
          </Space>

          <Form.Item name="tags" label="標籤 (逗號分隔)">
            <Input placeholder="例如: users, auth" />
          </Form.Item>

          <Collapse
            items={[
              {
                key: 'schema',
                label: 'Schema 定義（JSON Schema 格式）',
                children: (
                  <>
                    <Form.Item name="pathParams" label="路徑參數">
                      <TextArea rows={3} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="queryParams" label="查詢參數">
                      <TextArea rows={3} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="requestBody" label="請求體">
                      <TextArea rows={4} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="responseSchema" label="響應格式">
                      <TextArea rows={4} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                  </>
                ),
              },
            ]}
            style={{ marginBottom: 24 }}
          />

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setEndpointModalOpen(false)
                  setEditingEndpoint(null)
                  form.resetFields()
                }}
              >
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                {editingEndpoint ? '更新' : '建立'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
