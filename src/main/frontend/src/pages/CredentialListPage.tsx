import React, { useEffect, useState } from 'react'
import { Table, Button, Space, Card, Typography, Tag, message, Popconfirm, Tooltip } from 'antd'
import { PlusOutlined, DeleteOutlined, KeyOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useCredentialStore } from '../stores/credentialStore'
import { Credential } from '../api/credential'
import CredentialFormModal from '../components/credentials/CredentialFormModal'

const { Title } = Typography

const CredentialListPage: React.FC = () => {
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

  useEffect(() => {
    fetchCredentials()
  }, [fetchCredentials])

  const handleDelete = async (id: string) => {
    try {
      await deleteCredential(id)
      message.success('認證已刪除')
    } catch {
      message.error('刪除失敗')
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    try {
      const result = await testCredential(id)
      if (result.success) {
        message.success('連線測試成功')
      } else {
        message.error(`連線測試失敗: ${result.message || '未知錯誤'}`)
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
      database: '資料庫',
      ssh: 'SSH'
    }
    return names[type] || type
  }

  const getVisibilityTag = (visibility: string) => {
    switch (visibility) {
      case 'private':
        return <Tag>私人</Tag>
      case 'workspace':
        return <Tag color="blue">工作區</Tag>
      case 'shared':
        return <Tag color="green">共享</Tag>
      default:
        return <Tag>{visibility}</Tag>
    }
  }

  const columns = [
    {
      title: '名稱',
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
      title: '類型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => (
        <Tag color={getTypeColor(type)}>{getTypeDisplayName(type)}</Tag>
      )
    },
    {
      title: '說明',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '可見性',
      dataIndex: 'visibility',
      key: 'visibility',
      render: (visibility: string) => getVisibilityTag(visibility)
    },
    {
      title: '建立時間',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString('zh-TW')
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: Credential) => (
        <Space>
          <Tooltip title="測試連線">
            <Button
              type="link"
              icon={<CheckCircleOutlined />}
              loading={testingId === record.id}
              onClick={() => handleTest(record.id)}
            />
          </Tooltip>
          <Popconfirm
            title="確定要刪除此認證嗎？"
            description="刪除後將無法復原，使用此認證的服務可能無法正常運作。"
            onConfirm={() => handleDelete(record.id)}
            okText="刪除"
            cancelText="取消"
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
            認證管理
          </Title>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setFormVisible(true)}
          >
            新增認證
          </Button>
        </div>

        <div style={{ marginBottom: 16, padding: 12, background: '#fffbe6', borderRadius: 4 }}>
          <ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 8 }} />
          <span style={{ color: '#666' }}>
            認證資訊使用 AES-256 加密儲存，平台在呼叫外部服務時自動解密注入。
            請妥善保管您的認證，不要分享給不信任的人。
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
            showTotal: (total) => `共 ${total} 筆`
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
