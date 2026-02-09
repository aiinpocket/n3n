import { useEffect, useState, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Statistic,
  Table,
  Tag,
  message,
  Modal,
  Row,
  Col,
  Typography,
  Spin,
  Result,
} from 'antd'
import {
  ClearOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { housekeepingApi, HousekeepingStats, HousekeepingJob } from '../api/housekeeping'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Text } = Typography

export default function HousekeepingPage() {
  const { t } = useTranslation()
  const [stats, setStats] = useState<HousekeepingStats | null>(null)
  const [jobs, setJobs] = useState<HousekeepingJob[]>([])
  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)
  const [cleaningHistory, setCleaningHistory] = useState(false)
  const [totalJobs, setTotalJobs] = useState(0)
  const [jobPage, setJobPage] = useState(0)
  const [jobLoading, setJobLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoadError(null)
    try {
      const [statsData, jobsData] = await Promise.all([
        housekeepingApi.getStats(),
        housekeepingApi.getJobHistory(0, 10),
      ])
      setStats(statsData)
      setJobs(jobsData.content)
      setTotalJobs(jobsData.totalElements)
    } catch (error) {
      setLoadError(extractApiError(error, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleRunCleanup = async () => {
    Modal.confirm({
      title: t('housekeeping.confirmRun'),
      content: t('housekeeping.confirmRunDesc'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setRunning(true)
        try {
          const job = await housekeepingApi.runCleanup()
          message.success(t('housekeeping.runSuccess', {
            processed: job.recordsProcessed,
            archived: job.recordsArchived,
            deleted: job.recordsDeleted,
          }))
          loadData()
        } catch (error) {
          message.error(extractApiError(error, t('housekeeping.runFailed')))
        } finally {
          setRunning(false)
        }
      },
    })
  }

  const handleCleanupHistory = async () => {
    Modal.confirm({
      title: t('housekeeping.confirmCleanHistory'),
      content: t('housekeeping.confirmCleanHistoryDesc'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setCleaningHistory(true)
        try {
          const result = await housekeepingApi.cleanupHistory()
          message.success(t('housekeeping.cleanHistorySuccess', { count: result.recordsDeleted }))
          loadData()
        } catch (error) {
          message.error(extractApiError(error, t('common.operationFailed')))
        } finally {
          setCleaningHistory(false)
        }
      },
    })
  }

  const loadJobPage = async (page: number) => {
    setJobLoading(true)
    try {
      const data = await housekeepingApi.getJobHistory(page, 10)
      setJobs(data.content)
      setTotalJobs(data.totalElements)
      setJobPage(page)
    } catch (error) {
      message.error(extractApiError(error, t('common.loadFailed')))
    } finally {
      setJobLoading(false)
    }
  }

  const statusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED': return <CheckCircleOutlined style={{ color: 'var(--color-success)' }} />
      case 'FAILED': return <CloseCircleOutlined style={{ color: 'var(--color-error)' }} />
      case 'RUNNING': return <LoadingOutlined style={{ color: 'var(--color-info)' }} />
      default: return <ClockCircleOutlined style={{ color: 'var(--color-warning)' }} />
    }
  }

  const columns = [
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => (
        <Tag icon={statusIcon(status)} color={
          status === 'COMPLETED' ? 'success' :
          status === 'FAILED' ? 'error' :
          status === 'RUNNING' ? 'processing' : 'warning'
        }>
          {status}
        </Tag>
      ),
    },
    {
      title: t('housekeeping.processed'),
      dataIndex: 'recordsProcessed',
      key: 'recordsProcessed',
      width: 100,
    },
    {
      title: t('housekeeping.archived'),
      dataIndex: 'recordsArchived',
      key: 'recordsArchived',
      width: 100,
    },
    {
      title: t('housekeeping.deleted'),
      dataIndex: 'recordsDeleted',
      key: 'recordsDeleted',
      width: 100,
    },
    {
      title: t('housekeeping.startedAt'),
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (val: string) => val ? new Date(val).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('housekeeping.completedAt'),
      dataIndex: 'completedAt',
      key: 'completedAt',
      render: (val: string | null) => val ? new Date(val).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.error'),
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true,
      render: (val: string | null) => val ? <Text type="danger">{val}</Text> : '-',
    },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (loadError) {
    return (
      <Result
        status="error"
        title={t('common.loadFailed')}
        subTitle={loadError}
        extra={<Button type="primary" onClick={loadData}>{t('common.retry')}</Button>}
      />
    )
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card
        title={
          <Space>
            <ClearOutlined />
            <span>{t('housekeeping.title')}</span>
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadData}>
              {t('common.refresh')}
            </Button>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleRunCleanup}
              loading={running}
            >
              {t('housekeeping.runCleanup')}
            </Button>
            <Button
              danger
              icon={<DeleteOutlined />}
              onClick={handleCleanupHistory}
              loading={cleaningHistory}
            >
              {t('housekeeping.cleanHistory')}
            </Button>
          </Space>
        }
      >
        {stats && (
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={8} md={6}>
              <Statistic title={t('housekeeping.totalExecutions')} value={stats.totalExecutions} />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic title={t('housekeeping.archivedExecutions')} value={stats.archivedExecutions} />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic
                title={t('housekeeping.oldExecutions')}
                value={stats.oldExecutions}
                valueStyle={stats.oldExecutions > 0 ? { color: 'var(--color-warning)' } : undefined}
              />
            </Col>
            <Col xs={12} sm={8} md={6}>
              <Statistic title={t('housekeeping.retentionDays')} value={stats.retentionDays} suffix={t('housekeeping.days')} />
            </Col>
          </Row>
        )}
      </Card>

      <Card
        title={
          <Space>
            <HistoryOutlined />
            <span>{t('housekeeping.jobHistory')}</span>
          </Space>
        }
        size="small"
      >
        <Table
          columns={columns}
          dataSource={jobs}
          rowKey="id"
          loading={jobLoading}
          pagination={{
            current: jobPage + 1,
            total: totalJobs,
            pageSize: 10,
            showTotal: (total) => t('common.total', { count: total }),
            onChange: (p) => loadJobPage(p - 1),
          }}
          size="small"
        />
      </Card>
    </Space>
  )
}
