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
} from 'antd'
import {
  PlusOutlined,
  ReloadOutlined,
  DownloadOutlined,
  AppleOutlined,
  WindowsOutlined,
  DesktopOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  listDevices,
  unpairDevice,
  revokeAllDevices,
  getDownloadInfo,
  formatSize,
  getDownloadUrl,
  type Device,
  type DownloadInfo,
} from '../api/device'
import DeviceCard from '../components/agent/DeviceCard'
import DevicePairingModal from '../components/agent/DevicePairingModal'
import DeviceEditModal from '../components/agent/DeviceEditModal'

const { Title, Text, Paragraph } = Typography

const DeviceManagementPage: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([])
  const [downloadInfo, setDownloadInfo] = useState<DownloadInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pairingModalOpen, setPairingModalOpen] = useState(false)
  const [editingDevice, setEditingDevice] = useState<Device | null>(null)

  const fetchDevices = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const [deviceList, downloads] = await Promise.all([
        listDevices(),
        getDownloadInfo().catch(() => null),
      ])
      setDevices(deviceList)
      setDownloadInfo(downloads)
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '載入設備列表失敗'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDevices()
  }, [fetchDevices])

  const handleUnpair = async (deviceId: string) => {
    try {
      await unpairDevice(deviceId)
      message.success('設備已解除配對')
      fetchDevices()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '解除配對失敗'
      message.error(errorMessage)
    }
  }

  const handleRevokeAll = async () => {
    try {
      await revokeAllDevices()
      message.success('所有設備已撤銷')
      fetchDevices()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '撤銷失敗'
      message.error(errorMessage)
    }
  }

  const renderDeviceList = () => {
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
          action={<Button onClick={fetchDevices}>重試</Button>}
        />
      )
    }

    if (devices.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="尚未配對任何設備"
        >
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setPairingModalOpen(true)}
          >
            新增設備
          </Button>
        </Empty>
      )
    }

    return (
      <Row gutter={[16, 16]}>
        {devices.map((device) => (
          <Col xs={24} sm={12} lg={8} key={device.deviceId}>
            <DeviceCard
              device={device}
              onEdit={setEditingDevice}
              onDelete={handleUnpair}
            />
          </Col>
        ))}
      </Row>
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
      key: 'devices',
      label: `已配對設備 (${devices.length})`,
      children: renderDeviceList(),
    },
    {
      key: 'downloads',
      label: '下載 Agent',
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            message="N3N Agent 讓您可以從平台遠端控制您的電腦"
            description="下載並安裝 Agent 後，使用配對碼將設備連接到平台。支援螢幕截圖、命令執行、檔案管理等功能。"
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
            管理已連接的設備和下載 Agent
          </Paragraph>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchDevices}>
            重新整理
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setPairingModalOpen(true)}
          >
            新增設備
          </Button>
          {devices.length > 0 && (
            <Popconfirm
              title="撤銷所有設備"
              description="確定要撤銷所有已配對的設備嗎？此操作無法復原。"
              onConfirm={handleRevokeAll}
              okText="確定"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger icon={<WarningOutlined />}>
                撤銷全部
              </Button>
            </Popconfirm>
          )}
        </Space>
      </div>

      <Tabs items={tabItems} />

      <DevicePairingModal
        open={pairingModalOpen}
        onClose={() => setPairingModalOpen(false)}
        onPaired={fetchDevices}
      />

      <DeviceEditModal
        device={editingDevice}
        open={!!editingDevice}
        onClose={() => setEditingDevice(null)}
        onUpdated={fetchDevices}
      />
    </div>
  )
}

export default DeviceManagementPage
