import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, Card, Col, Empty, Popconfirm, Row, Space, Spin, Tag, Tooltip, Typography } from 'antd'
import { message, modal } from '../utils/feedback'
import {
  CaretRightOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  ExportOutlined,
  FileTextOutlined,
  PauseOutlined,
  PlusOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { appsApi, type AppsAvailability, type AppStatus, type HostedAppItem } from '../api/apps'
import AppCreateModal from '../components/apps/AppCreateModal'
import AppRedeployModal from '../components/apps/AppRedeployModal'
import AppLogsDrawer from '../components/apps/AppLogsDrawer'
import { extractApiError } from '../utils/errorMessages'

const { Title, Text, Paragraph } = Typography

const POLL_INTERVAL_MS = 2000

/**
 * 小應用：上傳自己的 docker-compose / Dockerfile 專案 zip，
 * 平台幫你在沙盒容器裡跑起來，掛上自己的子網域。
 */
export default function AppsPage() {
  const { t } = useTranslation()
  const [availability, setAvailability] = useState<AppsAvailability | null>(null)
  const [apps, setApps] = useState<HostedAppItem[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [redeployApp, setRedeployApp] = useState<HostedAppItem | null>(null)
  const [logsApp, setLogsApp] = useState<HostedAppItem | null>(null)
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = useCallback(async () => {
    try {
      setApps(await appsApi.list())
    } catch (error: unknown) {
      message.error(extractApiError(error, t('apps.loadFailed')))
    }
  }, [t])

  useEffect(() => {
    const init = async () => {
      setLoading(true)
      try {
        const result = await appsApi.availability()
        setAvailability(result)
        if (result.enabled) await load()
      } catch (error: unknown) {
        message.error(extractApiError(error, t('apps.loadFailed')))
      } finally {
        setLoading(false)
      }
    }
    void init()
  }, [load, t])

  // 有應用在部署中就每 2 秒輪詢，直到塵埃落定
  useEffect(() => {
    const deploying = apps.some((app) => app.status === 'deploying')
    if (deploying && pollTimer.current == null) {
      pollTimer.current = setInterval(() => void load(), POLL_INTERVAL_MS)
    }
    if (!deploying && pollTimer.current != null) {
      clearInterval(pollTimer.current)
      pollTimer.current = null
    }
    return () => {
      if (pollTimer.current != null) {
        clearInterval(pollTimer.current)
        pollTimer.current = null
      }
    }
  }, [apps, load])

  const appUrl = (app: HostedAppItem): string | null => {
    if (availability?.baseDomain) {
      return `https://${app.slug}.${availability.baseDomain}/`
    }
    if (app.hostPort != null) {
      return `http://${window.location.hostname}:${app.hostPort}/`
    }
    return null
  }

  const handleStop = async (app: HostedAppItem) => {
    try {
      await appsApi.stop(app.id)
      message.success(t('apps.stopped', { name: app.name }))
      await load()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('apps.actionFailed')))
    }
  }

  const handleStart = async (app: HostedAppItem) => {
    try {
      await appsApi.start(app.id)
      message.success(t('apps.started', { name: app.name }))
      await load()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('apps.actionFailed')))
    }
  }

  // 刪除採兩段確認：Popconfirm 之後再彈出 Modal 提醒容器將一併移除
  const confirmDelete = (app: HostedAppItem) => {
    modal.confirm({
      title: t('apps.deleteTitle', { name: app.name }),
      content: t('apps.deleteWarning'),
      okText: t('apps.deleteOk'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await appsApi.remove(app.id)
          message.success(t('apps.deleted'))
          await load()
        } catch (error: unknown) {
          message.error(extractApiError(error, t('apps.deleteFailed')))
        }
      },
    })
  }

  const statusTag = (app: HostedAppItem) => {
    const map: Record<AppStatus, ReactNode> = {
      running: <Tag color="green">{t('apps.statusRunning')}</Tag>,
      deploying: (
        <Tag color="blue" icon={<SyncOutlined spin />}>
          {t('apps.statusDeploying')}
        </Tag>
      ),
      failed: (
        <Tooltip title={app.errorMessage ?? t('apps.statusFailed')}>
          <Tag color="red">{t('apps.statusFailed')}</Tag>
        </Tooltip>
      ),
      stopped: <Tag>{t('apps.statusStopped')}</Tag>,
      created: <Tag>{t('apps.statusCreated')}</Tag>,
    }
    return map[app.status] ?? <Tag>{app.status}</Tag>
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!availability?.enabled) {
    return (
      <div style={{ maxWidth: 720, margin: '0 auto' }}>
        <Title level={3}>{t('apps.title')}</Title>
        <Card>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <>
                <Paragraph strong style={{ marginBottom: 4 }}>
                  {t('apps.disabledTitle')}
                </Paragraph>
                <Text type="secondary">{t('apps.disabledHint')}</Text>
              </>
            }
          />
        </Card>
      </div>
    )
  }

  return (
    <div>
      <Space
        align="start"
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
      >
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            {t('apps.title')}
          </Title>
          <Text type="secondary">{t('apps.subtitle')}</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          {t('apps.create')}
        </Button>
      </Space>

      {apps.length === 0 ? (
        <Card>
          <Empty
            image={<CloudUploadOutlined style={{ fontSize: 48, opacity: 0.45 }} />}
            description={
              <>
                <Paragraph strong style={{ marginBottom: 4 }}>
                  {t('apps.empty')}
                </Paragraph>
                <Text type="secondary">{t('apps.emptyHint')}</Text>
              </>
            }
          >
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              {t('apps.create')}
            </Button>
          </Empty>
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {apps.map((app) => {
            const url = appUrl(app)
            return (
              <Col key={app.id} xs={24} sm={12} lg={8}>
                <Card
                  title={
                    <Space>
                      <span>{app.name}</span>
                      {statusTag(app)}
                    </Space>
                  }
                  actions={[
                    <Tooltip key="open" title={t('apps.open')}>
                      <Button
                        type="text"
                        icon={<ExportOutlined />}
                        disabled={url == null || app.status !== 'running'}
                        onClick={() => url && window.open(url, '_blank', 'noopener')}
                      />
                    </Tooltip>,
                    app.status === 'running' ? (
                      <Tooltip key="stop" title={t('apps.stop')}>
                        <Button
                          type="text"
                          icon={<PauseOutlined />}
                          onClick={() => void handleStop(app)}
                        />
                      </Tooltip>
                    ) : (
                      <Tooltip key="start" title={t('apps.start')}>
                        <Button
                          type="text"
                          icon={<CaretRightOutlined />}
                          disabled={app.status !== 'stopped'}
                          onClick={() => void handleStart(app)}
                        />
                      </Tooltip>
                    ),
                    <Tooltip key="logs" title={t('apps.logs')}>
                      <Button
                        type="text"
                        icon={<FileTextOutlined />}
                        disabled={app.status === 'created'}
                        onClick={() => setLogsApp(app)}
                      />
                    </Tooltip>,
                    <Tooltip key="redeploy" title={t('apps.redeploy')}>
                      <Button
                        type="text"
                        icon={<SyncOutlined />}
                        disabled={app.status === 'deploying'}
                        onClick={() => setRedeployApp(app)}
                      />
                    </Tooltip>,
                    <Popconfirm
                      key="delete"
                      title={t('apps.deleteConfirm')}
                      okText={t('common.confirm')}
                      cancelText={t('common.cancel')}
                      onConfirm={() => confirmDelete(app)}
                    >
                      <Tooltip title={t('common.delete')}>
                        <Button type="text" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
                      </Tooltip>
                    </Popconfirm>,
                  ]}
                >
                  <Space orientation="vertical" size={4} style={{ width: '100%' }}>
                    <Space size={8}>
                      <Tag>{app.appType}</Tag>
                      <Text type="secondary" copyable={{ text: app.slug }}>
                        {app.slug}
                      </Text>
                    </Space>
                    {url ? (
                      <Text ellipsis copyable={{ text: url }} style={{ fontSize: 13 }}>
                        {url}
                      </Text>
                    ) : (
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        {t('apps.noUrlYet')}
                      </Text>
                    )}
                    {app.status === 'failed' && app.errorMessage && (
                      <Text type="danger" ellipsis={{ tooltip: app.errorMessage }}>
                        {app.errorMessage}
                      </Text>
                    )}
                  </Space>
                </Card>
              </Col>
            )
          })}
        </Row>
      )}

      <AppCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onDeployed={() => {
          setCreateOpen(false)
          void load()
        }}
      />
      <AppRedeployModal
        app={redeployApp}
        onClose={() => setRedeployApp(null)}
        onDeployed={() => {
          setRedeployApp(null)
          void load()
        }}
      />
      <AppLogsDrawer app={logsApp} onClose={() => setLogsApp(null)} />
    </div>
  )
}
