import { useEffect, useState } from 'react'
import { Button, Card, Table, Space, Tag, Popconfirm, message, Tooltip } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, ApiOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useServiceStore } from '../stores/serviceStore'
import type { ExternalService } from '../types'

export default function ServiceListPage() {
  const navigate = useNavigate()
  const { services, totalElements, isLoading, currentPage, pageSize, fetchServices, deleteService, testConnection } = useServiceStore()
  const [testingId, setTestingId] = useState<string | null>(null)

  useEffect(() => {
    fetchServices()
  }, [fetchServices])

  const handleDelete = async (id: string) => {
    try {
      await deleteService(id)
      message.success('服務已刪除')
    } catch {
      message.error('刪除失敗')
    }
  }

  const handleTestConnection = async (id: string) => {
    setTestingId(id)
    try {
      const result = await testConnection(id)
      if (result.success) {
        message.success(`連線成功 (${result.latencyMs}ms)`)
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
        return <Tag color="green">運作中</Tag>
      case 'inactive':
        return <Tag color="default">停用</Tag>
      case 'error':
        return <Tag color="red">錯誤</Tag>
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
      title: '服務名稱',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (name: string, record: ExternalService) => (
        <a onClick={() => navigate(`/services/${record.id}`)}>{name}</a>
      ),
    },
    {
      title: '識別名稱',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <code>{name}</code>,
    },
    {
      title: '協議',
      dataIndex: 'protocol',
      key: 'protocol',
      render: (protocol: string) => getProtocolTag(protocol),
    },
    {
      title: '端點數量',
      dataIndex: 'endpointCount',
      key: 'endpointCount',
      render: (count: number) => <Tag>{count} 個 API</Tag>,
    },
    {
      title: '狀態',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '更新時間',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string) => new Date(date).toLocaleString('zh-TW'),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_: unknown, record: ExternalService) => (
        <Space>
          <Tooltip title="測試連線">
            <Button
              type="link"
              size="small"
              loading={testingId === record.id}
              icon={record.status === 'active' ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
              onClick={() => handleTestConnection(record.id)}
            >
              測試
            </Button>
          </Tooltip>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/services/${record.id}/edit`)}
          >
            編輯
          </Button>
          <Popconfirm
            title="確定要刪除此服務？"
            description="刪除後，使用此服務的流程可能無法正常執行。"
            onConfirm={() => handleDelete(record.id)}
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

  return (
    <Card
      title={
        <Space>
          <ApiOutlined />
          外部服務管理
        </Space>
      }
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/services/new')}
        >
          註冊新服務
        </Button>
      }
    >
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
          showTotal: (total) => `共 ${total} 項`,
          onChange: (page, size) => fetchServices(page - 1, size),
        }}
      />
    </Card>
  )
}
