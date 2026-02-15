import React, { useEffect, useState, useCallback, useMemo } from 'react'
import { Table, Button, Space, Card, Typography, Tag, message, Popconfirm, Tooltip, Empty, Alert, Badge, Modal, Form, Input, Select } from 'antd'
import { PlusOutlined, DeleteOutlined, KeyOutlined, CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, LinkOutlined, DisconnectOutlined, LoadingOutlined, EyeOutlined, EditOutlined, SearchOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useCredentialStore } from '../stores/credentialStore'
import { Credential, credentialApi } from '../api/credential'
import { oauth2Api, OAuth2Status } from '../api/oauth2'
import CredentialFormModal from '../components/credentials/CredentialFormModal'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'
import logger from '../utils/logger'

const { Title } = Typography

const CredentialListPage: React.FC = () => {
  const { t } = useTranslation()
  const {
    credentials,
    loading,
    error,
    totalElements,
    currentPage,
    fetchCredentials,
    deleteCredential,
    testCredential,
    clearError
  } = useCredentialStore()

  const [formVisible, setFormVisible] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [oauth2Statuses, setOauth2Statuses] = useState<Record<string, OAuth2Status>>({})
  const [oauth2Loading, setOauth2Loading] = useState<Record<string, boolean>>({})
  const [disconnectingId, setDisconnectingId] = useState<string | null>(null)
  const [viewDataModalOpen, setViewDataModalOpen] = useState(false)
  const [viewDataLoading, setViewDataLoading] = useState(false)
  const [viewDataContent, setViewDataContent] = useState<Record<string, unknown> | null>(null)
  const [viewDataName, setViewDataName] = useState('')
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingCredential, setEditingCredential] = useState<Credential | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editForm] = Form.useForm()
  const [searchText, setSearchText] = useState('')
  const [typeFilter, setTypeFilter] = useState<string>('all')

  useEffect(() => {
    fetchCredentials()
  }, [fetchCredentials])

  const credentialTypes = useMemo(() => {
    const types = new Set(credentials.map(c => c.type))
    return Array.from(types).sort()
  }, [credentials])

  const filteredCredentials = useMemo(() => {
    return credentials.filter(c => {
      const matchesSearch = !searchText ||
        c.name.toLowerCase().includes(searchText.toLowerCase()) ||
        (c.description && c.description.toLowerCase().includes(searchText.toLowerCase()))
      const matchesType = typeFilter === 'all' || c.type === typeFilter
      return matchesSearch && matchesType
    })
  }, [credentials, searchText, typeFilter])

  // Fetch OAuth2 status for all oauth2-type credentials
  const fetchOAuth2Statuses = useCallback(async (creds: Credential[]) => {
    const oauth2Creds = creds.filter(c => c.type === 'oauth2')
    if (oauth2Creds.length === 0) return

    const statuses: Record<string, OAuth2Status> = {}
    await Promise.all(
      oauth2Creds.map(async (cred) => {
        try {
          const status = await oauth2Api.getStatus(cred.id)
          statuses[cred.id] = status
        } catch (err) {
          logger.warn('Failed to fetch OAuth2 status for credential', cred.id, err)
          statuses[cred.id] = { connected: false }
        }
      })
    )
    setOauth2Statuses(statuses)
  }, [])

  useEffect(() => {
    if (credentials.length > 0) {
      fetchOAuth2Statuses(credentials)
    }
  }, [credentials, fetchOAuth2Statuses])

  const handleOAuth2Connect = async (credentialId: string, provider: string) => {
    setOauth2Loading(prev => ({ ...prev, [credentialId]: true }))
    try {
      const { authorizationUrl } = await oauth2Api.getAuthUrl(provider, credentialId)
      // Redirect user to the provider's consent page
      window.location.href = authorizationUrl
    } catch (err) {
      message.error(extractApiError(err, t('oauth2.connectFailed')))
      setOauth2Loading(prev => ({ ...prev, [credentialId]: false }))
    }
  }

  const handleOAuth2Disconnect = async (credentialId: string) => {
    setDisconnectingId(credentialId)
    try {
      await oauth2Api.disconnect(credentialId)
      setOauth2Statuses(prev => ({
        ...prev,
        [credentialId]: { connected: false }
      }))
      message.success(t('oauth2.disconnected'))
    } catch (err) {
      message.error(extractApiError(err, t('oauth2.disconnectFailed')))
    } finally {
      setDisconnectingId(null)
    }
  }

  const getOAuth2StatusBadge = (record: Credential) => {
    if (record.type !== 'oauth2') return null
    const status = oauth2Statuses[record.id]
    if (!status) return <Tag>{t('oauth2.checking')}</Tag>
    if (!status.connected) return <Badge status="default" text={t('oauth2.disconnectedStatus')} />
    if (status.expired) return <Badge status="error" text={t('oauth2.expired')} />
    if (status.expiringSoon) return <Badge status="warning" text={t('oauth2.expiringSoon')} />
    return <Badge status="success" text={t('oauth2.connected')} />
  }

  // Detect provider from credential name or data (heuristic)
  const detectProvider = (record: Credential): string => {
    const name = record.name.toLowerCase()
    if (name.includes('google')) return 'google'
    if (name.includes('github')) return 'github'
    if (name.includes('slack')) return 'slack'
    if (name.includes('microsoft') || name.includes('azure')) return 'microsoft'
    // Default: use the first word in name as a guess, fallback to 'google'
    return 'google'
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteCredential(id)
      message.success(t('credential.deleteSuccess'))
    } catch (error) {
      message.error(extractApiError(error, t('common.deleteFailed')))
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    try {
      const result = await testCredential(id)
      if (result.success) {
        message.success(t('credential.testSuccess'))
      } else {
        message.error(result.message || t('credential.testFailed', { message: t('common.error') }))
      }
    } catch (error) {
      message.error(extractApiError(error, t('credential.testFailed', { message: t('common.error') })))
    } finally {
      setTestingId(null)
    }
  }

  const handleViewData = async (id: string, name: string) => {
    setViewDataName(name)
    setViewDataLoading(true)
    setViewDataContent(null)
    setViewDataModalOpen(true)
    try {
      const data = await credentialApi.getData(id)
      setViewDataContent(data)
    } catch (err) {
      message.error(extractApiError(err, t('credential.viewDataFailed')))
      setViewDataModalOpen(false)
    } finally {
      setViewDataLoading(false)
    }
  }

  const handleEdit = (record: Credential) => {
    setEditingCredential(record)
    editForm.setFieldsValue({
      description: record.description || '',
      visibility: record.visibility || 'private',
    })
    setEditModalOpen(true)
  }

  const handleEditSubmit = async () => {
    try {
      const values = await editForm.validateFields()
      setEditSubmitting(true)
      await credentialApi.update(editingCredential!.id, values)
      message.success(t('common.updateSuccess'))
      setEditModalOpen(false)
      setEditingCredential(null)
      editForm.resetFields()
      fetchCredentials()
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return
      message.error(extractApiError(err, t('common.saveFailed')))
    } finally {
      setEditSubmitting(false)
    }
  }

  const handlePageChange = (page: number) => {
    fetchCredentials(page - 1)
  }

  const getTypeColor = (type: string) => {
    const colors: Record<string, string> = {
      http_basic: 'blue',
      http_bearer: 'green',
      api_key: 'orange',
      oauth2: 'purple',
      database: 'cyan',
      ssh: 'magenta'
    }
    return colors[type] || 'default'
  }

  const getTypeDisplayName = (type: string) => {
    const names: Record<string, string> = {
      http_basic: t('credential.typeHttpBasic'),
      http_bearer: t('credential.typeBearerToken'),
      api_key: t('credential.typeApiKey'),
      oauth2: t('credential.typeOAuth2'),
      database: t('credential.typeDatabase'),
      ssh: t('credential.typeSsh')
    }
    return names[type] || type
  }

  const getVisibilityTag = (visibility: string) => {
    switch (visibility) {
      case 'private':
        return <Tag>{t('credential.visibilityPrivate')}</Tag>
      case 'shared':
        return <Tag color="green">{t('credential.visibilityShared')}</Tag>
      case 'public':
        return <Tag color="blue">{t('credential.visibilityPublic')}</Tag>
      default:
        return <Tag>{visibility}</Tag>
    }
  }

  const columns = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Space>
          <KeyOutlined />
          <span>{name}</span>
        </Space>
      )
    },
    {
      title: t('credential.credentialType'),
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => (
        <Tag color={getTypeColor(type)}>{getTypeDisplayName(type)}</Tag>
      )
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: t('credential.visibility'),
      dataIndex: 'visibility',
      key: 'visibility',
      render: (visibility: string) => getVisibilityTag(visibility)
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string | null) => date ? new Date(date).toLocaleString(getLocale()) : '-'
    },
    {
      title: t('oauth2.status'),
      key: 'oauth2Status',
      render: (_: unknown, record: Credential) => getOAuth2StatusBadge(record)
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: Credential) => (
        <Space>
          {/* OAuth2 connect/disconnect buttons */}
          {record.type === 'oauth2' && (() => {
            const status = oauth2Statuses[record.id]
            if (status?.connected) {
              return (
                <Popconfirm
                  title={t('oauth2.disconnectConfirm')}
                  onConfirm={() => handleOAuth2Disconnect(record.id)}
                  okText={t('common.confirm')}
                  cancelText={t('common.cancel')}
                  okButtonProps={{ danger: true }}
                >
                  <Tooltip title={t('oauth2.disconnect')}>
                    <Button
                      type="link"
                      danger
                      icon={<DisconnectOutlined />}
                      loading={disconnectingId === record.id}
                      aria-label={t('oauth2.disconnect')}
                    />
                  </Tooltip>
                </Popconfirm>
              )
            }
            return (
              <Tooltip title={t('oauth2.connectWith', { provider: detectProvider(record) })}>
                <Button
                  type="link"
                  icon={oauth2Loading[record.id] ? <LoadingOutlined /> : <LinkOutlined />}
                  onClick={() => handleOAuth2Connect(record.id, detectProvider(record))}
                  disabled={oauth2Loading[record.id]}
                  style={{ color: 'var(--color-primary)' }}
                  aria-label={t('oauth2.connect')}
                />
              </Tooltip>
            )
          })()}
          <Tooltip title={t('common.edit')}>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
              aria-label={t('common.edit')}
            />
          </Tooltip>
          <Tooltip title={t('credential.viewData')}>
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => handleViewData(record.id, record.name)}
              aria-label={t('credential.viewData')}
            />
          </Tooltip>
          <Tooltip title={t('credential.testConnection')}>
            <Button
              type="link"
              icon={<CheckCircleOutlined />}
              loading={testingId === record.id}
              onClick={() => handleTest(record.id)}
              aria-label={t('credential.testConnection')}
            />
          </Tooltip>
          <Popconfirm
            title={t('credential.deleteConfirm')}
            description={t('credential.deleteConfirmDesc')}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />} title={t('common.delete')} aria-label={t('common.delete')} />
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={4} style={{ margin: 0 }}>
            <KeyOutlined style={{ marginRight: 8 }} />
            {t('credential.title')}
          </Title>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setFormVisible(true)}
          >
            {t('credential.newCredential')}
          </Button>
        </div>

        <Space style={{ marginBottom: 16, width: '100%' }} wrap>
          <Input
            placeholder={t('credential.searchPlaceholder')}
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            allowClear
            style={{ width: 280 }}
          />
          <Select
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ width: 180 }}
          >
            <Select.Option value="all">{t('credential.allTypes')}</Select.Option>
            {credentialTypes.map((type) => (
              <Select.Option key={type} value={type}>
                {getTypeDisplayName(type)}
              </Select.Option>
            ))}
          </Select>
        </Space>

        <div style={{ marginBottom: 16, padding: 12, background: 'rgba(245, 158, 11, 0.15)', borderRadius: 4, border: '1px solid var(--color-warning)' }}>
          <ExclamationCircleOutlined style={{ color: 'var(--color-warning)', marginRight: 8 }} />
          <span style={{ color: 'var(--color-text-secondary)' }}>
            {t('credential.securityInfo')}
          </span>
        </div>

        {error && (
          <Alert
            message={error}
            type="error"
            showIcon
            closable
            onClose={clearError}
            style={{ marginBottom: 16 }}
            action={
              <Button size="small" icon={<ReloadOutlined />} onClick={() => fetchCredentials()}>
                {t('common.retry')}
              </Button>
            }
          />
        )}

        <Table
          columns={columns}
          dataSource={filteredCredentials}
          rowKey="id"
          loading={loading}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={searchText || typeFilter !== 'all' ? t('credential.noMatchingCredentials') : t('credential.noCredentials')}
              >
                {!searchText && typeFilter === 'all' && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormVisible(true)}>
                    {t('credential.addCredential')}
                  </Button>
                )}
              </Empty>
            )
          }}
          pagination={{
            current: currentPage + 1,
            total: searchText || typeFilter !== 'all' ? filteredCredentials.length : totalElements,
            pageSize: 20,
            onChange: searchText || typeFilter !== 'all' ? undefined : handlePageChange,
            showTotal: (total) => t('common.total', { count: total })
          }}
          scroll={{ x: 900 }}
        />
      </Card>

      <Modal
        title={`${t('common.edit')}: ${editingCredential?.name}`}
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); editForm.resetFields(); }}
        onOk={handleEditSubmit}
        confirmLoading={editSubmitting}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
          <Form.Item name="visibility" label={t('credential.visibility')}>
            <Select>
              <Select.Option value="private">{t('credential.visibilityPrivate')}</Select.Option>
              <Select.Option value="shared">{t('credential.visibilityShared')}</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${t('credential.viewDataTitle')}: ${viewDataName}`}
        open={viewDataModalOpen}
        onCancel={() => { setViewDataModalOpen(false); setViewDataContent(null); }}
        footer={<Button onClick={() => { setViewDataModalOpen(false); setViewDataContent(null); }}>{t('common.close')}</Button>}
        width={600}
      >
        <Alert
          type="warning"
          showIcon
          message={t('credential.viewDataWarning')}
          style={{ marginBottom: 16 }}
        />
        {viewDataLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><LoadingOutlined style={{ fontSize: 24 }} /></div>
        ) : viewDataContent ? (
          <pre style={{
            background: 'var(--color-bg-elevated)',
            padding: 12,
            borderRadius: 6,
            fontSize: 12,
            maxHeight: 400,
            overflow: 'auto',
            fontFamily: 'monospace',
            color: 'var(--color-text-primary)',
          }}>
            {JSON.stringify(viewDataContent, null, 2)}
          </pre>
        ) : null}
      </Modal>

      <CredentialFormModal
        visible={formVisible}
        onClose={() => setFormVisible(false)}
        onSuccess={() => {
          setFormVisible(false)
          fetchCredentials()
        }}
      />
    </div>
  )
}

export default CredentialListPage
