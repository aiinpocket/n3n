import { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Typography,
  Empty,
  Alert,
  Table,
  Tag,
  message,
  Tooltip,
  Badge,
  Statistic,
  Row,
  Col,
  Modal,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ReloadOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DesktopOutlined,
  LinkOutlined,
  CopyOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { gatewayApi, type GatewayNode, type GatewayStats, type PairingCodeResponse } from '../api/gateway'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Text } = Typography

export default function GatewayPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [nodes, setNodes] = useState<GatewayNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [capabilities, setCapabilities] = useState<Record<string, unknown>>({})
  const [stats, setStats] = useState<GatewayStats | null>(null)
  const [pairingCode, setPairingCode] = useState<PairingCodeResponse | null>(null)
  const [pairingModalOpen, setPairingModalOpen] = useState(false)
  const [generatingCode, setGeneratingCode] = useState(false)

  const fetchStats = useCallback(async () => {
    try {
      const data = await gatewayApi.getStats()
      setStats(data)
    } catch {
      // Non-critical, ignore (may require ADMIN role)
    }
  }, [])

  const fetchNodes = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await gatewayApi.listNodes()
      setNodes(data)
    } catch (err) {
      setError(extractApiError(err, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  const fetchCapabilities = useCallback(async () => {
    try {
      const data = await gatewayApi.getCapabilities()
      setCapabilities(data)
    } catch {
      // Non-critical, ignore
    }
  }, [])

  useEffect(() => {
    fetchNodes()
    fetchCapabilities()
    fetchStats()
  }, [fetchNodes, fetchCapabilities, fetchStats])

  const handleGeneratePairingCode = async () => {
    setGeneratingCode(true)
    try {
      const result = await gatewayApi.generatePairingCode()
      setPairingCode(result)
      setPairingModalOpen(true)
    } catch (err) {
      message.error(extractApiError(err, t('gateway.pairingCodeFailed')))
    } finally {
      setGeneratingCode(false)
    }
  }

  const handleCopyPairingCode = async () => {
    if (pairingCode) {
      try {
        await navigator.clipboard.writeText(pairingCode.code)
        message.success(t('gateway.pairingCodeCopied'))
      } catch {
        message.error(t('common.copyFailed'))
      }
    }
  }

  const handleInvoke = async (connectionId: string, capability: string) => {
    try {
      const result = await gatewayApi.invokeNode(connectionId, { capability, args: {} })
      if (result.success) {
        message.success(t('gateway.invokeSuccess'))
      } else {
        message.error(result.error || t('gateway.invokeFailed'))
      }
    } catch (err) {
      message.error(extractApiError(err, t('gateway.invokeFailed')))
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'CONNECTED':
        return <Badge status="success" text={t('gateway.statusConnected')} />
      case 'DISCONNECTED':
        return <Badge status="default" text={t('gateway.statusDisconnected')} />
      case 'IDLE':
        return <Badge status="warning" text={t('gateway.statusIdle')} />
      default:
        return <Badge status="default" text={status} />
    }
  }

  const getPlatformIcon = () => {
    return <DesktopOutlined />
  }

  const columns: ColumnsType<GatewayNode> = [
    {
      title: t('gateway.nodeName'),
      dataIndex: 'displayName',
      key: 'displayName',
      render: (name: string) => (
        <Space>
          {getPlatformIcon()}
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status: string) => getStatusBadge(status),
    },
    {
      title: t('gateway.platform'),
      dataIndex: 'platform',
      key: 'platform',
      width: 120,
      render: (platform: string, record) => (
        <Text type="secondary">{platform} {record.version}</Text>
      ),
    },
    {
      title: t('gateway.capabilities'),
      dataIndex: 'capabilities',
      key: 'capabilities',
      render: (caps: string[]) => (
        <Space wrap>
          {caps?.map((cap) => (
            <Tag key={cap} color="blue">{cap}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('gateway.latency'),
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 100,
      render: (ms: number) => (
        <Text type={ms > 200 ? 'danger' : ms > 100 ? 'warning' : 'success'}>
          {ms}ms
        </Text>
      ),
    },
    {
      title: t('gateway.lastActive'),
      dataIndex: 'lastActiveAt',
      key: 'lastActiveAt',
      width: 180,
      render: (time: string) => (
        <Tooltip title={time}>
          <Space>
            <ClockCircleOutlined />
            <Text type="secondary">{new Date(time).toLocaleString(getLocale())}</Text>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space>
          {record.capabilities?.includes('ping') && (
            <Tooltip title={t('gateway.ping')}>
              <Button
                type="link"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() => handleInvoke(record.connectionId, 'ping')}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={
          <Space>
            <ApiOutlined />
            {t('gateway.title')}
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<LinkOutlined />}
              onClick={handleGeneratePairingCode}
              loading={generatingCode}
            >
              {t('gateway.generatePairingCode')}
            </Button>
            <Button icon={<ReloadOutlined />} onClick={fetchNodes} loading={loading}>
              {t('common.refresh')}
            </Button>
          </Space>
        }
      >
        {stats && (
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={8}>
              <Card size="small">
                <Statistic
                  title={t('gateway.totalNodes')}
                  value={stats.connectedNodes ?? nodes.length}
                  prefix={<CheckCircleOutlined />}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small">
                <Statistic
                  title={t('gateway.totalInvocations')}
                  value={stats.totalInvocations ?? 0}
                  prefix={<ApiOutlined />}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card size="small">
                <Statistic
                  title={t('gateway.uptime')}
                  value={stats.uptime ?? '-'}
                />
              </Card>
            </Col>
          </Row>
        )}

        {error && (
          <Alert
            type="error"
            message={error}
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          columns={columns}
          dataSource={nodes}
          rowKey="connectionId"
          loading={loading}
          pagination={false}
          scroll={{ x: 800 }}
          locale={{
            emptyText: (
              <Empty description={t('gateway.noNodes')}>
                <Button type="primary" onClick={() => navigate('/devices')}>
                  {t('gateway.addAgent')}
                </Button>
              </Empty>
            )
          }}
        />

        {Object.keys(capabilities).length > 0 && (
          <Card
            size="small"
            title={t('gateway.availableCapabilities')}
            style={{ marginTop: 16 }}
          >
            <Space wrap>
              {Object.keys(capabilities).map((cap) => (
                <Tag key={cap} icon={<ApiOutlined />} color="purple">{cap}</Tag>
              ))}
            </Space>
          </Card>
        )}
      </Card>

      <Modal
        title={t('gateway.pairingCodeTitle')}
        open={pairingModalOpen}
        onCancel={() => { setPairingModalOpen(false); setPairingCode(null) }}
        footer={[
          <Button key="close" onClick={() => { setPairingModalOpen(false); setPairingCode(null) }}>
            {t('common.close')}
          </Button>,
          <Button key="copy" type="primary" icon={<CopyOutlined />} onClick={handleCopyPairingCode}>
            {t('gateway.copyPairingCode')}
          </Button>,
        ]}
      >
        {pairingCode && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Alert
              type="info"
              showIcon
              message={t('gateway.pairingCodeInstructions')}
            />
            <div style={{
              textAlign: 'center',
              padding: 24,
              background: 'var(--color-bg-elevated)',
              borderRadius: 8,
              border: '1px solid var(--color-border)',
            }}>
              <Text style={{
                fontSize: 32,
                fontFamily: 'monospace',
                fontWeight: 700,
                letterSpacing: 4,
              }}>
                {pairingCode.code}
              </Text>
            </div>
            <Text type="secondary" style={{ textAlign: 'center', display: 'block' }}>
              {t('gateway.pairingCodeExpiry', { seconds: pairingCode.expiresInSeconds })}
            </Text>
          </Space>
        )}
      </Modal>
    </div>
  )
}
