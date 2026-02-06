import { useEffect, useCallback, useRef, useState } from 'react'
import { Card, Row, Col, Statistic, Progress, Badge, Button, Switch, Space, Typography } from 'antd'
import {
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DashboardOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useMonitoringStore } from '../stores/monitoringStore'

const { Text } = Typography

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

function formatUptime(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h ${minutes}m`
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

function formatDuration(ms: number | null): string {
  if (ms === null || ms === undefined) return '-'
  if (ms < 1000) return `${Math.round(ms)}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function MonitoringPage() {
  const { t } = useTranslation()
  const { systemMetrics, flowStats, healthStatus, loading, fetchAll } = useMonitoringStore()
  const [autoRefresh, setAutoRefresh] = useState(true)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadData = useCallback(() => {
    fetchAll()
  }, [fetchAll])

  useEffect(() => {
    loadData()
  }, [loadData])

  useEffect(() => {
    if (autoRefresh) {
      intervalRef.current = setInterval(loadData, 10000) // 10s
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [autoRefresh, loadData])

  const heapPercent = systemMetrics
    ? Math.round((systemMetrics.heapUsed / systemMetrics.heapMax) * 100)
    : 0

  const cpuPercent = systemMetrics
    ? Math.round(systemMetrics.cpuUsage * 100)
    : 0

  return (
    <div>
      <Card
        title={
          <Space>
            <DashboardOutlined />
            {t('monitoring.title')}
          </Space>
        }
        extra={
          <Space>
            <Space size="small">
              <Text type="secondary">{t('monitoring.autoRefresh')}</Text>
              <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} />
            </Space>
            <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
              {t('monitoring.refresh')}
            </Button>
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        {/* JVM Metrics */}
        <Card type="inner" title={t('monitoring.jvm.title')} style={{ marginBottom: 16 }}>
          <Row gutter={[24, 16]}>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('monitoring.jvm.heapUsed')}
                value={systemMetrics ? formatBytes(systemMetrics.heapUsed) : '-'}
                suffix={systemMetrics ? `/ ${formatBytes(systemMetrics.heapMax)}` : ''}
              />
              <Progress
                percent={heapPercent}
                size="small"
                status={heapPercent > 85 ? 'exception' : 'normal'}
                showInfo={false}
              />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('monitoring.jvm.cpu')}
                value={cpuPercent}
                suffix="%"
              />
              <Progress
                percent={cpuPercent}
                size="small"
                status={cpuPercent > 80 ? 'exception' : 'normal'}
                showInfo={false}
              />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('monitoring.jvm.threads')}
                value={systemMetrics?.threadCount ?? '-'}
                suffix={systemMetrics ? `(${t('monitoring.jvm.threadPeak')}: ${systemMetrics.threadPeak})` : ''}
              />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('monitoring.jvm.gc')}
                value={systemMetrics?.gcCount ?? '-'}
                suffix={systemMetrics ? `(${systemMetrics.gcTimeMs}ms)` : ''}
              />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('monitoring.jvm.uptime')}
                value={systemMetrics ? formatUptime(systemMetrics.uptimeMs) : '-'}
              />
            </Col>
          </Row>
        </Card>

        {/* Flow Execution Stats */}
        <Card type="inner" title={t('monitoring.flows.title')} style={{ marginBottom: 16 }}>
          <Row gutter={[24, 16]}>
            <Col xs={8} sm={6} md={4}>
              <Statistic title={t('monitoring.flows.total')} value={flowStats?.total24h ?? '-'} />
            </Col>
            <Col xs={8} sm={6} md={4}>
              <Statistic
                title={t('monitoring.flows.running')}
                value={flowStats?.running ?? '-'}
                valueStyle={{ color: '#6366F1' }}
              />
            </Col>
            <Col xs={8} sm={6} md={4}>
              <Statistic
                title={t('monitoring.flows.completed')}
                value={flowStats?.completed ?? '-'}
                valueStyle={{ color: '#22C55E' }}
              />
            </Col>
            <Col xs={8} sm={6} md={4}>
              <Statistic
                title={t('monitoring.flows.failed')}
                value={flowStats?.failed ?? '-'}
                valueStyle={{ color: '#EF4444' }}
              />
            </Col>
            <Col xs={8} sm={6} md={4}>
              <Statistic
                title={t('monitoring.flows.avgDuration')}
                value={formatDuration(flowStats?.avgDurationMs ?? null)}
              />
            </Col>
            <Col xs={8} sm={6} md={4}>
              <Statistic
                title={t('monitoring.flows.allTime')}
                value={flowStats?.totalAllTime ?? '-'}
              />
            </Col>
          </Row>
        </Card>

        {/* Connection Health */}
        <Card type="inner" title={t('monitoring.health.title')}>
          <Row gutter={[24, 16]}>
            <Col xs={12} sm={8}>
              <Space>
                <Badge
                  status={healthStatus?.database === 'UP' ? 'success' : 'error'}
                />
                <Text strong>{t('monitoring.health.database')}</Text>
                {healthStatus?.database === 'UP' ? (
                  <CheckCircleOutlined style={{ color: '#22C55E' }} />
                ) : (
                  <CloseCircleOutlined style={{ color: '#EF4444' }} />
                )}
                {healthStatus && (
                  <Text type="secondary">({healthStatus.dbResponseMs}ms)</Text>
                )}
              </Space>
            </Col>
            <Col xs={12} sm={8}>
              <Space>
                <Badge
                  status={healthStatus?.redis === 'UP' ? 'success' : 'error'}
                />
                <Text strong>{t('monitoring.health.redis')}</Text>
                {healthStatus?.redis === 'UP' ? (
                  <CheckCircleOutlined style={{ color: '#22C55E' }} />
                ) : (
                  <CloseCircleOutlined style={{ color: '#EF4444' }} />
                )}
                {healthStatus && (
                  <Text type="secondary">({healthStatus.redisResponseMs}ms)</Text>
                )}
              </Space>
            </Col>
          </Row>
        </Card>
      </Card>
    </div>
  )
}
