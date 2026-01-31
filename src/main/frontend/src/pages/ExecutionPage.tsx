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
import { useTranslation } from 'react-i18next'
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
  const { t, i18n } = useTranslation()
  const flowId = searchParams.get('flowId')

  const getLocale = () => {
    switch (i18n.language) {
      case 'ja': return 'ja-JP'
      case 'en': return 'en-US'
      default: return 'zh-TW'
    }
  }

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
          message.error(t('common.loadFailed'))
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
      message.success(t('execution.started'))
      navigate(`/executions/${response.id}`, { replace: true })
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('execution.startFailed'))
    } finally {
      setStarting(false)
    }
  }

  const handleCancelExecution = async () => {
    if (!id) return
    try {
      await cancelExecution(id, cancelReason)
      message.success(t('execution.cancelSuccess'))
      setCancelModalOpen(false)
      setCancelReason('')
      loadExecution()
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('execution.cancelFailed'))
    }
  }

  // New execution mode
  if (!id && flowId) {
    return (
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} />
            <span>{t('execution.runFlow')}: {flowName}</span>
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
            title={t('execution.readyToRun', { name: flowName })}
            subTitle={t('execution.clickToStart')}
            extra={
              <Button type="primary" size="large" icon={<PlayCircleOutlined />} onClick={handleStartExecution} loading={starting}>
                {t('execution.startExecution')}
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
      <Result status="404" title={t('execution.notFound')} subTitle={t('execution.notFoundDesc')} extra={<Button onClick={() => navigate('/flows')}>{t('execution.backToFlows')}</Button>} />
    )
  }

  const isRunning = executionData.status === 'running' || executionData.status === 'pending'

  return (
    <>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} />
            <span>{t('execution.detail')}</span>
            <Tag icon={statusIcons[executionData.status]} color={statusColors[executionData.status]}>
              {executionData.status.toUpperCase()}
            </Tag>
            {isConnected && <Tag color="green">{t('execution.realtime')}</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadExecution}>
              {t('execution.reload')}
            </Button>
            {isRunning && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelModalOpen(true)}>
                {t('execution.cancel')}
              </Button>
            )}
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Descriptions bordered column={2}>
            <Descriptions.Item label={t('execution.executionId')}>{executionData.id}</Descriptions.Item>
            <Descriptions.Item label={t('execution.flowName')}>{executionData.flowName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('flow.version')}>{executionData.version || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.triggerType')}>{executionData.triggerType}</Descriptions.Item>
            <Descriptions.Item label={t('execution.startTime')}>{executionData.startedAt ? new Date(executionData.startedAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.endTime')}>{executionData.completedAt ? new Date(executionData.completedAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.duration')}>{executionData.durationMs != null ? `${executionData.durationMs} ms` : '-'}</Descriptions.Item>
            <Descriptions.Item label={t('common.createdAt')}>{new Date(executionData.createdAt).toLocaleString(getLocale())}</Descriptions.Item>
            {executionData.cancelReason && <Descriptions.Item label={t('execution.cancelReason')}>{executionData.cancelReason}</Descriptions.Item>}
          </Descriptions>

          <Card title={t('execution.nodeExecutions')} size="small">
            {nodeExecutions.length === 0 ? (
              <Text type="secondary">{t('execution.noNodeExecutions')}</Text>
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

      <Modal title={t('execution.cancelExecution')} open={cancelModalOpen} onOk={handleCancelExecution} onCancel={() => setCancelModalOpen(false)}>
        <Input.TextArea placeholder={t('execution.cancelReasonPlaceholder')} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} rows={3} />
      </Modal>
    </>
  )
}
