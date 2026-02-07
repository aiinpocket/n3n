import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Tag, Button, Space, message, Typography, Input, Select, Alert, Modal, Empty } from 'antd'
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
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import { executionApi, ExecutionResponse } from '../api/execution'
import { useAllExecutions } from '../hooks/useExecutionMonitor'
import apiClient from '../api/client'
import logger from '../utils/logger'
import { getLocale, formatDuration } from '../utils/locale'

const { Text } = Typography

const statusColors: Record<string, string> = {
  pending: 'default',
  running: 'processing',
  completed: 'success',
  failed: 'error',
  cancelled: 'warning',
  waiting: 'orange',
}

const statusIcons: Record<string, React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  running: <LoadingOutlined />,
  completed: <CheckCircleOutlined />,
  failed: <CloseCircleOutlined />,
  cancelled: <StopOutlined />,
  waiting: <PauseCircleOutlined />,
}

const STATUS_OPTIONS = ['all', 'pending', 'running', 'completed', 'failed', 'cancelled', 'waiting'] as const

export default function ExecutionListPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [executions, setExecutions] = useState<ExecutionResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [searchValue, setSearchValue] = useState<string>('')
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [batchDeleting, setBatchDeleting] = useState(false)

  // Real-time updates from WebSocket
  const { executions: realtimeExecutions, isConnected } = useAllExecutions()

  const loadExecutions = useCallback(async (page = 1, pageSize = 20, status?: string, search?: string) => {
    setLoading(true)
    try {
      const data = await executionApi.list(page - 1, pageSize, status ?? statusFilter, search ?? searchValue)
      setExecutions(data.content)
      setPagination({
        current: data.number + 1,
        pageSize: data.size,
        total: data.totalElements,
      })
    } catch (error) {
      logger.error('Failed to load executions:', error)
      message.error(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [t, statusFilter, searchValue])

  useEffect(() => {
    loadExecutions()
  }, [loadExecutions])

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
            return { ...exec, status: realtime.status }
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
          loadExecutions()
        } catch {
          message.error(t('common.deleteFailed'))
        } finally {
          setBatchDeleting(false)
        }
      },
    })
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
      render: (name: string) => name || '-',
    },
    {
      title: t('flow.version'),
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (version: number) => version || '-',
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
            {status?.toUpperCase() || 'UNKNOWN'}
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
      width: 100,
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/executions/${record.id}`)}>
            {t('execution.view')}
          </Button>
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
                    loadExecutions()
                  } catch {
                    message.error(t('common.deleteFailed'))
                  }
                },
              })
            }}
          />
        </Space>
      ),
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
          <Button icon={<ReloadOutlined />} onClick={() => loadExecutions(pagination.current, pagination.pageSize)}>
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
