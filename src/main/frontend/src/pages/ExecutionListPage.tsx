import { useEffect, useState, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Card, Table, Tag, Button, Space, message, Typography, Input, Select, Alert, Modal, Empty, Tooltip } from 'antd'
import {
  ReloadOutlined,
  EyeOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  StopOutlined,
  SearchOutlined,
  PauseCircleOutlined,
  DeleteOutlined,
  RetweetOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import { executionApi, ExecutionResponse } from '../api/execution'
import { useAllExecutions } from '../hooks/useExecutionMonitor'
import apiClient from '../api/client'
import logger from '../utils/logger'
import { extractApiError } from '../utils/errorMessages'
import { getLocale, formatDuration } from '../utils/locale'

const { Text } = Typography

const statusColors: Record<string, string> = {
  pending: 'default',
  running: 'processing',
  completed: 'success',
  failed: 'error',
  cancelled: 'warning',
  waiting: 'orange',
  paused: 'orange',
}

const statusIcons: Record<string, React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  running: <LoadingOutlined />,
  completed: <CheckCircleOutlined />,
  failed: <CloseCircleOutlined />,
  cancelled: <StopOutlined />,
  waiting: <PauseCircleOutlined />,
  paused: <PauseCircleOutlined />,
}

const STATUS_OPTIONS = ['all', 'pending', 'running', 'completed', 'failed', 'cancelled', 'waiting', 'paused'] as const

