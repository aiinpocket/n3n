import React, { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Typography,
  Row,
  Col,
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
  DownloadOutlined,
  AppleOutlined,
  WindowsOutlined,
  DesktopOutlined,
  StopOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  CopyOutlined,
} from '@ant-design/icons'
import {
  getDownloadInfo,
  formatSize,
  getDownloadUrl,
  type DownloadInfo,
} from '../api/device'
import {
  listRegistrations,
  generateAgentToken,
  blockAgent,
  unblockAgent,
  deleteRegistration,
  getStatusInfo,
  formatTime,
  type AgentRegistration,
  type TokenGenerationResult,
} from '../api/agentRegistration'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

const DeviceManagementPage: React.FC = () => {
  const [registrations, setRegistrations] = useState<AgentRegistration[]>([])
  const [downloadInfo, setDownloadInfo] = useState<DownloadInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [generatingToken, setGeneratingToken] = useState(false)
  const [tokenResult, setTokenResult] = useState<TokenGenerationResult | null>(null)
  const [tokenModalOpen, setTokenModalOpen] = useState(false)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const [regs, downloads] = await Promise.all([
        listRegistrations(),
        getDownloadInfo().catch(() => null),
      ])
      setRegistrations(regs)
      setDownloadInfo(downloads)
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

  const handleGenerateToken = async () => {
    try {
      setGeneratingToken(true)
      const result = await generateAgentToken()
      setTokenResult(result)
      setTokenModalOpen(true)
      fetchData()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '產生 Token 失敗'
      message.error(errorMessage)
    } finally {
      setGeneratingToken(false)
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

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('已複製到剪貼簿')
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
            onClick={handleGenerateToken}
            loading={generatingToken}
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

  const renderDownloads = () => {
    if (!downloadInfo) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="下載資訊不可用"
        />
      )
    }

    const platforms = [
      {
        key: 'macos',
        name: 'macOS',
        icon: <AppleOutlined />,
        architectures: ['arm64', 'x86_64'],
      },
      {
        key: 'windows',
        name: 'Windows',
        icon: <WindowsOutlined />,
        architectures: ['x86_64'],
      },
      {
        key: 'linux',
        name: 'Linux',
        icon: <DesktopOutlined />,
        architectures: ['x86_64'],
      },
    ]

    return (
      <Row gutter={[16, 16]}>
        {platforms.map((platform) => (
          <Col xs={24} sm={12} lg={8} key={platform.key}>
            <Card
              title={
                <Space>
                  {platform.icon}
                  {platform.name}
                </Space>
              }
            >
              <Space direction="vertical" style={{ width: '100%' }}>
                {platform.architectures.map((arch) => {
                  const platformData = downloadInfo.agents[platform.key as keyof typeof downloadInfo.agents]
                  // eslint-disable-next-line @typescript-eslint/no-explicit-any
                  const info = platformData ? (platformData as any)[arch] : undefined

                  if (!info) return null

                  const isAvailable = info.status !== 'coming-soon' && info.size > 0

                  return (
                    <div
                      key={arch}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <Space>
                        <Text>{arch}</Text>
                        {isAvailable && (
                          <Text type="secondary">({formatSize(info.size)})</Text>
                        )}
                        {!isAvailable && <Tag>即將推出</Tag>}
                      </Space>
                      <Button
                        type="primary"
                        icon={<DownloadOutlined />}
                        size="small"
                        disabled={!isAvailable}
                        href={isAvailable ? getDownloadUrl(platform.key, arch) : undefined}
                      >
                        下載
                      </Button>
                    </div>
                  )
                })}
              </Space>
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  const tabItems = [
    {
      key: 'agents',
      label: `Agent 列表 (${registrations.length})`,
      children: renderAgentList(),
    },
    {
      key: 'downloads',
      label: '下載 Agent',
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            message="N3N Agent 讓您可以從平台遠端控制您的電腦"
            description={
              <ol style={{ margin: 0, paddingLeft: 20 }}>
                <li>下載並安裝對應平台的 Agent</li>
                <li>點擊「新增 Agent」產生設定檔</li>
                <li>將設定檔放到 Agent 目錄並啟動</li>
                <li>Agent 會自動連線到平台</li>
              </ol>
            }
          />
          {renderDownloads()}
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
            管理已連接的 Agent 和下載 Agent 程式
          </Paragraph>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            重新整理
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleGenerateToken}
            loading={generatingToken}
          >
            新增 Agent
          </Button>
        </Space>
      </div>

      <Tabs items={tabItems} />

      <Modal
        title="Agent 設定檔"
        open={tokenModalOpen}
        onCancel={() => setTokenModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setTokenModalOpen(false)}>
            關閉
          </Button>,
          <Button
            key="copy"
            type="primary"
            icon={<CopyOutlined />}
            onClick={() => {
              if (tokenResult) {
                copyToClipboard(JSON.stringify(tokenResult.config, null, 2))
              }
            }}
          >
            複製設定
          </Button>,
        ]}
        width={600}
      >
        {tokenResult && (
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Alert
              type="warning"
              message="請妥善保存此設定檔"
              description="此設定檔包含一次性的註冊 Token，關閉視窗後將無法再次查看。"
            />
            <div>
              <Text strong>設定檔內容：</Text>
              <TextArea
                value={JSON.stringify(tokenResult.config, null, 2)}
                autoSize={{ minRows: 10, maxRows: 20 }}
                readOnly
                style={{ fontFamily: 'monospace', marginTop: 8 }}
              />
            </div>
            <div>
              <Text type="secondary">
                將此設定檔儲存為 <Text code>n3n-agent-config.json</Text>，
                放到 Agent 程式的同一目錄下，然後啟動 Agent。
              </Text>
            </div>
          </Space>
        )}
      </Modal>
    </div>
  )
}

export default DeviceManagementPage
