import React, { useEffect, useState } from 'react'
import { Table, Button, Space, Card, Typography, Tag, message, Popconfirm, Tooltip } from 'antd'
import { PlusOutlined, DeleteOutlined, KeyOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useCredentialStore } from '../stores/credentialStore'
import { Credential } from '../api/credential'
import CredentialFormModal from '../components/credentials/CredentialFormModal'

const { Title } = Typography

const CredentialListPage: React.FC = () => {
  const { t, i18n } = useTranslation()
  const {
    credentials,
    loading,
    totalElements,
    currentPage,
    fetchCredentials,
    deleteCredential,
    testCredential
  } = useCredentialStore()

  const [formVisible, setFormVisible] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)

  const getLocale = () => {
    switch (i18n.language) {
      case 'ja': return 'ja-JP'
      case 'en': return 'en-US'
      default: return 'zh-TW'
    }
  }

  useEffect(() => {
    fetchCredentials()
  }, [fetchCredentials])

  const handleDelete = async (id: string) => {
    try {
      await deleteCredential(id)
      message.success(t('credential.deleteSuccess'))
    } catch {
      message.error(t('common.deleteFailed'))
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    try {
      const result = await testCredential(id)
      if (result.success) {
        message.success(t('credential.testSuccess'))
      } else {
        message.error(t('credential.testFailed', { message: result.message || t('common.error') }))
      }
    } finally {
      setTestingId(null)
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
      http_basic: 'HTTP Basic',
      http_bearer: 'Bearer Token',
      api_key: 'API Key',
      oauth2: 'OAuth2',
      database: t('credential.typeDatabase'),
      ssh: 'SSH'
    }
    return names[type] || type
  }

  const getVisibilityTag = (visibility: string) => {
    switch (visibility) {
      case 'private':
        return <Tag>{t('credential.visibilityPrivate')}</Tag>
      case 'workspace':
        return <Tag color="blue">{t('credential.visibilityWorkspace')}</Tag>
      case 'shared':
        return <Tag color="green">{t('credential.visibilityShared')}</Tag>
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
      render: (date: string) => new Date(date).toLocaleString(getLocale())
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: Credential) => (
        <Space>
          <Tooltip title={t('credential.testConnection')}>
            <Button
              type="link"
              icon={<CheckCircleOutlined />}
              loading={testingId === record.id}
              onClick={() => handleTest(record.id)}
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
            <Button type="link" danger icon={<DeleteOutlined />} />
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

        <div style={{ marginBottom: 16, padding: 12, background: '#fffbe6', borderRadius: 4 }}>
          <ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 8 }} />
          <span style={{ color: '#666' }}>
            {t('credential.securityInfo')}
          </span>
        </div>

        <Table
          columns={columns}
          dataSource={credentials}
          rowKey="id"
          loading={loading}
          pagination={{
            current: currentPage + 1,
            total: totalElements,
            pageSize: 20,
            onChange: handlePageChange,
            showTotal: (total) => t('common.total', { count: total })
          }}
        />
      </Card>

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