export default function ExecutionListPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { t } = useTranslation()
  const [executions, setExecutions] = useState<ExecutionResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const initialStatus = searchParams.get('status') || 'all'
  const [statusFilter, setStatusFilter] = useState<string>(initialStatus)
  const [searchValue, setSearchValue] = useState<string>('')
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [batchDeleting, setBatchDeleting] = useState(false)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const fetchRequestIdRef = useRef(0)

  // Real-time updates from WebSocket
  const { executions: realtimeExecutions, isConnected } = useAllExecutions()

  const loadExecutions = useCallback(async (page = 1, size = 20, status?: string, search?: string) => {
    const requestId = ++fetchRequestIdRef.current
    setLoading(true)
    try {
      const data = await executionApi.list(page - 1, size, status, search)
      if (requestId !== fetchRequestIdRef.current) return // Stale response
      setExecutions(data.content)
      setPagination({
        current: data.number + 1,
        pageSize: data.size,
        total: data.totalElements,
      })
    } catch (error) {
      if (requestId !== fetchRequestIdRef.current) return
      logger.error('Failed to load executions:', error)
      message.error(t('common.loadFailed'))
    } finally {
      if (requestId === fetchRequestIdRef.current) setLoading(false)
    }
  }, [t])

  // Initial load with URL params
  useEffect(() => {
    loadExecutions(1, 20, initialStatus)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleStatusChange = (value: string) => {
    setStatusFilter(value)
    loadExecutions(1, pagination.pageSize, value, searchValue)
  }

  const handleSearch = (value: string) => {
    setSearchValue(value)
    loadExecutions(1, pagination.pageSize, statusFilter, value)
  }

  // Merge real-time updates with loaded data
  useEffect(() => {
    if (realtimeExecutions.length > 0) {
      setExecutions((prev) =>
        prev.map((exec) => {
          const realtime = realtimeExecutions.find((r) => r.id === exec.id)
          if (realtime) {
            return { ...exec, ...realtime }
          }
          return exec
        })
      )
    }
  }, [realtimeExecutions])

  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) return
    Modal.confirm({
      title: t('execution.batchDeleteConfirm', { count: selectedRowKeys.length }),
      onOk: async () => {
        setBatchDeleting(true)
        try {
          const resp = await apiClient.delete('/executions/batch', { data: { ids: selectedRowKeys } })
          message.success(t('execution.batchDeleteSuccess', { count: resp.data.deleted }))
          setSelectedRowKeys([])
          loadExecutions(pagination.current, pagination.pageSize, statusFilter, searchValue)
        } catch (error) {
          message.error(extractApiError(error, t('common.deleteFailed')))
        } finally {
          setBatchDeleting(false)
        }
      },
    })
  }

  const handleCancel = async (id: string) => {
    setActionLoading(id)
    try {
      await executionApi.cancel(id)
      message.success(t('execution.cancelSuccess'))
      loadExecutions(pagination.current, pagination.pageSize, statusFilter, searchValue)
    } catch (error) {
      message.error(extractApiError(error, t('execution.cancelFailed')))
    } finally {
      setActionLoading(null)
    }
  }

  const handleRetry = async (id: string) => {
    setActionLoading(id)
    try {
      const result = await executionApi.retry(id)
      message.success(t('execution.retrySuccess'))
      navigate(`/executions/${result.id}`)
    } catch (error) {
      message.error(extractApiError(error, t('execution.retryFailed')))
    } finally {
      setActionLoading(null)
    }
  }

  const handleTableChange = (newPagination: TablePaginationConfig) => {
    loadExecutions(newPagination.current, newPagination.pageSize, statusFilter, searchValue)
  }

  const columns: ColumnsType<ExecutionResponse> = [
    {
      title: t('execution.executionId'),
      dataIndex: 'id',
      key: 'id',
      width: 120,
      render: (id: string) => (
        <Text copyable={{ text: id }} style={{ fontFamily: 'monospace' }}>
          {id.substring(0, 8)}
        </Text>
      ),
    },
    {
      title: t('execution.flowName'),
      dataIndex: 'flowName',
      key: 'flowName',
      render: (name: string, record: ExecutionResponse) => {
        if (!name) return '-'
        if (record.flowId) {
          return (
            <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/flows/${record.flowId}/edit`)}>
              {name}
            </Button>
          )
        }
        return name
      },
    },
    {
      title: t('flow.version'),
      dataIndex: 'flowVersion',
      key: 'flowVersion',
      width: 80,
      render: (version: string) => version || '-',
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => {
        const normalized = status?.toLowerCase() || 'unknown'
        return (
          <Tag icon={statusIcons[normalized]} color={statusColors[normalized] || 'default'}>
            {t(`execution.${normalized}`, { defaultValue: status?.toUpperCase() || 'UNKNOWN' })}
          </Tag>
        )
      },
    },
    {
      title: t('execution.startTime'),
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 180,
      render: (time: string) => (time ? new Date(time).toLocaleString(getLocale()) : '-'),
    },
    {
      title: t('execution.duration'),
      dataIndex: 'durationMs',
      key: 'durationMs',
      width: 100,
      render: (ms: number) => formatDuration(ms),
    },
    {
      title: t('execution.triggerType'),
      dataIndex: 'triggerType',
      key: 'triggerType',
      width: 120,
      render: (type: string) => {
        if (!type) return '-'
        const colorMap: Record<string, string> = {
          manual: 'blue',
          scheduler: 'purple',
          webhook: 'cyan',
          api: 'geekblue',
          retry: 'orange',
        }
        return <Tag color={colorMap[type?.toLowerCase()] || 'default'}>{t(`execution.trigger_${type?.toLowerCase()}`, { defaultValue: type })}</Tag>
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 200,
      render: (_, record) => {
        const status = record.status?.toLowerCase()
        return (
          <Space size={0}>
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/executions/${record.id}`)}>
              {t('execution.view')}
            </Button>
            {(status === 'running' || status === 'pending') && (
              <Tooltip title={t('execution.cancel')}>
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<StopOutlined />}
                  loading={actionLoading === record.id}
                  onClick={() => handleCancel(record.id)}
                  aria-label={t('execution.cancel')}
                />
              </Tooltip>
            )}
            {status === 'failed' && (
              <Tooltip title={t('execution.retry')}>
                <Button
                  type="link"
                  size="small"
                  icon={<RetweetOutlined />}
                  loading={actionLoading === record.id}
                  onClick={() => handleRetry(record.id)}
                  aria-label={t('execution.retry')}
                />
              </Tooltip>
            )}
            <Button
              type="link"
              danger
              icon={<DeleteOutlined />}
              size="small"
              onClick={() => {
                Modal.confirm({
                  title: t('execution.deleteConfirm'),
                  okType: 'danger',
                  onOk: async () => {
                    try {
                      await apiClient.delete(`/executions/batch`, { data: { ids: [record.id] } })
                      message.success(t('common.success'))
                      loadExecutions(pagination.current, pagination.pageSize, statusFilter, searchValue)
                    } catch (error) {
                      message.error(extractApiError(error, t('common.deleteFailed')))
                    }
                  },
                })
              }}
              aria-label={t('common.delete')}
            />
          </Space>
        )
      },
    },
  ]

  return (
    <Card
      title={
        <Space>
          <span>{t('execution.title')}</span>
          {isConnected && <Tag color="green">{t('execution.realtime')}</Tag>}
        </Space>
      }
      extra={
        <Space>
          <Input.Search
            placeholder={t('execution.searchPlaceholder')}
            aria-label={t('execution.searchPlaceholder')}
            allowClear
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onSearch={handleSearch}
            style={{ width: 220 }}
            enterButton={<SearchOutlined />}
          />
          <Select
            value={statusFilter}
            onChange={handleStatusChange}
            style={{ width: 150 }}
          >
            {STATUS_OPTIONS.map((status) => (
              <Select.Option key={status} value={status}>
                {status === 'all' ? t('execution.allStatuses') : t(`execution.${status}`)}
              </Select.Option>
            ))}
          </Select>
          <Button icon={<ReloadOutlined />} onClick={() => loadExecutions(pagination.current, pagination.pageSize, statusFilter, searchValue)}>
            {t('common.refresh')}
          </Button>
        </Space>
      }
    >
      {selectedRowKeys.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            <Space>
              <span>{t('execution.selectedCount', { count: selectedRowKeys.length })}</span>
              <Button size="small" danger icon={<DeleteOutlined />} loading={batchDeleting} onClick={handleBatchDelete}>
                {t('execution.batchDelete')}
              </Button>
              <Button size="small" onClick={() => setSelectedRowKeys([])}>
                {t('execution.clearSelection')}
              </Button>
            </Space>
          }
        />
      )}
      <Table
        columns={columns}
        dataSource={executions}
        rowKey="id"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t('execution.noExecutions')}
            />
          )
        }}
        pagination={pagination}
        onChange={handleTableChange}
        size="middle"
      />
    </Card>
  )
}
