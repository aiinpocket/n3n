import React, { useState, useEffect, useCallback } from 'react'
import logger from '../utils/logger'
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
  DisconnectOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
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
import {
  listDevices,
  unpairDevice,
  revokeAllDevices,
  type DeviceInfo,
} from '../api/agentPairing'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Title, Text, Paragraph } = Typography

const DeviceManagementPage: React.FC = () => {
  const { t } = useTranslation()
  const [registrations, setRegistrations] = useState<AgentRegistration[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [installModalOpen, setInstallModalOpen] = useState(false)
  const [installCommand, setInstallCommand] = useState<string | null>(null)
  const [generatingCommand, setGeneratingCommand] = useState(false)
  const [pairedDevices, setPairedDevices] = useState<DeviceInfo[]>([])
  const [pairedLoading, setPairedLoading] = useState(false)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const regs = await listRegistrations()
      setRegistrations(regs)
    } catch (err: unknown) {
      const errorMessage = extractApiError(err, t('device.loadFailed'))
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [t])

  const fetchPairedDevices = useCallback(async () => {
    try {
      setPairedLoading(true)
      const devices = await listDevices()
      setPairedDevices(devices)
    } catch (err) {
      logger.warn('Failed to fetch paired devices:', err)
    } finally {
      setPairedLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
    fetchPairedDevices()
  }, [fetchData, fetchPairedDevices])

  const handleGenerateInstallCommand = async () => {
    try {
      setGeneratingCommand(true)
      const result = await generateInstallCommand()
      setInstallCommand(result.command)
      setInstallModalOpen(true)
      fetchData() // Refresh to show new pending registration
    } catch (err: unknown) {
      const errorMessage = extractApiError(err, t('device.generateCommandFailed'))
      message.error(errorMessage)
    } finally {
      setGeneratingCommand(false)
    }
  }

  const handleCopyCommand = async () => {
    if (installCommand) {
      try {
        await navigator.clipboard.writeText(installCommand)
        message.success(t('device.copiedToClipboard'))
      } catch {
        message.error(t('common.copyFailed'))
      }
    }
  }

  const handleBlock = async (id: string) => {
    try {
      await blockAgent(id, t('device.blockedByUser'))
      message.success(t('device.agentBlocked'))
      fetchData()
    } catch (err: unknown) {
      const errorMessage = extractApiError(err, t('device.blockFailed'))
      message.error(errorMessage)
    }
  }

  const handleUnblock = async (id: string) => {
    try {
      await unblockAgent(id)
      message.success(t('device.agentUnblocked'))
      fetchData()
    } catch (err: unknown) {
      const errorMessage = extractApiError(err, t('device.unblockFailed'))
      message.error(errorMessage)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteRegistration(id)
      message.success(t('device.agentDeleted'))
      fetchData()
    } catch (err: unknown) {
      const errorMessage = extractApiError(err, t('device.deleteFailed'))
      message.error(errorMessage)
    }
  }

  const handleUnpairDevice = async (deviceId: string) => {
    try {
      await unpairDevice(deviceId)
      message.success(t('device.deviceUnpaired'))
      fetchPairedDevices()
    } catch (err: unknown) {
      message.error(extractApiError(err, t('device.unpairFailed')))
    }
  }

  const handleRevokeAll = async () => {
    try {
      const result = await revokeAllDevices()
      message.success(t('device.revokeAllSuccess', { count: result.revokedCount }))
      fetchPairedDevices()
    } catch (err: unknown) {
      message.error(extractApiError(err, t('device.revokeAllFailed')))
    }
  }

  const pairedDeviceColumns: ColumnsType<DeviceInfo> = [
    {
      title: t('device.deviceName'),
      dataIndex: 'deviceName',
      key: 'deviceName',
      render: (name: string, record: DeviceInfo) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.deviceId.substring(0, 8)}...</Text>
        </Space>
      ),
    },
    {
      title: t('device.platform'),
      dataIndex: 'platform',
      key: 'platform',
      width: 120,
      render: (platform: string) => <Tag>{platform}</Tag>,
    },
    {
      title: t('device.status'),
      key: 'status',
      width: 100,
      render: (_: unknown, record: DeviceInfo) => (
        record.revoked
          ? <Tag color="red">{t('deviceCard.revoked')}</Tag>
          : <Tag color="green">{t('deviceCard.online')}</Tag>
      ),
    },
    {
      title: t('device.pairedAt'),
      dataIndex: 'pairedAt',
      key: 'pairedAt',
      width: 180,
      render: (ts: number) => new Date(ts).toLocaleString(getLocale()),
    },
    {
      title: t('device.lastActiveAt'),
      dataIndex: 'lastActiveAt',
      key: 'lastActiveAt',
      width: 180,
      render: (ts: number) => new Date(ts).toLocaleString(getLocale()),
    },
    {
      title: t('device.actions'),
      key: 'actions',
      width: 120,
      render: (_: unknown, record: DeviceInfo) => (
        <Popconfirm
          title={t('device.unpairConfirmTitle')}
          description={t('device.unpairConfirmDesc')}
          onConfirm={() => handleUnpairDevice(record.deviceId)}
          okText={t('common.ok')}
          cancelText={t('common.cancel')}
          okButtonProps={{ danger: true }}
        >
          <Button size="small" icon={<DisconnectOutlined />} danger>
            {t('device.unpairDevice')}
          </Button>
        </Popconfirm>
      ),
    },
  ]

  const columns: ColumnsType<AgentRegistration> = [
    {
      title: t('device.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: AgentRegistration['status']) => {
        const info = getStatusInfo(status)
        return <Tag color={info.color}>{t(info.label)}</Tag>
      },
    },
    {
      title: t('device.deviceName'),
      dataIndex: 'deviceName',
      key: 'deviceName',
      render: (name: string | null, record: AgentRegistration) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name || t('device.pendingRegistration')}</Text>
          {record.platform && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.platform}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: t('device.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (time: number) => formatTime(time),
    },
    {
      title: t('device.lastActivity'),
      dataIndex: 'lastSeenAt',
      key: 'lastSeenAt',
      width: 180,
      render: (time: number | null) => formatTime(time),
    },
    {
      title: t('device.actions'),
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
              {t('device.unblock')}
            </Button>
          ) : record.status === 'REGISTERED' ? (
            <Popconfirm
              title={t('device.blockConfirmTitle')}
              description={t('device.blockConfirmDesc')}
              onConfirm={() => handleBlock(record.id)}
              okText={t('common.ok')}
              cancelText={t('common.cancel')}
            >
              <Button size="small" icon={<StopOutlined />} danger>
                {t('device.block')}
              </Button>
            </Popconfirm>
          ) : null}
          <Popconfirm
            title={t('device.deleteConfirmTitle')}
            description={t('device.deleteConfirmDesc')}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.ok')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
          >
            <Button size="small" icon={<DeleteOutlined />} danger>
              {t('common.delete')}
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
          action={<Button onClick={fetchData}>{t('error.retry')}</Button>}
        />
      )
    }

    if (registrations.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('device.noAgents')}
        >
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleGenerateInstallCommand}
            loading={generatingCommand}
          >
            {t('device.addAgent')}
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
        scroll={{ x: 800 }}
      />
    )
  }

  const renderPairedDevices = () => {
    if (pairedLoading) {
      return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
    }
    if (pairedDevices.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('device.noPairedDevices')} />
    }
    return (
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {pairedDevices.length > 1 && (
          <div style={{ textAlign: 'right' }}>
            <Popconfirm
              title={t('device.revokeAllConfirmTitle')}
              description={t('device.revokeAllConfirmDesc')}
              onConfirm={handleRevokeAll}
              okText={t('common.ok')}
              cancelText={t('common.cancel')}
              okButtonProps={{ danger: true }}
              icon={<ExclamationCircleOutlined style={{ color: 'red' }} />}
            >
              <Button danger icon={<DisconnectOutlined />}>
                {t('device.revokeAll')}
              </Button>
            </Popconfirm>
          </div>
        )}
        <Table
          columns={pairedDeviceColumns}
          dataSource={pairedDevices}
          rowKey="deviceId"
          pagination={false}
          scroll={{ x: 800 }}
        />
      </Space>
    )
  }

  const tabItems = [
    {
      key: 'agents',
      label: `${t('device.agentList')} (${registrations.length})`,
      children: renderAgentList(),
    },
    {
      key: 'paired',
      label: `${t('device.pairedDevices')} (${pairedDevices.length})`,
      children: renderPairedDevices(),
    },
    {
      key: 'install',
      label: t('device.installInstructions'),
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            message={t('device.oneClickInstall')}
            description={
              <div>
                <p>{t('device.oneClickInstallDesc')}</p>
                <p style={{ marginBottom: 0 }}>
                  <strong>{t('device.macLinuxTerminal')}</strong>
                </p>
              </div>
            }
          />
          <Card>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div style={{ textAlign: 'center' }}>
                <AppleOutlined style={{ fontSize: 48, color: 'var(--color-text-tertiary)', marginRight: 24 }} />
                <WindowsOutlined style={{ fontSize: 48, color: 'var(--color-info)' }} />
              </div>
              <Button
                type="primary"
                size="large"
                icon={<PlusOutlined />}
                onClick={handleGenerateInstallCommand}
                loading={generatingCommand}
                block
              >
                {t('device.generateInstallCommand')}
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
            {t('device.title')}
          </Title>
          <Paragraph type="secondary">
            {t('device.subtitle')}
          </Paragraph>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            {t('common.refresh')}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleGenerateInstallCommand}
            loading={generatingCommand}
          >
            {t('device.addAgent')}
          </Button>
        </Space>
      </div>

      <Tabs items={tabItems} />

      <Modal
        title={t('device.installAgentTitle')}
        open={installModalOpen}
        onCancel={() => { setInstallModalOpen(false); setInstallCommand(null) }}
        footer={[
          <Button key="close" onClick={() => { setInstallModalOpen(false); setInstallCommand(null) }}>
            {t('common.close')}
          </Button>,
          <Button key="copy" type="primary" icon={<CopyOutlined />} onClick={handleCopyCommand}>
            {t('device.copyCommand')}
          </Button>,
        ]}
        width={600}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Paragraph>
            {t('device.runCommandInTerminal')}
          </Paragraph>
          <Input.TextArea
            value={installCommand || ''}
            readOnly
            autoSize={{ minRows: 2, maxRows: 4 }}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
          <Alert
            type="info"
            message={t('device.installCompleteHint')}
          />
        </Space>
      </Modal>
    </div>
  )
}

export default DeviceManagementPage
