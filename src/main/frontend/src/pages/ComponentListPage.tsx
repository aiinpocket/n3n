import { useEffect, useState, useCallback } from 'react'
import {
  Button,
  Card,
  Table,
  Tag,
  Space,
  Modal,
  Form,
  Input,
  Select,
  message,
  Popconfirm,
  Typography,
  Drawer,
  List,
  Descriptions,
  Tooltip,
  Alert,
  Empty,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import {
  componentApi,
  ComponentResponse,
  ComponentVersionResponse,
  CreateComponentRequest,
  CreateVersionRequest,
} from '../api/component'
import logger from '../utils/logger'
import { getLocale } from '../utils/locale'

const { Text } = Typography
const { TextArea } = Input

const statusColors: Record<string, string> = {
  active: 'success',
  deprecated: 'warning',
  disabled: 'default',
}

// Category colors for visual distinction
const categoryColors: Record<string, string> = {
  trigger: '#22C55E',
  action: '#3B82F6',
  condition: '#F59E0B',
  transform: '#14B8A6',
  output: '#EF4444',
  utility: '#8B5CF6',
}

const categories = ['trigger', 'action', 'condition', 'transform', 'output', 'utility']

// Docker image format validation
const dockerImagePattern = /^(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?::[0-9]+)?\/)?[a-z0-9]+(?:[._-][a-z0-9]+)*(?:\/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127})?(?:@sha256:[a-f0-9]{64})?$/

// Semantic versioning pattern
const semverPattern = /^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/

