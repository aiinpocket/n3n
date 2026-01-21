import { useEffect, useState, useCallback } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Spin,
  message,
  Descriptions,
  Tag,
  Timeline,
  Result,
  Modal,
  Input,
  Typography,
} from 'antd'
import {
  PlayCircleOutlined,
  StopOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { executionApi, ExecutionResponse, NodeExecutionResponse } from '../api/execution'
import { useExecutionMonitor, useExecutionActions } from '../hooks/useExecutionMonitor'
import { flowApi } from '../api/flow'

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

export default function ExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const flowId = searchParams.get('flowId')

  const [executionData, setExecutionData] = useState<ExecutionResponse | null>(null)
  const [nodeExecutions, setNodeExecutions] = useState<NodeExecutionResponse[]>([])
  const [flowName, setFlowName] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [cancelModalOpen, setCancelModalOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')

  const { execution, isConnected } = useExecutionMonitor(id)
  const { startExecution, cancelExecution } = useExecutionActions()

  // Load execution data
  const loadExecution = useCallback(async () => {
    if (!id) return
    try {
      const data = await executionApi.get(id)
      setExecutionData(data)
      setFlowName(data.flowName || '')

      const nodes = await executionApi.getNodeExecutions(id)
      setNodeExecutions(nodes)
    } catch (error) {
      console.error('Failed to load execution:', error)
    } finally {
      setLoading(false)
    }
  }, [id])

  // Load flow name for new execution
  useEffect(() => {
    const loadFlowName = async () => {
      if (flowId && !id) {
        try {
          const flow = await flowApi.getFlow(flowId)
          setFlowName(flow.name)
          setLoading(false)
        } catch (error) {
          console.error('Failed to load flow:', error)
          message.error('無法載入 Flow')
          navigate('/flows')
        }
      }
    }
    loadFlowName()
  }, [flowId, id, navigate])

  // Load existing execution
  useEffect(() => {
    if (id) {
      loadExecution()
    }
  }, [id, loadExecution])

  // Update from WebSocket
  useEffect(() => {
    if (execution && executionData) {
      // Update status from WebSocket
      if (execution.status !== executionData.status) {
        setExecutionData((prev) =>
          prev
            ? {
                ...prev,
                status: execution.status,
                completedAt: execution.completedAt,
              }
            : prev
        )
        // Reload node executions when status changes
        if (id) {
          executionApi.getNodeExecutions(id).then(setNodeExecutions)
        }
      }
    }
  }, [execution, executionData, id])

  const handleStartExecution = async () => {
    if (!flowId) return
    setStarting(true)
    try {
      const response = await startExecution({ flowId })
      message.success('執行已啟動')
      navigate(`/executions/${response.id}`, { replace: true })
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '啟動失敗')
    } finally {
      setStarting(false)
    }
  }

  const handleCancelExecution = async () => {
    if (!id) return
    try {
      await cancelExecution(id, cancelReason)
      message.success('執行已取消')
      setCancelModalOpen(false)
      setCancelReason('')
      loadExecution()
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '取消失敗')
    }
  }

  // New execution mode
  if (!id && flowId) {
    return (
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} />
            <span>執行 Flow: {flowName}</span>
          </Space>
        }
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 50 }}>
            <Spin size="large" />
          </div>
        ) : (
          <Result
            icon={<PlayCircleOutlined style={{ color: '#1890ff' }} />}
            title={`準備執行 "${flowName}"`}
            subTitle="點擊下方按鈕開始執行此 Flow"
            extra={
              <Button type="primary" size="large" icon={<PlayCircleOutlined />} onClick={handleStartExecution} loading={starting}>
                開始執行
              </Button>
            }
          />
        )}
      </Card>
    )
  }

  // View execution mode
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!executionData) {
    return (
      <Result status="404" title="找不到執行記錄" subTitle="此執行記錄可能已被刪除或不存在" extra={<Button onClick={() => navigate('/flows')}>返回 Flow 列表</Button>} />
    )
  }

  const isRunning = executionData.status === 'running' || executionData.status === 'pending'

  return (
    <>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} />
            <span>執行詳情</span>
            <Tag icon={statusIcons[executionData.status]} color={statusColors[executionData.status]}>
              {executionData.status.toUpperCase()}
            </Tag>
            {isConnected && <Tag color="green">即時連線</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadExecution}>
              重新載入
            </Button>
            {isRunning && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelModalOpen(true)}>
                取消執行
              </Button>
            )}
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Descriptions bordered column={2}>
            <Descriptions.Item label="執行 ID">{executionData.id}</Descriptions.Item>
            <Descriptions.Item label="Flow 名稱">{executionData.flowName || '-'}</Descriptions.Item>
            <Descriptions.Item label="版本">{executionData.version || '-'}</Descriptions.Item>
            <Descriptions.Item label="觸發類型">{executionData.triggerType}</Descriptions.Item>
            <Descriptions.Item label="開始時間">{executionData.startedAt ? new Date(executionData.startedAt).toLocaleString() : '-'}</Descriptions.Item>
            <Descriptions.Item label="完成時間">{executionData.completedAt ? new Date(executionData.completedAt).toLocaleString() : '-'}</Descriptions.Item>
            <Descriptions.Item label="執行時間">{executionData.durationMs != null ? `${executionData.durationMs} ms` : '-'}</Descriptions.Item>
            <Descriptions.Item label="建立時間">{new Date(executionData.createdAt).toLocaleString()}</Descriptions.Item>
            {executionData.cancelReason && <Descriptions.Item label="取消原因">{executionData.cancelReason}</Descriptions.Item>}
          </Descriptions>

          <Card title="節點執行記錄" size="small">
            {nodeExecutions.length === 0 ? (
              <Text type="secondary">尚無節點執行記錄</Text>
            ) : (
              <Timeline
                items={nodeExecutions.map((node) => ({
                  color: node.status === 'completed' ? 'green' : node.status === 'failed' ? 'red' : node.status === 'running' ? 'blue' : 'gray',
                  dot: statusIcons[node.status],
                  children: (
                    <div>
                      <Space>
                        <Text strong>{node.nodeId}</Text>
                        <Tag>{node.componentName}</Tag>
                        <Tag color={statusColors[node.status]}>{node.status}</Tag>
                        {node.durationMs != null && <Text type="secondary">{node.durationMs}ms</Text>}
                      </Space>
                      {node.errorMessage && (
                        <div style={{ marginTop: 4 }}>
                          <Text type="danger">{node.errorMessage}</Text>
                        </div>
                      )}
                    </div>
                  ),
                }))}
              />
            )}
          </Card>
        </Space>
      </Card>

      <Modal title="取消執行" open={cancelModalOpen} onOk={handleCancelExecution} onCancel={() => setCancelModalOpen(false)}>
        <Input.TextArea placeholder="取消原因（選填）" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} rows={3} />
      </Modal>
    </>
  )
}
