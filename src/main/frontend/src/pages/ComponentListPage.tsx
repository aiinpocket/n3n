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
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import {
  componentApi,
  ComponentResponse,
  ComponentVersionResponse,
  CreateComponentRequest,
  CreateVersionRequest,
} from '../api/component'

const { Text } = Typography
const { TextArea } = Input

const statusColors: Record<string, string> = {
  active: 'success',
  deprecated: 'warning',
  disabled: 'default',
}

const categories = ['trigger', 'action', 'condition', 'transform', 'output', 'utility']

export default function ComponentListPage() {
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
      console.error('Failed to load components:', error)
      message.error('載入元件失敗')
    } finally {
      setLoading(false)
    }
  }, [])

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
      message.success('元件建立成功')
      setCreateModalOpen(false)
      createForm.resetFields()
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '建立失敗')
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await componentApi.delete(id)
      message.success('元件刪除成功')
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '刪除失敗')
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
      console.error('Failed to load versions:', error)
      message.error('載入版本失敗')
    } finally {
      setLoadingVersions(false)
    }
  }

  const handleAddVersion = async (values: CreateVersionRequest & { interfaceDefJson: string; configSchemaJson?: string }) => {
    if (!selectedComponent) return
    setAddingVersion(true)
    try {
      const request: CreateVersionRequest = {
        version: values.version,
        image: values.image,
        interfaceDef: JSON.parse(values.interfaceDefJson),
        configSchema: values.configSchemaJson ? JSON.parse(values.configSchemaJson) : undefined,
      }
      await componentApi.createVersion(selectedComponent.id, request)
      message.success('版本建立成功')
      setAddVersionModalOpen(false)
      versionForm.resetFields()
      // Reload versions
      const data = await componentApi.listVersions(selectedComponent.id)
      setVersions(data)
      loadComponents(pagination.current, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      if (err.message?.includes('JSON')) {
        message.error('JSON 格式錯誤')
      } else {
        message.error(err.response?.data?.message || err.message || '建立版本失敗')
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
        message.warning('只能將狀態設為 active 或 deprecated')
        return
      }
      message.success('狀態更新成功')
      // Reload versions
      const data = await componentApi.listVersions(selectedComponent.id)
      setVersions(data)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '狀態更新失敗')
    }
  }

  const columns: ColumnsType<ComponentResponse> = [
    {
      title: '名稱',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <Text code>{name}</Text>,
    },
    {
      title: '顯示名稱',
      dataIndex: 'displayName',
      key: 'displayName',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 200,
      ellipsis: true,
      render: (desc: string) => desc || '-',
    },
    {
      title: '分類',
      dataIndex: 'category',
      key: 'category',
      render: (category: string) => <Tag>{category || '-'}</Tag>,
    },
    {
      title: '最新版本',
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version: string) => version || '-',
    },
    {
      title: '啟用版本數',
      dataIndex: 'activeVersionCount',
      key: 'activeVersionCount',
      render: (count: number) => count ?? 0,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openVersionDrawer(record)}>
            版本
          </Button>
          <Popconfirm title="確定要刪除此元件？" onConfirm={() => handleDelete(record.id)} okText="確定" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              刪除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title="元件列表"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadComponents(pagination.current, pagination.pageSize)}>
              重新整理
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              註冊元件
            </Button>
          </Space>
        }
      >
        <Table columns={columns} dataSource={components} rowKey="id" loading={loading} pagination={pagination} onChange={handleTableChange} />
      </Card>

      {/* Create Component Modal */}
      <Modal title="註冊新元件" open={createModalOpen} onCancel={() => setCreateModalOpen(false)} footer={null} destroyOnClose>
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="name"
            label="元件名稱"
            rules={[
              { required: true, message: '請輸入元件名稱' },
              { pattern: /^[a-z][a-z0-9-]*$/, message: '只能使用小寫字母、數字和連字號，且必須以字母開頭' },
            ]}
          >
            <Input placeholder="例如: http-request" />
          </Form.Item>
          <Form.Item name="displayName" label="顯示名稱" rules={[{ required: true, message: '請輸入顯示名稱' }]}>
            <Input placeholder="例如: HTTP 請求" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} placeholder="元件功能說明" />
          </Form.Item>
          <Form.Item name="category" label="分類">
            <Select placeholder="選擇分類" allowClear>
              {categories.map((cat) => (
                <Select.Option key={cat} value={cat}>
                  {cat}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateModalOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                建立
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Version Drawer */}
      <Drawer
        title={`${selectedComponent?.displayName || ''} - 版本管理`}
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
            新增版本
          </Button>
        }
      >
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
                  <Select.Option value="active">active</Select.Option>
                  <Select.Option value="deprecated">deprecated</Select.Option>
                </Select>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Text strong>{version.version}</Text>
                    <Tag color={statusColors[version.status]}>{version.status}</Tag>
                  </Space>
                }
                description={
                  <Descriptions size="small" column={1}>
                    <Descriptions.Item label="Image">{version.image}</Descriptions.Item>
                    <Descriptions.Item label="建立時間">{new Date(version.createdAt).toLocaleString()}</Descriptions.Item>
                  </Descriptions>
                }
              />
            </List.Item>
          )}
        />
      </Drawer>

      {/* Add Version Modal */}
      <Modal title="新增版本" open={addVersionModalOpen} onCancel={() => setAddVersionModalOpen(false)} footer={null} width={600} destroyOnClose>
        <Form form={versionForm} layout="vertical" onFinish={handleAddVersion}>
          <Form.Item name="version" label="版本號" rules={[{ required: true, message: '請輸入版本號' }]}>
            <Input placeholder="例如: 1.0.0" />
          </Form.Item>
          <Form.Item name="image" label="Docker Image" rules={[{ required: true, message: '請輸入 Docker Image' }]}>
            <Input placeholder="例如: registry.example.com/http-request:1.0.0" />
          </Form.Item>
          <Form.Item
            name="interfaceDefJson"
            label="介面定義 (JSON)"
            rules={[{ required: true, message: '請輸入介面定義' }]}
            extra="定義輸入輸出介面"
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
          <Form.Item name="configSchemaJson" label="設定 Schema (JSON)" extra="選填，定義元件設定選項">
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
              <Button onClick={() => setAddVersionModalOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit" loading={addingVersion}>
                建立
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