export default function ComponentListPage() {
  const { t } = useTranslation()
  const [components, setComponents] = useState<ComponentResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  // Create component modal
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [creating, setCreating] = useState(false)

  // Version drawer
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false)
  const [selectedComponent, setSelectedComponent] = useState<ComponentResponse | null>(null)
  const [versions, setVersions] = useState<ComponentVersionResponse[]>([])
  const [loadingVersions, setLoadingVersions] = useState(false)

  // Add version modal
  const [addVersionModalOpen, setAddVersionModalOpen] = useState(false)
  const [versionForm] = Form.useForm()
  const [addingVersion, setAddingVersion] = useState(false)

  const loadComponents = useCallback(async (page = 1, pageSize = 20) => {
    setLoading(true)
    try {
      const data = await componentApi.list(page - 1, pageSize)
      setComponents(data.content)
      setPagination({
        current: data.number + 1,
        pageSize: data.size,
        total: data.totalElements,
      })
    } catch (error) {
      logger.error('Failed to load components:', error)
      message.error(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadComponents()
  }, [loadComponents])

  const handleTableChange = (newPagination: TablePaginationConfig) => {
    loadComponents(newPagination.current, newPagination.pageSize)
  }

  const handleCreate = async (values: CreateComponentRequest) => {
    setCreating(true)
    try {
      await componentApi.create(values)
      message.success(t('common.createSuccess'))
      setCreateModalOpen(false)
      createForm.resetFields()
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('common.createFailed'))
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await componentApi.delete(id)
      message.success(t('component.deleteSuccess'))
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('common.deleteFailed'))
    }
  }

  const openVersionDrawer = async (component: ComponentResponse) => {
    setSelectedComponent(component)
    setVersionDrawerOpen(true)
    setLoadingVersions(true)
    try {
      const data = await componentApi.listVersions(component.id)
      setVersions(data)
    } catch (error) {
      logger.error('Failed to load versions:', error)
      message.error(t('component.loadVersionsFailed'))
    } finally {
      setLoadingVersions(false)
    }
  }

  const handleAddVersion = async (values: CreateVersionRequest & { interfaceDefJson: string; configSchemaJson?: string }) => {
    if (!selectedComponent) return
    setAddingVersion(true)
    try {
      let interfaceDef: Record<string, unknown>
      let configSchema: Record<string, unknown> | undefined
      try {
        interfaceDef = JSON.parse(values.interfaceDefJson)
        configSchema = values.configSchemaJson ? JSON.parse(values.configSchemaJson) : undefined
      } catch {
        message.error(t('component.jsonFormatError'))
        setAddingVersion(false)
        return
      }
      const request: CreateVersionRequest = {
        version: values.version,
        image: values.image,
        interfaceDef,
        configSchema,
      }
      await componentApi.createVersion(selectedComponent.id, request)
      message.success(t('component.versionCreated'))
      setAddVersionModalOpen(false)
      versionForm.resetFields()
      // Reload versions
      const data = await componentApi.listVersions(selectedComponent.id)
      setVersions(data)
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      if (err.message?.includes('JSON')) {
        message.error(t('component.jsonFormatError'))
      } else {
        message.error(err.response?.data?.message || err.message || t('component.versionCreateFailed'))
      }
    } finally {
      setAddingVersion(false)
    }
  }

  const handleStatusChange = async (version: ComponentVersionResponse, newStatus: string) => {
    if (!selectedComponent) return
    try {
      if (newStatus === 'active') {
        await componentApi.activateVersion(selectedComponent.id, version.version)
      } else if (newStatus === 'deprecated') {
        await componentApi.deprecateVersion(selectedComponent.id, version.version)
      } else {
        message.warning(t('component.statusWarning'))
        return
      }
      message.success(t('component.statusUpdated'))
      // Reload versions
      const data = await componentApi.listVersions(selectedComponent.id)
      setVersions(data)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('component.statusUpdateFailed'))
    }
  }

  const columns: ColumnsType<ComponentResponse> = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <Text code>{name}</Text>,
    },
    {
      title: t('component.displayName'),
      dataIndex: 'displayName',
      key: 'displayName',
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      width: 200,
      ellipsis: true,
      render: (desc: string) => (
        desc ? (
          <Tooltip title={desc} placement="topLeft">
            <span>{desc}</span>
          </Tooltip>
        ) : '-'
      ),
    },
    {
      title: t('component.category'),
      dataIndex: 'category',
      key: 'category',
      render: (category: string) => (
        <Tag
          style={{
            backgroundColor: categoryColors[category] || '#666',
            color: '#fff',
            border: 'none',
          }}
        >
          {category || '-'}
        </Tag>
      ),
    },
    {
      title: t('component.latestVersion'),
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version: string) => version || '-',
    },
    {
      title: t('component.activeVersionCount'),
      dataIndex: 'activeVersionCount',
      key: 'activeVersionCount',
      render: (count: number) => count ?? 0,
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openVersionDrawer(record)}>
            {t('component.versions')}
          </Button>
          <Popconfirm title={t('component.deleteConfirm')} onConfirm={() => handleDelete(record.id)} okText={t('common.confirm')} cancelText={t('common.cancel')}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title={t('component.title')}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadComponents(pagination.current, pagination.pageSize)}>
              {t('common.refresh')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              {t('component.newComponent')}
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={components}
          rowKey="id"
          loading={loading}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('component.noComponents')}
              />
            )
          }}
          pagination={pagination}
          onChange={handleTableChange}
        />
      </Card>

      {/* Create Component Modal */}
      <Modal title={t('component.registerComponent')} open={createModalOpen} onCancel={() => setCreateModalOpen(false)} footer={null} destroyOnClose>
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="name"
            label={t('component.componentName')}
            rules={[
              { required: true, message: t('component.componentNameRequired') },
              { pattern: /^[a-z][a-z0-9-]*$/, message: t('component.componentNamePattern') },
            ]}
          >
            <Input placeholder={t('component.componentNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="displayName" label={t('component.displayName')} rules={[{ required: true, message: t('component.displayNameRequired') }]}>
            <Input placeholder={t('component.displayNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <TextArea rows={3} placeholder={t('component.descriptionPlaceholder')} />
          </Form.Item>
          <Form.Item name="category" label={t('component.category')}>
            <Select placeholder={t('component.selectCategory')} allowClear>
              {categories.map((cat) => (
                <Select.Option key={cat} value={cat}>
                  {cat}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Version Drawer */}
      <Drawer
        title={`${selectedComponent?.displayName || ''} - ${t('component.versionManagement')}`}
        placement="right"
        width={600}
        open={versionDrawerOpen}
        onClose={() => {
          setVersionDrawerOpen(false)
          setSelectedComponent(null)
          setVersions([])
        }}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddVersionModalOpen(true)}>
            {t('component.addVersion')}
          </Button>
        }
      >
        {versions.length === 0 && !loadingVersions && (
          <Alert
            message={t('component.noVersions')}
            description={t('component.noVersionsDesc')}
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}
        <List
          loading={loadingVersions}
          dataSource={versions}
          renderItem={(version) => (
            <List.Item
              actions={[
                <Select
                  key="status"
                  value={version.status}
                  style={{ width: 110 }}
                  size="small"
                  onChange={(value) => handleStatusChange(version, value)}
                >
                  <Select.Option value="active">{t('component.statusActive')}</Select.Option>
                  <Select.Option value="deprecated">{t('component.statusDeprecated')}</Select.Option>
                </Select>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Text strong>{version.version}</Text>
                    <Tag color={statusColors[version.status]}>{version.status === 'active' ? t('component.statusActive') : version.status === 'deprecated' ? t('component.statusDeprecated') : version.status}</Tag>
                  </Space>
                }
                description={
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label={t('component.image')}>{version.image}</Descriptions.Item>
                    <Descriptions.Item label={t('common.createdAt')}>{version.createdAt ? new Date(version.createdAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
                  </Descriptions>
                }
              />
            </List.Item>
          )}
        />
      </Drawer>

      {/* Add Version Modal */}
      <Modal title={t('component.addVersion')} open={addVersionModalOpen} onCancel={() => setAddVersionModalOpen(false)} footer={null} width={600} destroyOnClose>
        <Form form={versionForm} layout="vertical" onFinish={handleAddVersion}>
          <Form.Item
            name="version"
            label={t('component.versionNumber')}
            rules={[
              { required: true, message: t('component.versionRequired') },
              {
                pattern: semverPattern,
                message: t('component.versionFormatError'),
              },
            ]}
            extra={t('component.versionFormatHint')}
          >
            <Input placeholder={t('component.versionPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="image"
            label={t('component.dockerImage')}
            rules={[
              { required: true, message: t('component.imageRequired') },
              {
                pattern: dockerImagePattern,
                message: t('component.imageFormatError'),
              },
            ]}
            extra={t('component.imageFormatHint')}
          >
            <Input placeholder={t('component.imagePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="interfaceDefJson"
            label={t('component.interfaceDef')}
            rules={[{ required: true, message: t('component.interfaceDefRequired') }]}
            extra={t('component.interfaceDefExtra')}
          >
            <TextArea
              rows={6}
              placeholder={JSON.stringify(
                {
                  inputs: [{ name: 'url', type: 'string', required: true }],
                  outputs: [{ name: 'response', type: 'object' }],
                },
                null,
                2
              )}
            />
          </Form.Item>
          <Form.Item name="configSchemaJson" label={t('component.configSchema')} extra={t('component.configSchemaExtra')}>
            <TextArea
              rows={4}
              placeholder={JSON.stringify(
                {
                  type: 'object',
                  properties: { timeout: { type: 'number', default: 30000 } },
                },
                null,
                2
              )}
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setAddVersionModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={addingVersion}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
