import { useEffect, useState, useCallback } from 'react'
import { Button, Card, Table, Space, Modal, Form, Input, message, Tag, Dropdown, Select, List, Tabs, Alert } from 'antd'
import { PlusOutlined, EditOutlined, PlayCircleOutlined, DeleteOutlined, SearchOutlined, UploadOutlined, ExportOutlined, MoreOutlined, ThunderboltOutlined, BulbOutlined, ShareAltOutlined, EyeOutlined, CopyOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useFlowListStore } from '../stores/flowListStore'
import type { Flow, FlowShare } from '../api/flow'
import { flowShareApi } from '../api/flow'
import apiClient from '../api/client'
import FlowExportModal from '../components/flow/FlowExportModal'
import FlowImportModal from '../components/flow/FlowImportModal'
import FlowGeneratorModal from '../components/ai/FlowGeneratorModal'
import { Typography, Result } from 'antd'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Text } = Typography

export default function FlowListPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { flows, totalElements, loading, error: flowListError, currentPage, pageSize, searchQuery, fetchFlows, setSearchQuery, createFlow, deleteFlow, cloneFlow } = useFlowListStore()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [creating, setCreating] = useState(false)
  const [searchValue, setSearchValue] = useState(searchQuery)
  const [importModalOpen, setImportModalOpen] = useState(false)
  const [exportFlow, setExportFlow] = useState<{ id: string; name: string; version: string } | null>(null)
  const [aiGeneratorOpen, setAiGeneratorOpen] = useState(false)
  const [shareModalOpen, setShareModalOpen] = useState(false)
  const [shareFlow, setShareFlow] = useState<{ id: string; name: string } | null>(null)
  const [shareEmail, setShareEmail] = useState('')
  const [sharePermission, setSharePermission] = useState<'view' | 'edit'>('view')
  const [shares, setShares] = useState<FlowShare[]>([])
  const [sharesLoading, setSharesLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<string>('my')
  const [sharedFlows, setSharedFlows] = useState<FlowShare[]>([])
  const [sharedLoading, setSharedLoading] = useState(false)
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [batchDeleting, setBatchDeleting] = useState(false)

  useEffect(() => {
    fetchFlows()
  }, [fetchFlows])

  const fetchSharedFlows = useCallback(async () => {
    setSharedLoading(true)
    try {
      const data = await flowShareApi.getSharedWithMe()
      setSharedFlows(data)
    } catch {
      setSharedFlows([])
    } finally {
      setSharedLoading(false)
    }
  }, [])

  useEffect(() => {
    if (activeTab === 'shared') {
      fetchSharedFlows()
    }
  }, [activeTab, fetchSharedFlows])

  // Debounced search
  const handleSearch = useCallback((value: string) => {
    setSearchQuery(value)
    fetchFlows(0, pageSize, value)
  }, [fetchFlows, pageSize, setSearchQuery])

  const handleCreate = async (values: { name: string; description?: string }) => {
    setCreating(true)
    try {
      const flow = await createFlow(values.name, values.description)
      message.success(t('flow.createSuccess'))
      setCreateModalOpen(false)
      form.resetFields()
      navigate(`/flows/${flow.id}/edit`)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.createFailed')))
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('flow.deleteConfirm'),
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteFlow(id)
          message.success(t('flow.deleteSuccess'))
        } catch {
          message.error(t('common.deleteFailed'))
        }
      },
    })
  }

  const handleClone = async (record: Flow) => {
    try {
      const cloned = await cloneFlow(record.id)
      message.success(t('flow.cloneSuccess'))
      navigate(`/flows/${cloned.id}/edit`)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('flow.cloneFailed')))
    }
  }

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    Modal.confirm({
      title: t('flow.batchDeleteConfirm', { count: selectedRowKeys.length }),
      onOk: async () => {
        setBatchDeleting(true)
        try {
          const resp = await apiClient.delete('/flows/batch', { data: { ids: selectedRowKeys } })
          message.success(t('flow.batchDeleteSuccess', { count: resp.data.deleted }))
          setSelectedRowKeys([])
          fetchFlows()
        } catch {
          message.error(t('common.deleteFailed'))
        } finally {
          setBatchDeleting(false)
        }
      },
    })
  }

  const handleOpenShare = async (flow: Flow) => {
    setShareFlow({ id: flow.id, name: flow.name })
    setShareModalOpen(true)
    setSharesLoading(true)
    try {
      const data = await flowShareApi.getShares(flow.id)
      setShares(data)
    } catch {
      setShares([])
    } finally {
      setSharesLoading(false)
    }
  }

  const handleShareFlow = async () => {
    if (!shareFlow || !shareEmail) return
    try {
      await flowShareApi.shareFlow(shareFlow.id, { email: shareEmail, permission: sharePermission })
      message.success(t('share.shareSuccess'))
      setShareEmail('')
      const data = await flowShareApi.getShares(shareFlow.id)
      setShares(data)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('share.shareFailed')))
    }
  }

  const handleRemoveShare = async (shareId: string) => {
    if (!shareFlow) return
    try {
      await flowShareApi.removeShare(shareFlow.id, shareId)
      message.success(t('share.removeSuccess'))
      const data = await flowShareApi.getShares(shareFlow.id)
      setShares(data)
    } catch {
      message.error(t('share.removeFailed'))
    }
  }

  const columns = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Flow) => (
        <a onClick={() => navigate(`/flows/${record.id}/edit`)}>{name}</a>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('flow.latestVersion'),
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version: string | null) => version || '-',
    },
    {
      title: t('flow.publishedVersion'),
      dataIndex: 'publishedVersion',
      key: 'publishedVersion',
      render: (version: string | null) =>
        version ? <Tag color="green">{version}</Tag> : <Tag>{t('common.notPublished')}</Tag>,
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string | null) => date ? new Date(date).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 280,
      render: (_: unknown, record: Flow) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/flows/${record.id}/edit`)}
          >
            {t('common.edit')}
          </Button>
          <Button
            type="link"
            size="small"
            icon={<PlayCircleOutlined />}
            disabled={!record.publishedVersion}
            onClick={() => navigate(`/executions/new?flowId=${record.id}`)}
          >
            {t('flow.trigger')}
          </Button>
          <Dropdown
            menu={{
              items: [
                {
                  key: 'share',
                  icon: <ShareAltOutlined />,
                  label: t('share.share'),
                  onClick: () => handleOpenShare(record),
                },
                {
                  key: 'clone',
                  icon: <CopyOutlined />,
                  label: t('flow.clone'),
                  onClick: () => handleClone(record),
                },
                {
                  key: 'export',
                  icon: <ExportOutlined />,
                  label: t('flow.export'),
                  disabled: !record.latestVersion,
                  onClick: () => setExportFlow({
                    id: record.id,
                    name: record.name,
                    version: record.latestVersion || '1',
                  }),
                },
                {
                  type: 'divider',
                },
                {
                  key: 'delete',
                  icon: <DeleteOutlined />,
                  label: t('common.delete'),
                  danger: true,
                  onClick: () => handleDelete(record.id),
                },
              ],
            }}
          >
            <Button type="link" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      ),
    },
  ]

  const sharedColumns = [
    {
      title: t('common.name'),
      dataIndex: 'flowName',
      key: 'flowName',
      render: (name: string, record: FlowShare) => (
        <a onClick={() => navigate(`/flows/${record.flowId}/edit`)}>{name || record.flowId}</a>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'flowDescription',
      key: 'flowDescription',
      ellipsis: true,
      render: (desc: string) => desc || '-',
    },
    {
      title: t('flow.owner'),
      dataIndex: 'sharedByName',
      key: 'sharedByName',
      render: (name: string) => name || '-',
    },
    {
      title: t('flow.permission'),
      dataIndex: 'permission',
      key: 'permission',
      render: (permission: string) => (
        <Tag color={permission === 'edit' ? 'blue' : permission === 'admin' ? 'purple' : 'default'}>
          {t(`share.${permission}`)}
        </Tag>
      ),
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'sharedAt',
      key: 'sharedAt',
      render: (date: string) => date ? new Date(date).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 160,
      render: (_: unknown, record: FlowShare) => (
        <Space>
          {record.permission === 'view' ? (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/flows/${record.flowId}/edit`)}
            >
              {t('share.view')}
            </Button>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => navigate(`/flows/${record.flowId}/edit`)}
            >
              {t('common.edit')}
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title={t('flow.title')}
        extra={
          <Space>
            <Input.Search
              placeholder={t('flow.searchPlaceholder')}
              allowClear
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onSearch={handleSearch}
              style={{ width: 250 }}
              enterButton={<SearchOutlined />}
            />
            <Button
              icon={<UploadOutlined />}
              onClick={() => setImportModalOpen(true)}
            >
              {t('flow.import')}
            </Button>
            <Button
              icon={<ThunderboltOutlined />}
              onClick={() => setAiGeneratorOpen(true)}
              style={{ background: 'var(--color-ai)', borderColor: 'var(--color-ai)', color: '#fff' }}
            >
              {t('flow.aiCreate')}
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              {t('flow.newFlow')}
            </Button>
          </Space>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'my',
              label: t('flow.myFlows'),
              children: (
                <>
                {selectedRowKeys.length > 0 && (
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={
                      <Space>
                        <span>{t('flow.selectedCount', { count: selectedRowKeys.length })}</span>
                        <Button size="small" danger icon={<DeleteOutlined />} loading={batchDeleting} onClick={handleBatchDelete}>
                          {t('flow.batchDelete')}
                        </Button>
                        <Button size="small" onClick={() => setSelectedRowKeys([])}>
                          {t('flow.clearSelection')}
                        </Button>
                      </Space>
                    }
                  />
                )}
                {flowListError && (
                  <Alert type="error" message={flowListError} closable showIcon style={{ marginBottom: 16 }} />
                )}
                <Table
                  columns={columns}
                  dataSource={flows}
                  rowKey="id"
                  loading={loading}
                  rowSelection={{
                    selectedRowKeys,
                    onChange: (keys) => setSelectedRowKeys(keys),
                  }}
                  pagination={{
                    current: currentPage + 1,
                    pageSize,
                    total: totalElements,
                    showSizeChanger: true,
                    showTotal: (total) => t('common.total', { count: total }),
                    onChange: (page, size) => fetchFlows(page - 1, size, searchQuery),
                  }}
                  locale={{
                    emptyText: (
                      <Result
                        icon={<BulbOutlined style={{ color: 'var(--color-ai)' }} />}
                        title={t('flow.emptyTitle')}
                        subTitle={t('flow.emptySubtitle')}
                        extra={
                          <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 400 }}>
                            <Button
                              type="primary"
                              size="large"
                              icon={<ThunderboltOutlined />}
                              onClick={() => setAiGeneratorOpen(true)}
                              style={{ width: '100%', background: 'var(--color-ai)', borderColor: 'var(--color-ai)' }}
                            >
                              {t('flow.aiCreateRecommended')}
                            </Button>
                            <Text type="secondary">
                              {t('flow.aiCreateHint')}
                            </Text>
                            <Button
                              size="large"
                              icon={<PlusOutlined />}
                              onClick={() => setCreateModalOpen(true)}
                              style={{ width: '100%' }}
                            >
                              {t('flow.createBlank')}
                            </Button>
                            <Button
                              icon={<UploadOutlined />}
                              onClick={() => setImportModalOpen(true)}
                              style={{ width: '100%' }}
                            >
                              {t('flow.importExisting')}
                            </Button>
                          </Space>
                        }
                      />
                    ),
                  }}
                />
                </>
              ),
            },
            {
              key: 'shared',
              label: t('flow.sharedWithMe'),
              children: (
                <Table
                  columns={sharedColumns}
                  dataSource={sharedFlows}
                  rowKey="id"
                  loading={sharedLoading}
                  locale={{
                    emptyText: t('share.noShares'),
                  }}
                />
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={t('flow.newFlow')}
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="name"
            label={t('flow.flowName')}
            rules={[{ required: true, message: t('flow.flowNamePlaceholder') }]}
          >
            <Input placeholder={t('flow.flowNamePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('flow.flowDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('flow.flowDescriptionPlaceholder')} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setCreateModalOpen(false)
                form.resetFields()
              }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Import Modal */}
      <FlowImportModal
        visible={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={() => fetchFlows()}
      />

      {/* Export Modal */}
      {exportFlow && (
        <FlowExportModal
          visible={!!exportFlow}
          flowId={exportFlow.id}
          flowName={exportFlow.name}
          version={exportFlow.version}
          onClose={() => setExportFlow(null)}
        />
      )}

      {/* Share Modal */}
      <Modal
        title={`${t('share.share')}: ${shareFlow?.name || ''}`}
        open={shareModalOpen}
        onCancel={() => { setShareModalOpen(false); setShareFlow(null); setShares([]) }}
        footer={null}
        width={500}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder={t('share.emailPlaceholder')}
              value={shareEmail}
              onChange={(e) => setShareEmail(e.target.value)}
              style={{ flex: 1 }}
            />
            <Select
              value={sharePermission}
              onChange={setSharePermission}
              style={{ width: 100 }}
              options={[
                { value: 'view', label: t('share.view') },
                { value: 'edit', label: t('share.edit') },
              ]}
            />
            <Button type="primary" onClick={handleShareFlow} disabled={!shareEmail}>
              {t('share.invite')}
            </Button>
          </Space.Compact>

          <List
            size="small"
            loading={sharesLoading}
            dataSource={shares}
            locale={{ emptyText: t('share.noShares') }}
            renderItem={(item: FlowShare) => (
              <List.Item
                actions={[
                  <Button
                    type="link"
                    size="small"
                    danger
                    onClick={() => handleRemoveShare(item.id)}
                  >
                    {t('common.delete')}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={item.userEmail || item.invitedEmail || item.userName}
                  description={
                    <Tag color={item.permission === 'edit' ? 'blue' : 'default'}>
                      {t(`share.${item.permission}`)}
                    </Tag>
                  }
                />
              </List.Item>
            )}
          />
        </Space>
      </Modal>

      {/* AI Flow Generator Modal */}
      <FlowGeneratorModal
        open={aiGeneratorOpen}
        onClose={() => setAiGeneratorOpen(false)}
        onCreateFlow={async (flowDef) => {
          if (flowDef) {
            // First create a flow, then navigate to editor with the generated content
            try {
              const flow = await createFlow(t('flow.aiGeneratedName'), t('flow.aiGeneratedDescription'))
              message.success(t('flow.createdRedirecting'))
              // Navigate to editor and let it handle the flow definition
              navigate(`/flows/${flow.id}/edit`, {
                state: { generatedFlow: flowDef },
              })
            } catch {
              message.error(t('flow.createFailed'))
            }
          }
        }}
      />
    </>
  )
}
