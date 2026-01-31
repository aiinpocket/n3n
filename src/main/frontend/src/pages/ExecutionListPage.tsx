import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Table, Tag, Button, Space, message, Typography } from 'antd'
import {
  ReloadOutlined,
  EyeOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  StopOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import { executionApi, ExecutionResponse } from '../api/execution'
import { useAllExecutions } from '../hooks/useExecutionMonitor'

const { Text } = Typography

const statusColors: Record<string, string> = {
  pending: 'default',
  running: 'processing',
  completed: 'success',
  failed: 'error',
  cancelled: 'warning',
}

const statusIcons: Record<string, React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  running: <LoadingOutlined />,
  completed: <CheckCircleOutlined />,
  failed: <CloseCircleOutlined />,
  cancelled: <StopOutlined />,
}

export default function ExecutionListPage() {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const [executions, setExecutions] = useState<ExecutionResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  // Real-time updates from WebSocket
  const { executions: realtimeExecutions, isConnected } = useAllExecutions()

  const getLocale = () => {
    switch (i18n.language) {
      case 'ja': return 'ja-JP'
      case 'en': return 'en-US'
      default: return 'zh-TW'
    }
  }

  const loadExecutions = useCallback(async (page = 1, pageSize = 20) => {
    setLoading(true)
    try {
      const data = await executionApi.list(page - 1, pageSize)
      setExecutions(data.content)
      setPagination({
        current: data.number + 1,
        pageSize: data.size,
        total: data.totalElements,
      })
    } catch (error) {
      console.error('Failed to load executions:', error)
      message.error(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadExecutions()
  }, [loadExecutions])

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

  const handleTableChange = (newPagination: TablePaginationConfig) => {
    loadExecutions(newPagination.current, newPagination.pageSize)
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
      render: (status: string) => (
        <Tag icon={statusIcons[status]} color={statusColors[status] || 'default'}>
          {status.toUpperCase()}
        </Tag>
      ),
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
      render: (ms: number) => (ms != null ? `${ms}ms` : '-'),
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
      width: 80,
      render: (_, record) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/executions/${record.id}`)}>
          {t('execution.view')}
        </Button>
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
        <Button icon={<ReloadOutlined />} onClick={() => loadExecutions(pagination.current, pagination.pageSize)}>
          {t('common.refresh')}
        </Button>
      }
    >
      <Table
        columns={columns}
        dataSource={executions}
        rowKey="id"
        loading={loading}
        pagination={pagination}
        onChange={handleTableChange}
        size="middle"
      />
    </Card>
  )
}
