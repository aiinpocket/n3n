import { useEffect, useState, useCallback } from 'react'
import { Button, Card, Table, Space, Modal, Form, Input, Tag, Dropdown, Select, List, Tabs, Alert, Popconfirm } from 'antd'
import { message, modal } from '../utils/feedback'
import { PlusOutlined, EditOutlined, PlayCircleOutlined, DeleteOutlined, SearchOutlined, UploadOutlined, ExportOutlined, MoreOutlined, ThunderboltOutlined, BulbOutlined, ShareAltOutlined, EyeOutlined, CopyOutlined, BookOutlined, ReloadOutlined, HistoryOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useFlowListStore } from '../stores/flowListStore'
import type { Flow } from '../api/flow'
import { flowApi } from '../api/flow'
import { flowShareApi } from '../api/flowShare'
import type { FlowShare } from '../api/flowShare'
import FlowExportModal from '../components/flow/FlowExportModal'
import FlowImportModal from '../components/flow/FlowImportModal'
import FlowGeneratorModal from '../components/ai/FlowGeneratorModal'
import ShareLinkSection from '../components/flow/ShareLinkSection'
import { templateApi } from '../api/template'
import { Typography, Result } from 'antd'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'
import { isDraftVersion } from '../utils/versionLabel'

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
  const [templateModalOpen, setTemplateModalOpen] = useState(false)
  const [templateFlow, setTemplateFlow] = useState<{ id: string; name: string; version: string } | null>(null)
  const [templateForm] = Form.useForm()
  const [templateSaving, setTemplateSaving] = useState(false)
  const [shareActionLoading, setShareActionLoading] = useState(false)

  useEffect(() => {
    fetchFlows()
  }, [fetchFlows])

  const fetchSharedFlows = useCallback(async () => {
    setSharedLoading(true)
    try {
      const data = await flowShareApi.getSharedWithMe()
      setSharedFlows(data)
    } catch (err) {
      message.error(extractApiError(err, t('flow.loadFailed')))
      setSharedFlows([])
    } finally {
      setSharedLoading(false)
    }
  }, [t])

  useEffect(() => {
    if (activeTab === 'shared') {
      fetchSharedFlows()
    }
  }, [activeTab, fetchSharedFlows])

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
    modal.confirm({
      title: t('flow.deleteConfirm'),
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteFlow(id)
          message.success(t('flow.deleteSuccess'))
        } catch (error) {
          message.error(extractApiError(error, t('common.deleteFailed')))
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

  const handleSaveAsTemplate = async (values: { name: string; description: string; category: string }) => {
    if (!templateFlow) return
    setTemplateSaving(true)
    try {
      await templateApi.createFromFlow(templateFlow.id, templateFlow.version, {
        name: values.name,
        description: values.description,
        category: values.category,
      })
      message.success(t('flow.saveAsTemplateSuccess'))
      setTemplateModalOpen(false)
      templateForm.resetFields()
      setTemplateFlow(null)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('flow.saveAsTemplateFailed')))
    } finally {
      setTemplateSaving(false)
    }
  }

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) return
    modal.confirm({
      title: t('flow.batchDeleteConfirm', { count: selectedRowKeys.length }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setBatchDeleting(true)
        try {
          const resp = await flowApi.batchDelete(selectedRowKeys as string[])
          message.success(t('flow.batchDeleteSuccess', { count: resp.deleted }))
          setSelectedRowKeys([])
          fetchFlows()
        } catch (error) {
          message.error(extractApiError(error, t('common.deleteFailed')))
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
    } catch (err) {
      message.error(extractApiError(err, t('flow.loadFailed')))
      setShares([])
    } finally {
      setSharesLoading(false)
    }
  }

  const isValidEmail = (email: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)

  const handleShareFlow = async () => {
    if (!shareFlow || !shareEmail) return
    if (!isValidEmail(shareEmail)) {
      message.warning(t('share.invalidEmail'))
      return
    }
    setShareActionLoading(true)
    try {
      await flowShareApi.share(shareFlow.id, { email: shareEmail, permission: sharePermission })
      message.success(t('share.shareSuccess'))
      setShareEmail('')
      const data = await flowShareApi.getShares(shareFlow.id)
      setShares(data)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('share.shareFailed')))
    } finally {
      setShareActionLoading(false)
    }
  }

  const handleRemoveShare = async (shareId: string) => {
    if (!shareFlow) return
    setShareActionLoading(true)
    try {
      await flowShareApi.removeShare(shareFlow.id, shareId)
      message.success(t('share.removeSuccess'))
      const data = await flowShareApi.getShares(shareFlow.id)
      setShares(data)
    } catch (error) {
      message.error(extractApiError(error, t('share.removeFailed')))
    } finally {
      setShareActionLoading(false)
    }
  }

  const handleUpdateSharePermission = async (shareId: string, permission: string) => {
    if (!shareFlow) return
    try {
      await flowShareApi.updatePermission(shareFlow.id, shareId, permission)
      setShares(prev => prev.map(s => s.id === shareId ? { ...s, permission: permission as FlowShare['permission'] } : s))
      message.success(t('share.permissionUpdated'))
    } catch (error) {
      message.error(extractApiError(error, t('common.updateFailed')))
    }
  }

  const columns = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      sorter: (a: Flow, b: Flow) => a.name.localeCompare(b.name),
      render: (name: string, record: Flow) => (
        <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => navigate(`/flows/${record.id}/edit`)}>{name}</Button>
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
      sorter: (a: Flow, b: Flow) => (a.latestVersion || '').localeCompare(b.latestVersion || ''),
      render: (version: string | null) =>
        version ? (isDraftVersion(version) ? <Tag title={version}>{t('flow.draft')}</Tag> : version) : '-',
    },
    {
      title: t('flow.publishedVersion'),
      dataIndex: 'publishedVersion',
      key: 'publishedVersion',
      sorter: (a: Flow, b: Flow) => {
        const aVal = a.publishedVersion ? 1 : 0
        const bVal = b.publishedVersion ? 1 : 0
        return aVal - bVal
      },
      render: (version: string | null) =>
        version ? <Tag color="green">{version}</Tag> : <Tag>{t('common.notPublished')}</Tag>,
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      defaultSortOrder: 'descend' as const,
      sorter: (a: Flow, b: Flow) => new Date(a.updatedAt || 0).getTime() - new Date(b.updatedAt || 0).getTime(),
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
                  key: 'executions',
                  icon: <HistoryOutlined />,
                  label: t('flow.viewExecutions'),
                  onClick: () => navigate(`/executions?flowId=${record.id}`),
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
                  key: 'saveAsTemplate',
                  icon: <BookOutlined />,
                  label: t('flow.saveAsTemplate'),
                  disabled: !record.latestVersion,
                  onClick: () => {
                    setTemplateFlow({
                      id: record.id,
                      name: record.name,
                      version: record.latestVersion || '1',
                    })
                    templateForm.setFieldsValue({ name: record.name, description: record.description || '' })
                    setTemplateModalOpen(true)
                  },
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
            <Button type="link" size="small" icon={<MoreOutlined />} aria-label={t('common.actions')} />
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
      sorter: (a: FlowShare, b: FlowShare) => (a.flowName || '').localeCompare(b.flowName || ''),
      render: (name: string, record: FlowShare) => (
        <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => navigate(`/flows/${record.flowId}/edit`)}>{name || record.flowId}</Button>
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
      sorter: (a: FlowShare, b: FlowShare) => (a.sharedByName || '').localeCompare(b.sharedByName || ''),
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
      defaultSortOrder: 'descend' as const,
      sorter: (a: FlowShare, b: FlowShare) => new Date(a.sharedAt || 0).getTime() - new Date(b.sharedAt || 0).getTime(),
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

  if (!loading && flowListError) {
    return (
      <Result
        status="error"
        title={t('common.loadFailed')}
        subTitle={flowListError}
        extra={<Button type="primary" onClick={() => fetchFlows()}>{t('common.retry')}</Button>}
      />
    )
  }

  return (
    <>
      <Card
        title={t('flow.title')}
        extra={
          <Space>
            <Input.Search
              placeholder={t('flow.searchPlaceholder')}
              aria-label={t('flow.searchPlaceholder')}
              allowClear
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onSearch={handleSearch}
              style={{ width: 250 }}
              enterButton={<SearchOutlined />}
            />
            <Button icon={<ReloadOutlined />} onClick={() => fetchFlows(currentPage, pageSize, searchQuery)}>
              {t('common.refresh')}
            </Button>
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
                  scroll={{ x: 900 }}
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
                  pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => t('common.total', { count: total }) }}
                  scroll={{ x: 800 }}
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
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="name"
            label={t('flow.flowName')}
            rules={[
              { required: true, message: t('flow.flowNamePlaceholder') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('flow.flowNamePlaceholder')} maxLength={255} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('flow.flowDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('flow.flowDescriptionPlaceholder')} maxLength={5000} showCount />
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
        destroyOnClose
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
            <Button type="primary" onClick={handleShareFlow} disabled={!shareEmail} loading={shareActionLoading}>
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
                  <Popconfirm
                    key="remove"
                    title={t('share.removeConfirm')}
                    onConfirm={() => handleRemoveShare(item.id)}
                    okText={t('common.delete')}
                    cancelText={t('common.cancel')}
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      type="link"
                      size="small"
                      danger
                      disabled={shareActionLoading}
                    >
                      {t('common.delete')}
                    </Button>
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  title={item.userEmail || item.invitedEmail || item.userName}
                  description={
                    <Select
                      size="small"
                      value={item.permission}
                      onChange={(value) => handleUpdateSharePermission(item.id, value)}
                      style={{ width: 90 }}
                      options={[
                        { value: 'view', label: t('share.view') },
                        { value: 'edit', label: t('share.edit') },
                      ]}
                    />
                  }
                />
              </List.Item>
            )}
          />

          {shareFlow && <ShareLinkSection flowId={shareFlow.id} visible={shareModalOpen} />}
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
            } catch (err) {
              message.error(extractApiError(err, t('flow.createFailed')))
            }
          }
        }}
      />

      {/* Save as Template Modal */}
      <Modal
        title={t('flow.saveAsTemplate')}
        open={templateModalOpen}
        onCancel={() => {
          setTemplateModalOpen(false)
          templateForm.resetFields()
          setTemplateFlow(null)
        }}
        footer={null}
        destroyOnClose
      >
        <Form form={templateForm} layout="vertical" onFinish={handleSaveAsTemplate}>
          <Form.Item
            name="name"
            label={t('template.templateName')}
            rules={[
              { required: true, message: t('template.templateNameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('template.templateNamePlaceholder')} maxLength={255} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('flow.flowDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('template.templateDescPlaceholder')} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item
            name="category"
            label={t('template.category')}
            initialValue="automation"
          >
            <Select
              options={[
                { value: 'automation', label: t('template.categories.automation') },
                { value: 'data', label: t('template.categories.data') },
                { value: 'integration', label: t('template.categories.integration') },
                { value: 'notification', label: t('template.categories.notification') },
                { value: 'monitoring', label: t('template.categories.monitoring') },
                { value: 'ai', label: t('template.categories.ai') },
                { value: 'utility', label: t('template.categories.utility') },
              ]}
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setTemplateModalOpen(false)
                templateForm.resetFields()
                setTemplateFlow(null)
              }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={templateSaving} icon={<BookOutlined />}>
                {t('flow.saveAsTemplate')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
