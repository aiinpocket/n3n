import { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Typography, List, Tag, Skeleton, Button, Space, Steps, message } from 'antd'
import {
  ApartmentOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  PlusOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
  RocketOutlined,
  AppstoreAddOutlined,
  SyncOutlined,
  FileTextOutlined,
  SafetyOutlined,
  ApiOutlined,
  KeyOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import apiClient from '../api/client'
import { getLocale } from '../utils/locale'

const { Title, Text, Paragraph } = Typography

interface StatsData {
  totalFlows: number
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  runningExecutions: number
}

interface RecentActivity {
  activityType: string
  targetName: string
  createdAt: string
}

interface RecentExecution {
  id: string
  flowName: string
  status: string
  startedAt: string
  durationMs: number | null
}

export default function DashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [stats, setStats] = useState<StatsData | null>(null)
  const [recentActivities, setRecentActivities] = useState<RecentActivity[]>([])
  const [recentExecutions, setRecentExecutions] = useState<RecentExecution[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const loadDashboard = async () => {
      setLoading(true)
      try {
        const [statsRes, execRes, activitiesRes] = await Promise.all([
          apiClient.get('/dashboard/stats'),
          apiClient.get('/executions', { params: { size: 5 } }),
          apiClient.get('/activities', { params: { size: 5 } }),
        ])

        setStats(statsRes.data)

        const executions = execRes.data.content || []
        setRecentExecutions(executions)

        const activities = (activitiesRes.data.content || activitiesRes.data || []).slice(0, 5)
        setRecentActivities(activities)
      } catch {
        message.error(t('dashboard.loadFailed'))
        setStats({
          totalFlows: 0,
          totalExecutions: 0,
          successfulExecutions: 0,
          failedExecutions: 0,
          runningExecutions: 0,
        })
        setRecentActivities([])
        setRecentExecutions([])
      } finally {
        setLoading(false)
      }
    }
    loadDashboard()
  }, [])

  const statusColors: Record<string, string> = {
    completed: 'success',
    failed: 'error',
    running: 'processing',
    pending: 'default',
    cancelled: 'warning',
  }

  if (loading) {
    return (
      <div>
        <Row gutter={[16, 16]}>
          {[1, 2, 3, 4].map((i) => (
            <Col xs={24} sm={12} lg={6} key={i}>
              <Card><Skeleton active paragraph={{ rows: 1 }} /></Card>
            </Col>
          ))}
        </Row>
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}><Card><Skeleton active paragraph={{ rows: 4 }} /></Card></Col>
          <Col xs={24} lg={12}><Card><Skeleton active paragraph={{ rows: 4 }} /></Card></Col>
        </Row>
      </div>
    )
  }

  // Welcome panel for new users (no flows yet)
  if (stats && stats.totalFlows === 0) {
    return (
      <div>
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--color-bg-primary) 0%, var(--color-bg-secondary) 50%, var(--color-bg-primary) 100%)',
            border: '1px solid rgba(20, 184, 166, 0.3)',
            borderRadius: 16,
            marginBottom: 24,
          }}
        >
          <div style={{ textAlign: 'center', padding: '32px 16px' }}>
            <RocketOutlined style={{ fontSize: 64, color: 'var(--color-primary)', marginBottom: 16 }} />
            <Title level={2} style={{ color: 'var(--color-text-primary)', margin: '0 0 8px 0' }}>
              {t('dashboard.welcomeTitle')}
            </Title>
            <Paragraph style={{ color: 'var(--color-text-secondary)', fontSize: 16, marginBottom: 32 }}>
              {t('dashboard.welcomeSubtitle')}
            </Paragraph>

            <Steps
              direction="horizontal"
              size="small"
              current={-1}
              style={{ maxWidth: 700, margin: '0 auto 40px' }}
              items={[
                {
                  title: <span style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.step1Title')}</span>,
                  description: <span style={{ color: 'var(--color-text-secondary)' }}>{t('dashboard.step1Desc')}</span>,
                  icon: <ApartmentOutlined style={{ color: 'var(--color-primary)' }} />,
                },
                {
                  title: <span style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.step2Title')}</span>,
                  description: <span style={{ color: 'var(--color-text-secondary)' }}>{t('dashboard.step2Desc')}</span>,
                  icon: <AppstoreAddOutlined style={{ color: 'var(--color-info)' }} />,
                },
                {
                  title: <span style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.step3Title')}</span>,
                  description: <span style={{ color: 'var(--color-text-secondary)' }}>{t('dashboard.step3Desc')}</span>,
                  icon: <PlayCircleOutlined style={{ color: 'var(--color-success)' }} />,
                },
              ]}
            />

            <Space size="middle" wrap>
              <Button
                type="primary"
                size="large"
                icon={<PlusOutlined />}
                onClick={() => navigate('/flows')}
                style={{ height: 48, paddingInline: 32, fontSize: 16 }}
              >
                {t('dashboard.createFirstFlow')}
              </Button>
              <Button
                size="large"
                icon={<FileTextOutlined />}
                onClick={() => navigate('/flows?tab=templates')}
                style={{
                  height: 48,
                  paddingInline: 24,
                  fontSize: 16,
                  background: 'var(--color-bg-elevated)',
                  borderColor: 'var(--color-border)',
                  color: 'var(--color-text-primary)',
                }}
              >
                {t('dashboard.startFromTemplate')}
              </Button>
              <Button
                size="large"
                icon={<ThunderboltOutlined />}
                onClick={() => navigate('/ai-assistant')}
                style={{
                  height: 48,
                  paddingInline: 24,
                  fontSize: 16,
                  background: 'rgba(139, 92, 246, 0.15)',
                  borderColor: 'rgba(139, 92, 246, 0.5)',
                  color: 'var(--color-ai)',
                }}
              >
                {t('dashboard.aiGenerate')}
              </Button>
            </Space>
          </div>
        </Card>

        {/* Quick Start Cards */}
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/credentials')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <KeyOutlined style={{ fontSize: 28, color: '#F59E0B', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickCredentials')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickCredentialsDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/services')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <ApiOutlined style={{ fontSize: 28, color: '#3B82F6', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickServices')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickServicesDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/webhooks')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <ThunderboltOutlined style={{ fontSize: 28, color: '#14B8A6', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickWebhooks')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickWebhooksDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/account')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <SafetyOutlined style={{ fontSize: 28, color: '#8B5CF6', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickSecurity')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickSecurityDesc')}</Text>
            </Card>
          </Col>
        </Row>
      </div>
    )
  }

  return (
    <div>
      <Space style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', width: '100%' }}>
        <Title level={3} style={{ color: 'var(--color-text-primary)', margin: 0 }}>
          {t('dashboard.title')}
        </Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/flows')}>
            {t('flow.newFlow')}
          </Button>
          <Button icon={<ThunderboltOutlined />} onClick={() => navigate('/ai-assistant')} style={{ background: '#8B5CF6', borderColor: '#8B5CF6', color: '#fff' }}>
            {t('nav.aiAssistant')}
          </Button>
        </Space>
      </Space>

      {/* Stats Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/flows')} style={{ cursor: 'pointer' }}>
            <Statistic
              title={t('dashboard.totalFlows')}
              value={stats?.totalFlows || 0}
              prefix={<ApartmentOutlined style={{ color: '#14B8A6' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/executions')} style={{ cursor: 'pointer' }}>
            <Statistic
              title={t('dashboard.totalExecutions')}
              value={stats?.totalExecutions || 0}
              prefix={<PlayCircleOutlined style={{ color: '#3B82F6' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('dashboard.successful')}
              value={stats?.successfulExecutions || 0}
              prefix={<CheckCircleOutlined style={{ color: '#22C55E' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('dashboard.failed')}
              value={stats?.failedExecutions || 0}
              prefix={<CloseCircleOutlined style={{ color: '#EF4444' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* Running indicator */}
      {stats && stats.runningExecutions > 0 && (
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col span={24}>
            <Card size="small" style={{ borderColor: 'rgba(59, 130, 246, 0.3)', background: 'rgba(59, 130, 246, 0.05)' }}>
              <Space>
                <SyncOutlined spin style={{ color: '#3B82F6' }} />
                <Text style={{ color: '#93C5FD' }}>
                  {t('dashboard.running')}: {stats.runningExecutions}
                </Text>
                <Button type="link" size="small" onClick={() => navigate('/executions?status=running')}>
                  {t('dashboard.viewAll')}
                </Button>
              </Space>
            </Card>
          </Col>
        </Row>
      )}

      {/* Recent Activity & Executions */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title={<><ClockCircleOutlined style={{ marginRight: 8 }} />{t('dashboard.recentExecutions')}</>}
            extra={<Button type="link" onClick={() => navigate('/executions')}>{t('dashboard.viewAll')}</Button>}
          >
            <List
              size="small"
              dataSource={recentExecutions}
              locale={{ emptyText: t('execution.noExecutions') }}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Button type="link" size="small" onClick={() => navigate(`/executions/${item.id}`)}>
                      {t('common.details')}
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={item.flowName || item.id.substring(0, 8)}
                    description={
                      <Space>
                        <Tag color={statusColors[item.status] || 'default'}>{t(`execution.${item.status}`, { defaultValue: item.status })}</Tag>
                        {item.durationMs != null && <Text type="secondary">{item.durationMs}ms</Text>}
                        <Text type="secondary">{item.startedAt ? new Date(item.startedAt).toLocaleString(getLocale()) : '-'}</Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title={<><HistoryOutlined style={{ marginRight: 8 }} />{t('dashboard.recentActivities')}</>}
            extra={<Button type="link" onClick={() => navigate('/activities')}>{t('dashboard.viewAll')}</Button>}
          >
            <List
              size="small"
              dataSource={recentActivities}
              locale={{ emptyText: t('nav.activities') }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={<Tag>{t(`activityType.${item.activityType}`, { defaultValue: item.activityType })}</Tag>}
                    description={
                      <Space>
                        <Text>{item.targetName || '-'}</Text>
                        <Text type="secondary">{item.createdAt ? new Date(item.createdAt).toLocaleString(getLocale()) : '-'}</Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
