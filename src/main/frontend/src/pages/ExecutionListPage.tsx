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
  const [executions, setExecutions] = useState<ExecutionResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  // Real-time updates from WebSocket
  const { executions: realtimeExecutions, isConnected } = useAllExecutions()

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
      message.error('載入執行記錄失敗')
    } finally {
      setLoading(false)
    }
  }, [])

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
      title: '執行 ID',
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
      title: '流程名稱',
      dataIndex: 'flowName',
      key: 'flowName',
      render: (name: string) => name || '-',
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (version: number) => version || '-',
    },
    {
      title: '狀態',
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
      title: '開始時間',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 180,
      render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
    },
    {
      title: '耗時',
      dataIndex: 'durationMs',
      key: 'durationMs',
      width: 100,
      render: (ms: number) => (ms != null ? `${ms}ms` : '-'),
    },
    {
      title: '觸發類型',
      dataIndex: 'triggerType',
      key: 'triggerType',
      width: 100,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => navigate(`/executions/${record.id}`)}>
          查看
        </Button>
      ),
    },
  ]

  return (
    <Card
      title={
        <Space>
          <span>執行記錄</span>
          {isConnected && <Tag color="green">即時連線</Tag>}
        </Space>
      }
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => loadExecutions(pagination.current, pagination.pageSize)}>
          重新整理
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
