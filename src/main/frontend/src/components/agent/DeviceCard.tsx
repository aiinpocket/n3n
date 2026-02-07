import React from 'react'
import { getLocale } from '../../utils/locale'
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
import { useTranslation } from 'react-i18next'
import type { Device } from '../../api/device'

const { Text } = Typography

interface DeviceCardProps {
  device: Device
  onEdit: (device: Device) => void
  onDelete: (deviceId: string) => void
}

const DeviceCard: React.FC<DeviceCardProps> = ({ device, onEdit, onDelete }) => {
  const { t } = useTranslation()
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
    return new Date(timestamp).toLocaleString(getLocale())
  }

  const getTimeSince = (timestamp: number) => {
    const seconds = Math.floor((Date.now() - timestamp) / 1000)
    if (seconds < 60) return t('deviceCard.justNow')
    if (seconds < 3600) return t('deviceCard.minutesAgo', { count: Math.floor(seconds / 60) })
    if (seconds < 86400) return t('deviceCard.hoursAgo', { count: Math.floor(seconds / 3600) })
    return t('deviceCard.daysAgo', { count: Math.floor(seconds / 86400) })
  }

  const isOnline = Date.now() - device.lastActiveAt < 5 * 60 * 1000 // 5 minutes

  return (
    <Card
      hoverable
      style={{ marginBottom: 16 }}
      actions={[
        <Tooltip title={t('deviceCard.editSettings')} key="edit">
          <Button
            type="text"
            icon={<EditOutlined />}
            onClick={() => onEdit(device)}
          />
        </Tooltip>,
        <Popconfirm
          key="delete"
          title={t('deviceCard.unpair')}
          description={t('deviceCard.unpairConfirm')}
          onConfirm={() => onDelete(device.deviceId)}
          okText={t('common.confirm')}
          cancelText={t('common.cancel')}
          okButtonProps={{ danger: true }}
        >
          <Tooltip title={t('deviceCard.unpair')}>
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
                  <CheckCircleOutlined /> {t('deviceCard.online')}
                </>
              ) : (
                <>
                  <CloseCircleOutlined /> {t('deviceCard.offline')}
                </>
              )}
            </Tag>
            {device.revoked && <Tag color="red">{t('deviceCard.revoked')}</Tag>}
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Text type="secondary">
              <DesktopOutlined /> {getPlatformName()}
            </Text>
            <Text type="secondary">
              <ClockCircleOutlined /> {t('deviceCard.lastActivity')}{getTimeSince(device.lastActiveAt)}
            </Text>
            {device.directConnectionEnabled && device.externalAddress && (
              <Text type="secondary">
                <GlobalOutlined /> {device.externalAddress}
              </Text>
            )}
            <Text type="secondary" style={{ fontSize: 12 }}>
              {t('deviceCard.pairedAt')}{formatDate(device.pairedAt)}
            </Text>
          </Space>
        }
      />
    </Card>
  )
}

export default DeviceCard
