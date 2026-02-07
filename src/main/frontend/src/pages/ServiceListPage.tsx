import { useEffect, useState } from 'react'
import { Button, Card, Table, Space, Tag, Popconfirm, message, Tooltip, Alert } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, ApiOutlined, CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useServiceStore } from '../stores/serviceStore'
import type { ExternalService } from '../types'
import { getLocale } from '../utils/locale'

export default function ServiceListPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { services, totalElements, isLoading, error, currentPage, pageSize, fetchServices, deleteService, testConnection, clearError } = useServiceStore()
  const [testingId, setTestingId] = useState<string | null>(null)

  useEffect(() => {
    fetchServices()
  }, [fetchServices])

  const handleDelete = async (id: string) => {
    try {
      await deleteService(id)
      message.success(t('service.deleteSuccess'))
    } catch {
      message.error(t('common.deleteFailed'))
    }
  }

  const handleTestConnection = async (id: string) => {
    setTestingId(id)
    try {
      const result = await testConnection(id)
      if (result.success) {
        message.success(t('service.connectionSuccess', { latency: result.latencyMs }))
      } else {
        message.warning(result.message)
      }
    } finally {
      setTestingId(null)
    }
  }

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'active':
        return <Tag color="green">{t('service.statusActive')}</Tag>
      case 'inactive':
        return <Tag color="default">{t('service.statusInactive')}</Tag>
      case 'error':
        return <Tag color="red">{t('service.statusError')}</Tag>
      default:
        return <Tag>{status}</Tag>
    }
  }

  const getProtocolTag = (protocol: string) => {
    const colors: Record<string, string> = {
      REST: 'blue',
      GraphQL: 'purple',
      gRPC: 'orange',
    }
    return <Tag color={colors[protocol] || 'default'}>{protocol}</Tag>
  }

  const columns = [
    {
      title: t('service.serviceName'),
      dataIndex: 'displayName',
      key: 'displayName',
      render: (name: string, record: ExternalService) => (
        <a onClick={() => navigate(`/services/${record.id}`)}>{name}</a>
      ),
    },
    {
      title: t('service.identifierName'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <code>{name}</code>,
    },
    {
      title: t('service.protocol'),
      dataIndex: 'protocol',
      key: 'protocol',
      render: (protocol: string) => getProtocolTag(protocol),
    },
    {
      title: t('service.endpointCount'),
      dataIndex: 'endpointCount',
      key: 'endpointCount',
      render: (count: number) => <Tag>{count} API</Tag>,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
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
      width: 250,
      render: (_: unknown, record: ExternalService) => (
        <Space>
          <Tooltip title={t('service.testConnection')}>
            <Button
              type="link"
              size="small"
              loading={testingId === record.id}
              icon={record.status === 'active' ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
              onClick={() => handleTestConnection(record.id)}
            >
              {t('service.test')}
            </Button>
          </Tooltip>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/services/${record.id}/edit`)}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('service.deleteConfirm')}
            description={t('service.deleteConfirmDesc')}
            onConfirm={() => handleDelete(record.id)}
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

  return (
    <Card
      title={
        <Space>
          <ApiOutlined />
          {t('service.title')}
        </Space>
      }
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/services/new')}
        >
          {t('service.newService')}
        </Button>
      }
    >
      {error && (
        <Alert
          message={error}
          type="error"
          showIcon
          closable
          onClose={clearError}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => fetchServices()}>
              {t('common.retry')}
            </Button>
          }
        />
      )}
      <Table
        columns={columns}
        dataSource={services}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: currentPage + 1,
          pageSize,
          total: totalElements,
          showSizeChanger: true,
          showTotal: (total) => t('common.total', { count: total }),
          onChange: (page, size) => fetchServices(page - 1, size),
        }}
      />
    </Card>
  )
}
