import { useCallback, useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Typography, Tag, Skeleton, Button, Space, Steps, Result } from 'antd'
import List from '../components/common/SimpleList'
import { message } from '../utils/feedback'
import { extractApiError } from '../utils/errorMessages'
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
  BulbOutlined,
  GiftOutlined,
  SyncOutlined,
  BookOutlined,
  SafetyOutlined,
  ApiOutlined,
  KeyOutlined,
  ReloadOutlined,
  PauseCircleOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { dashboardApi, type DashboardStats } from '../api/dashboard'
import { executionApi, type ExecutionResponse } from '../api/execution'
import { activityApi, type UserActivity } from '../api/activity'
import { getLocale } from '../utils/locale'
import { useAuthStore } from '../stores/authStore'
import CloudImportSection from '../components/CloudImportSection'
import AiPromptHero from '../components/ai/AiPromptHero'

const { Title, Text, Paragraph } = Typography

export default function DashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const userName = useAuthStore((s) => s.user?.name)
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recentActivities, setRecentActivities] = useState<UserActivity[]>([])
  const [recentExecutions, setRecentExecutions] = useState<ExecutionResponse[]>([])
  const [waitingExecutions, setWaitingExecutions] = useState<ExecutionResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const [statsRes, execRes, activitiesRes, waitingRes] = await Promise.allSettled([
        dashboardApi.getStats(),
        executionApi.list(0, 5),
        activityApi.listMy(0, 5),
        executionApi.list(0, 5, 'waiting'),
      ])

      if (statsRes.status === 'fulfilled') {
        setStats(statsRes.value)
      }

      if (execRes.status === 'fulfilled') {
        const executions = execRes.value.content || []
        setRecentExecutions(executions)
      }

      if (activitiesRes.status === 'fulfilled') {
        const activities = (activitiesRes.value.content || []).slice(0, 5)
        setRecentActivities(activities)
      }

      if (waitingRes.status === 'fulfilled') {
        setWaitingExecutions(waitingRes.value.content || [])
      }

      const results = [statsRes, execRes, activitiesRes, waitingRes]
      const allFailed = statsRes.status === 'rejected' && execRes.status === 'rejected' && activitiesRes.status === 'rejected'
      const anyFailed = results.some((res) => res.status === 'rejected')
      if (allFailed) {
        message.error(t('dashboard.loadFailed'))
        setLoadError(true)
      } else if (anyFailed) {
        message.warning(t('dashboard.partialLoadFailed'))
      }
    } catch (error) {
      message.error(extractApiError(error, t('dashboard.loadFailed')))
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  const statusColors: Record<string, string> = {
    completed: 'success',
    failed: 'error',
    running: 'processing',
    pending: 'default',
    cancelled: 'warning',
    waiting: 'orange',
    paused: 'orange',
  }

  const statusIcons: Record<string, React.ReactNode> = {
    completed: <CheckCircleOutlined />,
    failed: <CloseCircleOutlined />,
    running: <SyncOutlined spin />,
    pending: <ClockCircleOutlined />,
    cancelled: <CloseCircleOutlined />,
    waiting: <PauseCircleOutlined />,
    paused: <PauseCircleOutlined />,
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

  if (loadError) {
    return (
      <Result
        status="warning"
        title={t('dashboard.loadFailed')}
        extra={
          <Button type="primary" icon={<ReloadOutlined />} onClick={loadDashboard}>
            {t('common.retry')}
          </Button>
        }
      />
    )
  }

  // Welcome panel for new users (no flows yet)
  if (stats && stats.totalFlows === 0) {
    return (
      <div>
        <AiPromptHero />
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--color-bg-primary) 0%, var(--color-bg-secondary) 50%, var(--color-bg-primary) 100%)',
            border: '1px solid rgba(192, 101, 59, 0.25)',
            borderRadius: 16,
            marginBottom: 24,
          }}
        >
          <div style={{ textAlign: 'center', padding: '32px 16px' }}>
            <RocketOutlined style={{ fontSize: 64, color: 'var(--color-primary)', marginBottom: 16 }} />
            <Title level={2} style={{ color: 'var(--color-text-primary)', margin: '0 0 8px 0' }}>
              {t('dashboard.welcomeTitle')}
            </Title>
            <Paragraph style={{ color: 'var(--color-text-secondary)', fontSize: 16, marginBottom: 8 }}>
              {t('dashboard.welcomeSubtitle')}
            </Paragraph>
            <Paragraph style={{ color: 'var(--color-text-tertiary)', fontSize: 14, marginBottom: 32, maxWidth: 500, margin: '0 auto 32px' }}>
              {t('dashboard.welcomeExplanation')}
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
                  icon: <BulbOutlined style={{ color: 'var(--color-primary)' }} />,
                },
                {
                  title: <span style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.step2Title')}</span>,
                  description: <span style={{ color: 'var(--color-text-secondary)' }}>{t('dashboard.step2Desc')}</span>,
                  icon: <ThunderboltOutlined style={{ color: 'var(--color-ai)' }} />,
                },
                {
                  title: <span style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.step3Title')}</span>,
                  description: <span style={{ color: 'var(--color-text-secondary)' }}>{t('dashboard.step3Desc')}</span>,
                  icon: <GiftOutlined style={{ color: 'var(--color-success)' }} />,
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
                icon={<BookOutlined />}
                onClick={() => navigate('/templates')}
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
                  background: 'rgba(141, 123, 176, 0.12)',
                  borderColor: 'rgba(141, 123, 176, 0.5)',
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
            <Card hoverable size="small" onClick={() => navigate('/credentials')} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate('/credentials') }} tabIndex={0} role="button" aria-label={t('dashboard.quickCredentials')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <KeyOutlined style={{ fontSize: 28, color: 'var(--color-warning)', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickCredentials')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickCredentialsDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/services')} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate('/services') }} tabIndex={0} role="button" aria-label={t('dashboard.quickServices')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <ApiOutlined style={{ fontSize: 28, color: 'var(--color-info)', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickServices')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickServicesDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/webhooks')} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate('/webhooks') }} tabIndex={0} role="button" aria-label={t('dashboard.quickWebhooks')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <ThunderboltOutlined style={{ fontSize: 28, color: 'var(--color-primary)', marginBottom: 8 }} />
              <div><Text strong style={{ color: 'var(--color-text-primary)' }}>{t('dashboard.quickWebhooks')}</Text></div>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('dashboard.quickWebhooksDesc')}</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable size="small" onClick={() => navigate('/settings/account')} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate('/settings/account') }} tabIndex={0} role="button" aria-label={t('dashboard.quickSecurity')} style={{ cursor: 'pointer', textAlign: 'center' }}>
              <SafetyOutlined style={{ fontSize: 28, color: 'var(--color-ai)', marginBottom: 8 }} />
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
          {userName ? t('dashboard.welcomeBack', { name: userName }) : t('dashboard.title')}
        </Title>
      </Space>

      <AiPromptHero />

      {/* 需要人工處理的執行（等待批准/輸入）— 一眼看到哪裡卡住 */}
      {waitingExecutions.length > 0 && (
        <Card
          size="small"
          style={{
            marginBottom: 16,
            borderColor: 'rgba(200, 155, 60, 0.5)',
            background: 'rgba(200, 155, 60, 0.08)',
          }}
        >
          <Space wrap>
            <PauseCircleOutlined style={{ color: 'var(--color-warning)' }} />
            <Text strong style={{ color: 'var(--color-text-primary)' }}>
              {t('dashboard.needsAttention', { total: waitingExecutions.length })}
            </Text>
            {waitingExecutions.slice(0, 3).map((e) => (
              <Button key={e.id} type="link" size="small" onClick={() => navigate(`/executions/${e.id}`)}>
                {e.flowName || e.id.substring(0, 8)}
              </Button>
            ))}
            <Button type="link" size="small" onClick={() => navigate('/executions?status=waiting')}>
              {t('dashboard.viewAll')}
            </Button>
          </Space>
        </Card>
      )}

      <Space style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/flows')}>
            {t('flow.newFlow')}
          </Button>
          <Button icon={<ThunderboltOutlined />} onClick={() => navigate('/ai-assistant')} style={{ background: 'var(--color-ai)', borderColor: 'var(--color-ai)', color: '#fff' }}>
            {t('nav.aiAssistant')}
          </Button>
        </Space>
      </Space>

      {/* Stats Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/flows')} style={{ cursor: 'pointer' }} role="button" tabIndex={0} aria-label={t('dashboard.totalFlows')}>
            <Statistic
              title={t('dashboard.totalFlows')}
              value={stats?.totalFlows || 0}
              prefix={<ApartmentOutlined style={{ color: 'var(--color-primary)' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/executions')} style={{ cursor: 'pointer' }} role="button" tabIndex={0} aria-label={t('dashboard.totalExecutions')}>
            <Statistic
              title={t('dashboard.totalExecutions')}
              value={stats?.totalExecutions || 0}
              prefix={<PlayCircleOutlined style={{ color: 'var(--color-info)' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/executions?status=completed')} style={{ cursor: 'pointer' }} role="button" tabIndex={0} aria-label={t('dashboard.successful')}>
            <Statistic
              title={t('dashboard.successful')}
              value={stats?.successfulExecutions || 0}
              prefix={<CheckCircleOutlined style={{ color: 'var(--color-success)' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable onClick={() => navigate('/executions?status=failed')} style={{ cursor: 'pointer' }} role="button" tabIndex={0} aria-label={t('dashboard.failed')}>
            <Statistic
              title={t('dashboard.failed')}
              value={stats?.failedExecutions || 0}
              prefix={<CloseCircleOutlined style={{ color: 'var(--color-danger)' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* Running indicator */}
      {stats && stats.runningExecutions > 0 && (
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col span={24}>
            <Card size="small" style={{ borderColor: 'rgba(110, 143, 166, 0.4)', background: 'rgba(110, 143, 166, 0.08)' }}>
              <Space>
                <SyncOutlined spin style={{ color: 'var(--color-info)' }} />
                <Text style={{ color: 'var(--color-text-secondary)' }}>
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
                    title={
                      item.flowName && item.flowId ? (
                        <Button type="link" size="small" style={{ padding: 0, height: 'auto' }} onClick={() => navigate(`/flows/${item.flowId}/edit`)}>
                          {item.flowName}
                        </Button>
                      ) : (
                        item.flowName || item.id.substring(0, 8)
                      )
                    }
                    description={
                      <Space>
                        <Tag icon={statusIcons[item.status]} color={statusColors[item.status] || 'default'}>{t(`execution.${item.status}`, { defaultValue: item.status })}</Tag>
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
              locale={{ emptyText: t('dashboard.noActivities') }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={<Tag>{t(`activityType.${item.activityType}`, { defaultValue: item.activityType })}</Tag>}
                    description={
                      <Space>
                        {/* Older execution activities stored the flow name only inside details */}
                        <Text>{item.resourceName || (typeof item.details?.flowName === 'string' ? item.details.flowName : '-')}</Text>
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

      {/* Cloud Import */}
      <CloudImportSection />
    </div>
  )
}
