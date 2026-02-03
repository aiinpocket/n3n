import React from 'react'
import { Card, Tag, Button, Space, Tooltip, Typography, Popconfirm } from 'antd'
import {
  AppleOutlined,
  WindowsOutlined,
  DesktopOutlined,
  DeleteOutlined,
  EditOutlined,
  GlobalOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import type { Device } from '../../api/device'

const { Text } = Typography

interface DeviceCardProps {
  device: Device
  onEdit: (device: Device) => void
  onDelete: (deviceId: string) => void
}

const DeviceCard: React.FC<DeviceCardProps> = ({ device, onEdit, onDelete }) => {
  const getPlatformIcon = () => {
    switch (device.platform) {
      case 'macos':
        return <AppleOutlined style={{ fontSize: 24 }} />
      case 'windows':
        return <WindowsOutlined style={{ fontSize: 24 }} />
      default:
        return <DesktopOutlined style={{ fontSize: 24 }} />
    }
  }

  const getPlatformName = () => {
    switch (device.platform) {
      case 'macos':
        return 'macOS'
      case 'windows':
        return 'Windows'
      case 'linux':
        return 'Linux'
      default:
        return device.platform
    }
  }

  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleString()
  }

  const getTimeSince = (timestamp: number) => {
    const seconds = Math.floor((Date.now() - timestamp) / 1000)
    if (seconds < 60) return '剛剛'
    if (seconds < 3600) return `${Math.floor(seconds / 60)} 分鐘前`
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小時前`
    return `${Math.floor(seconds / 86400)} 天前`
  }

  const isOnline = Date.now() - device.lastActiveAt < 5 * 60 * 1000 // 5 minutes

  return (
    <Card
      hoverable
      style={{ marginBottom: 16 }}
      actions={[
        <Tooltip title="編輯設定" key="edit">
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => onEdit(device)}
          />
        </Tooltip>,
        <Popconfirm
          key="delete"
          title="解除配對"
          description="確定要解除此設備的配對嗎？"
          onConfirm={() => onDelete(device.deviceId)}
          okText="確定"
          cancelText="取消"
          okButtonProps={{ danger: true }}
        >
          <Tooltip title="解除配對">
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Tooltip>
        </Popconfirm>,
      ]}
    >
      <Card.Meta
        avatar={
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 8,
              backgroundColor: 'var(--color-bg-elevated)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {getPlatformIcon()}
          </div>
        }
        title={
          <Space>
            <span>{device.deviceName}</span>
            <Tag color={isOnline ? 'green' : 'default'}>
              {isOnline ? (
                <>
                  <CheckCircleOutlined /> 在線
                </>
              ) : (
                <>
                  <CloseCircleOutlined /> 離線
                </>
              )}
            </Tag>
            {device.revoked && <Tag color="red">已撤銷</Tag>}
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Text type="secondary">
              <DesktopOutlined /> {getPlatformName()}
            </Text>
            <Text type="secondary">
              <ClockCircleOutlined /> 最後活動：{getTimeSince(device.lastActiveAt)}
            </Text>
            {device.directConnectionEnabled && device.externalAddress && (
              <Text type="secondary">
                <GlobalOutlined /> {device.externalAddress}
              </Text>
            )}
            <Text type="secondary" style={{ fontSize: 12 }}>
              配對時間：{formatDate(device.pairedAt)}
            </Text>
          </Space>
        }
      />
    </Card>
  )
}

export default DeviceCard
