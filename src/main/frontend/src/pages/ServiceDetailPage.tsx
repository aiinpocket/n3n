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
import { useTranslation } from 'react-i18next'
import { useServiceStore } from '../stores/serviceStore'
import type { ServiceEndpoint, CreateEndpointRequest } from '../types'

const { TextArea } = Input
const { Option } = Select
const { Text } = Typography

export default function ServiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
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
      message.success(t('service.schemaUpdated', { added: result.addedEndpoints, updated: result.updatedEndpoints }))
    } catch {
      message.error(t('service.schemaUpdateFailed'))
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
        message.success(t('service.connectionSuccess', { latency: result.latencyMs }))
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
        tags: values.tags ? (values.tags as string).split(',').map((tag) => tag.trim()) : undefined,
      }

      if (editingEndpoint) {
        await updateEndpoint(id, editingEndpoint.id, data)
        message.success(t('service.endpointUpdated'))
      } else {
        await createEndpoint(id, data)
        message.success(t('service.endpointCreated'))
      }
      setEndpointModalOpen(false)
      form.resetFields()
      setEditingEndpoint(null)
    } catch (error: unknown) {
      const err = error as { message?: string }
      message.error(err.message || t('common.error'))
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
      message.success(t('service.endpointDeleted'))
    } catch {
      message.error(t('common.deleteFailed'))
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
      title: t('service.method'),
      dataIndex: 'method',
      key: 'method',
      width: 80,
      render: (method: string) => <Tag color={getMethodColor(method)}>{method}</Tag>,
    },
    {
      title: t('service.path'),
      dataIndex: 'path',
      key: 'path',
      render: (path: string) => <code>{path}</code>,
    },
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('service.tags'),
      dataIndex: 'tags',
      key: 'tags',
      render: (tags: string[] | null) =>
        tags?.map((tag) => <Tag key={tag}>{tag}</Tag>) || '-',
    },
    {
      title: t('common.actions'),
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
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('service.deleteEndpointConfirm')}
            onConfirm={() => handleDeleteEndpoint(record.id)}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              {t('common.delete')}
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
              {t('service.testConnection')}
            </Button>
            {currentService.schemaUrl && (
              <Tooltip title={t('service.refreshSchemaTooltip')}>
                <Button
                  icon={<SyncOutlined spin={refreshing} />}
                  loading={refreshing}
                  onClick={handleRefreshSchema}
                >
                  {t('service.refreshSchema')}
                </Button>
              </Tooltip>
            )}
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => navigate(`/services/${id}/edit`)}
            >
              {t('service.editService')}
            </Button>
          </Space>
        }
      >
        <Descriptions bordered column={2} style={{ marginBottom: 24 }}>
          <Descriptions.Item label={t('service.identifierName')}>
            <code>{currentService.name}</code>
          </Descriptions.Item>
          <Descriptions.Item label={t('service.protocol')}>
            <Tag color="blue">{currentService.protocol}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('service.baseUrl')} span={2}>
            <Text copyable>{currentService.baseUrl}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="Schema URL">
            {currentService.schemaUrl || '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('service.authType')}>
            {currentService.authType || t('service.noAuth')}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.status')}>
            <Tag color={currentService.status === 'active' ? 'green' : 'red'}>
              {currentService.status === 'active' ? t('service.statusActive') : currentService.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('service.endpointCount')}>
            {currentService.endpoints.length}
          </Descriptions.Item>
          {currentService.description && (
            <Descriptions.Item label={t('common.description')} span={2}>
              {currentService.description}
            </Descriptions.Item>
          )}
        </Descriptions>

        <Card
          title={t('service.apiEndpoints')}
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
              {t('service.addEndpoint')}
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
        title={editingEndpoint ? t('service.editEndpoint') : t('service.addEndpoint')}
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
            label={t('service.endpointName')}
            rules={[{ required: true, message: t('service.endpointNameRequired') }]}
          >
            <Input placeholder={t('service.endpointNamePlaceholder')} />
          </Form.Item>

          <Form.Item name="description" label={t('common.description')}>
            <Input placeholder={t('service.endpointDescPlaceholder')} />
          </Form.Item>

          <Space style={{ width: '100%' }} align="start">
            <Form.Item
              name="method"
              label={t('service.httpMethod')}
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
              label={t('service.path')}
              rules={[{ required: true, message: t('service.pathRequired') }]}
              style={{ flex: 1 }}
            >
              <Input placeholder={t('service.pathPlaceholder')} />
            </Form.Item>
          </Space>

          <Form.Item name="tags" label={t('service.tagsComma')}>
            <Input placeholder={t('service.tagsPlaceholder')} />
          </Form.Item>

          <Collapse
            items={[
              {
                key: 'schema',
                label: t('service.schemaDef'),
                children: (
                  <>
                    <Form.Item name="pathParams" label={t('service.pathParams')}>
                      <TextArea rows={3} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="queryParams" label={t('service.queryParams')}>
                      <TextArea rows={3} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="requestBody" label={t('service.requestBody')}>
                      <TextArea rows={4} placeholder='{"type": "object", "properties": {...}}' />
                    </Form.Item>
                    <Form.Item name="responseSchema" label={t('service.responseSchema')}>
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
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit">
                {editingEndpoint ? t('common.save') : t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
