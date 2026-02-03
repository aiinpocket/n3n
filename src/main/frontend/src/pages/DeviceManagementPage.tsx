import React, { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Typography,
  Empty,
  Spin,
  Alert,
  Tabs,
  message,
  Popconfirm,
  Tag,
  Table,
  Modal,
  Input,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  PlusOutlined,
  ReloadOutlined,
  CopyOutlined,
  StopOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  AppleOutlined,
  WindowsOutlined,
} from '@ant-design/icons'
import {
  listRegistrations,
  blockAgent,
  unblockAgent,
  deleteRegistration,
  getStatusInfo,
  formatTime,
  generateInstallCommand,
  type AgentRegistration,
} from '../api/agentRegistration'

const { Title, Text, Paragraph } = Typography

const DeviceManagementPage: React.FC = () => {
  const [registrations, setRegistrations] = useState<AgentRegistration[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [installModalOpen, setInstallModalOpen] = useState(false)
  const [installCommand, setInstallCommand] = useState<string | null>(null)
  const [generatingCommand, setGeneratingCommand] = useState(false)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const regs = await listRegistrations()
      setRegistrations(regs)
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '載入設備列表失敗'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const handleGenerateInstallCommand = async () => {
    try {
      setGeneratingCommand(true)
      const result = await generateInstallCommand()
      setInstallCommand(result.command)
      setInstallModalOpen(true)
      fetchData() // Refresh to show new pending registration
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '產生安裝命令失敗'
      message.error(errorMessage)
    } finally {
      setGeneratingCommand(false)
    }
  }

  const handleCopyCommand = () => {
    if (installCommand) {
      navigator.clipboard.writeText(installCommand)
      message.success('已複製到剪貼簿')
    }
  }

  const handleBlock = async (id: string) => {
    try {
      await blockAgent(id, 'Blocked by user')
      message.success('Agent 已封鎖')
      fetchData()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '封鎖失敗'
      message.error(errorMessage)
    }
  }

  const handleUnblock = async (id: string) => {
    try {
      await unblockAgent(id)
      message.success('Agent 已解除封鎖')
      fetchData()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '解除封鎖失敗'
      message.error(errorMessage)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteRegistration(id)
      message.success('Agent 已刪除')
      fetchData()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '刪除失敗'
      message.error(errorMessage)
    }
  }

  const columns: ColumnsType<AgentRegistration> = [
    {
      title: '狀態',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: AgentRegistration['status']) => {
        const info = getStatusInfo(status)
        return <Tag color={info.color}>{info.label}</Tag>
      },
    },
    {
      title: '裝置名稱',
      dataIndex: 'deviceName',
      key: 'deviceName',
      render: (name: string | null, record: AgentRegistration) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name || '(待註冊)'}</Text>
          {record.platform && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.platform}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '建立時間',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (time: number) => formatTime(time),
    },
    {
      title: '最後活動',
      dataIndex: 'lastSeenAt',
      key: 'lastSeenAt',
      width: 180,
      render: (time: number | null) => formatTime(time),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: unknown, record: AgentRegistration) => (
        <Space>
          {record.status === 'BLOCKED' ? (
            <Button
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleUnblock(record.id)}
            >
              解除封鎖
            </Button>
          ) : record.status === 'REGISTERED' ? (
            <Popconfirm
              title="封鎖此 Agent"
              description="封鎖後此 Agent 將無法連線到平台"
              onConfirm={() => handleBlock(record.id)}
              okText="確定"
              cancelText="取消"
            >
              <Button size="small" icon={<StopOutlined />} danger>
                封鎖
              </Button>
            </Popconfirm>
          ) : null}
          <Popconfirm
            title="刪除此 Agent"
            description="刪除後無法復原，需要重新產生 Token"
            onConfirm={() => handleDelete(record.id)}
            okText="確定"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" icon={<DeleteOutlined />} danger>
              刪除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const renderAgentList = () => {
    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
        </div>
      )
    }

    if (error) {
      return (
        <Alert
          type="error"
          message={error}
          action={<Button onClick={fetchData}>重試</Button>}
        />
      )
    }

    if (registrations.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="尚未新增任何 Agent"
        >
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleGenerateInstallCommand}
            loading={generatingCommand}
          >
            新增 Agent
          </Button>
        </Empty>
      )
    }

    return (
      <Table
        columns={columns}
        dataSource={registrations}
        rowKey="id"
        pagination={false}
      />
    )
  }

  const tabItems = [
    {
      key: 'agents',
      label: `Agent 列表 (${registrations.length})`,
      children: renderAgentList(),
    },
    {
      key: 'install',
      label: '安裝說明',
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            message="一鍵安裝"
            description={
              <div>
                <p>點擊「新增 Agent」按鈕，複製產生的安裝命令，在終端機貼上執行即可。</p>
                <p style={{ marginBottom: 0 }}>
                  <strong>macOS / Linux：</strong>打開「終端機」(Terminal)
                </p>
              </div>
            }
          />
          <Card>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div style={{ textAlign: 'center' }}>
                <AppleOutlined style={{ fontSize: 48, color: '#666', marginRight: 24 }} />
                <WindowsOutlined style={{ fontSize: 48, color: '#0078d4' }} />
              </div>
              <Button
                type="primary"
                size="large"
                icon={<PlusOutlined />}
                onClick={handleGenerateInstallCommand}
                loading={generatingCommand}
                block
              >
                產生安裝命令
              </Button>
            </Space>
          </Card>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <div>
          <Title level={2} style={{ margin: 0 }}>
            設備管理
          </Title>
          <Paragraph type="secondary">
            管理已連接的 Agent，或新增 Agent 到您的電腦
          </Paragraph>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            重新整理
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleGenerateInstallCommand}
            loading={generatingCommand}
          >
            新增 Agent
          </Button>
        </Space>
      </div>

      <Tabs items={tabItems} />

      <Modal
        title="安裝 N3N Agent"
        open={installModalOpen}
        onCancel={() => setInstallModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setInstallModalOpen(false)}>
            關閉
          </Button>,
          <Button key="copy" type="primary" icon={<CopyOutlined />} onClick={handleCopyCommand}>
            複製命令
          </Button>,
        ]}
        width={600}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Paragraph>
            在終端機 (Terminal) 中執行以下命令：
          </Paragraph>
          <Input.TextArea
            value={installCommand || ''}
            readOnly
            autoSize={{ minRows: 2, maxRows: 4 }}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
          <Alert
            type="info"
            message="安裝完成後，Agent 會自動連線到平台。重新整理此頁面可查看狀態。"
          />
        </Space>
      </Modal>
    </div>
  )
}

export default DeviceManagementPage
