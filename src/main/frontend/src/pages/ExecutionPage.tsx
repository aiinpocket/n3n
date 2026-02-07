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
  Drawer,
  Tabs,
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
  PauseCircleOutlined,
  RedoOutlined,
  DatabaseOutlined,
  DownOutlined,
  UpOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { executionApi, ExecutionResponse, NodeExecutionResponse } from '../api/execution'
import { useExecutionMonitor, useExecutionActions } from '../hooks/useExecutionMonitor'
import { flowApi } from '../api/flow'
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
}

const statusIcons: Record<string, React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  running: <LoadingOutlined />,
  completed: <CheckCircleOutlined />,
  failed: <CloseCircleOutlined />,
  cancelled: <StopOutlined />,
  waiting: <PauseCircleOutlined />,
}

export default function ExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const flowId = searchParams.get('flowId')

  const [executionData, setExecutionData] = useState<ExecutionResponse | null>(null)
  const [nodeExecutions, setNodeExecutions] = useState<NodeExecutionResponse[]>([])
  const [flowName, setFlowName] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [starting, setStarting] = useState(false)
  const [cancelModalOpen, setCancelModalOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [pauseModalOpen, setPauseModalOpen] = useState(false)
  const [pauseReason, setPauseReason] = useState('')
  const [pausing, setPausing] = useState(false)
  const [resumeModalOpen, setResumeModalOpen] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [dataDrawerOpen, setDataDrawerOpen] = useState(false)
  const [selectedNodeData, setSelectedNodeData] = useState<{
    nodeId: string;
    input: Record<string, unknown> | null;
    output: Record<string, unknown> | null;
  } | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loadingNodeData, setLoadingNodeData] = useState(false)
  const [expandedErrors, setExpandedErrors] = useState<Set<string>>(new Set())

  const { execution, isConnected } = useExecutionMonitor(id)
  const { startExecution, cancelExecution } = useExecutionActions()

  // Load execution data
  const loadExecution = useCallback(async () => {
    if (!id) return
    setLoadError(null)
    try {
      const data = await executionApi.get(id)
      setExecutionData(data)
      setFlowName(data.flowName || '')

      const nodes = await executionApi.getNodeExecutions(id)
      setNodeExecutions(nodes)
    } catch (error) {
      logger.error('Failed to load execution:', error)
      setLoadError(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [id, t])

  // Load flow name for new execution
  useEffect(() => {
    const loadFlowName = async () => {
      if (flowId && !id) {
        try {
          const flow = await flowApi.getFlow(flowId)
          setFlowName(flow.name)
          setLoading(false)
        } catch (error) {
          logger.error('Failed to load flow:', error)
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
          executionApi.getNodeExecutions(id).then(setNodeExecutions).catch(() => {
            // Silently handle - node data will refresh on next update
          })
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
      message.error(extractApiError(error, t('execution.startFailed')))
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
      message.error(extractApiError(error, t('execution.cancelFailed')))
    }
  }

  const handlePauseExecution = async () => {
    if (!id) return
    setPausing(true)
    try {
      await executionApi.pause(id, pauseReason || undefined)
      message.success(t('execution.pauseSuccess'))
      setPauseModalOpen(false)
      setPauseReason('')
      loadExecution()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('execution.pauseFailed')))
    } finally {
      setPausing(false)
    }
  }

  const handleResumeExecution = async () => {
    if (!id) return
    try {
      await executionApi.resume(id)
      message.success(t('execution.resumeSuccess'))
      setResumeModalOpen(false)
      loadExecution()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('execution.resumeFailed')))
    }
  }

  const handleRetryExecution = async () => {
    if (!id) return
    setRetrying(true)
    try {
      const response = await executionApi.retry(id)
      message.success(t('execution.retrySuccess'))
      navigate(`/executions/${response.id}`, { replace: true })
    } catch (error: unknown) {
      message.error(extractApiError(error, t('execution.retryFailed')))
    } finally {
      setRetrying(false)
    }
  }

  const handleViewNodeData = async (node: NodeExecutionResponse) => {
    if (!id) return
    setDataDrawerOpen(true)
    setLoadingNodeData(true)
    try {
      const data = await executionApi.getNodeData(id, node.nodeId)
      setSelectedNodeData({
        nodeId: node.nodeId,
        input: data.input,
        output: data.output,
      })
    } catch {
      // Fallback to inline data if the API call fails
      setSelectedNodeData({
        nodeId: node.nodeId,
        input: node.inputData,
        output: node.outputData,
      })
    } finally {
      setLoadingNodeData(false)
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
            icon={<PlayCircleOutlined style={{ color: 'var(--color-primary)' }} />}
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

  if (loadError && !executionData) {
    return (
      <Result status="error" title={t('common.loadFailed')} subTitle={loadError} extra={
        <Space>
          <Button type="primary" onClick={loadExecution}>{t('common.retry')}</Button>
          <Button onClick={() => navigate('/flows')}>{t('execution.backToFlows')}</Button>
        </Space>
      } />
    )
  }

  if (!executionData) {
    return (
      <Result status="404" title={t('execution.notFound')} subTitle={t('execution.notFoundDesc')} extra={<Button onClick={() => navigate('/flows')}>{t('execution.backToFlows')}</Button>} />
    )
  }

  const isRunning = executionData.status === 'running' || executionData.status === 'pending'
  const isWaiting = executionData.status === 'waiting'
  const isFailed = executionData.status === 'failed'
  const isCancelled = executionData.status === 'cancelled'

  return (
    <>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} />
            <span>{t('execution.detail')}</span>
            <Tag icon={statusIcons[executionData.status]} color={statusColors[executionData.status]}>
              {t(`execution.${executionData.status.toLowerCase()}`, { defaultValue: executionData.status.toUpperCase() })}
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
              <Button icon={<PauseCircleOutlined />} onClick={() => setPauseModalOpen(true)} loading={pausing}>
                {t('execution.pause')}
              </Button>
            )}
            {isWaiting && (
              <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => setResumeModalOpen(true)}>
                {t('execution.resume')}
              </Button>
            )}
            {(isFailed || isCancelled) && (
              <Button icon={<RedoOutlined />} onClick={handleRetryExecution} loading={retrying}>
                {t('execution.retry')}
              </Button>
            )}
            {(isRunning || isWaiting) && (
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
            <Descriptions.Item label={t('execution.duration')}>{formatDuration(executionData.durationMs)}</Descriptions.Item>
            <Descriptions.Item label={t('common.createdAt')}>{executionData.createdAt ? new Date(executionData.createdAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
            {executionData.cancelReason && <Descriptions.Item label={t('execution.cancelReason')}>{executionData.cancelReason}</Descriptions.Item>}
            {executionData.pauseReason && <Descriptions.Item label={t('execution.pauseReason')}>{executionData.pauseReason}</Descriptions.Item>}
            {executionData.waitingNodeId && <Descriptions.Item label={t('execution.waitingNode')}>{executionData.waitingNodeId}</Descriptions.Item>}
            {executionData.resumeCondition && <Descriptions.Item label={t('execution.resumeCondition')}>{executionData.resumeCondition}</Descriptions.Item>}
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
                        <Button
                          type="link"
                          size="small"
                          icon={<DatabaseOutlined />}
                          onClick={() => handleViewNodeData(node)}
                        >
                          {t('execution.viewData')}
                        </Button>
                      </Space>
                      {node.errorMessage && (
                        <div style={{ marginTop: 4 }}>
                          <Text type="danger">{node.errorMessage}</Text>
                          {node.errorStack && (
                            <div style={{ marginTop: 4 }}>
                              <Button
                                type="link"
                                size="small"
                                icon={expandedErrors.has(node.nodeId) ? <UpOutlined /> : <DownOutlined />}
                                onClick={() => {
                                  setExpandedErrors((prev) => {
                                    const next = new Set(prev)
                                    if (next.has(node.nodeId)) {
                                      next.delete(node.nodeId)
                                    } else {
                                      next.add(node.nodeId)
                                    }
                                    return next
                                  })
                                }}
                                style={{ padding: 0, fontSize: 12 }}
                              >
                                {expandedErrors.has(node.nodeId) ? t('execution.hideErrorStack') : t('execution.showErrorStack')}
                              </Button>
                              {expandedErrors.has(node.nodeId) && (
                                <pre style={{
                                  marginTop: 8,
                                  padding: 12,
                                  background: 'rgba(239, 68, 68, 0.08)',
                                  borderRadius: 6,
                                  fontSize: 12,
                                  lineHeight: 1.5,
                                  overflow: 'auto',
                                  maxHeight: 300,
                                  color: 'var(--color-text-secondary)',
                                  border: '1px solid rgba(239, 68, 68, 0.2)',
                                }}>
                                  {node.errorStack}
                                </pre>
                              )}
                            </div>
                          )}
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

      <Modal
        title={t('execution.pauseExecution')}
        open={pauseModalOpen}
        onOk={handlePauseExecution}
        onCancel={() => { setPauseModalOpen(false); setPauseReason('') }}
        confirmLoading={pausing}
        okText={t('execution.pause')}
      >
        <p>{t('execution.pauseConfirm')}</p>
        <Input.TextArea placeholder={t('execution.pauseReasonPlaceholder')} value={pauseReason} onChange={(e) => setPauseReason(e.target.value)} rows={3} />
      </Modal>

      <Modal
        title={t('execution.resumeExecution')}
        open={resumeModalOpen}
        onOk={handleResumeExecution}
        onCancel={() => setResumeModalOpen(false)}
        okText={t('execution.resume')}
      >
        {executionData?.pauseReason && (
          <p><Text strong>{t('execution.pauseReason')}:</Text> {executionData.pauseReason}</p>
        )}
        {executionData?.resumeCondition && (
          <p><Text strong>{t('execution.resumeCondition')}:</Text> {executionData.resumeCondition}</p>
        )}
        <p>{t('execution.resumeConfirm')}</p>
      </Modal>

      <Drawer
        title={`${t('execution.viewData')} - ${selectedNodeData?.nodeId || ''}`}
        open={dataDrawerOpen}
        onClose={() => {
          setDataDrawerOpen(false)
          setSelectedNodeData(null)
        }}
        width={window.innerWidth < 768 ? '100%' : 600}
        placement="right"
      >
        {loadingNodeData ? (
          <div style={{ textAlign: 'center', padding: 50 }}>
            <Spin size="large" />
          </div>
        ) : (
          <Tabs
            defaultActiveKey="input"
            items={[
              {
                key: 'input',
                label: <span style={{ color: 'var(--color-info)' }}>{t('execution.inputData')}</span>,
                children: selectedNodeData?.input ? (
                  <pre
                    style={{
                      background: 'var(--color-bg-elevated)',
                      color: 'var(--color-text-primary)',
                      padding: 16,
                      borderRadius: 8,
                      overflow: 'auto',
                      maxHeight: 'calc(100vh - 200px)',
                      fontSize: 13,
                      lineHeight: 1.6,
                    }}
                  >
                    {JSON.stringify(selectedNodeData.input, null, 2)}
                  </pre>
                ) : (
                  <Text type="secondary">{t('execution.noData')}</Text>
                ),
              },
              {
                key: 'output',
                label: <span style={{ color: 'var(--color-success)' }}>{t('execution.outputData')}</span>,
                children: selectedNodeData?.output ? (
                  <pre
                    style={{
                      background: 'var(--color-bg-elevated)',
                      color: 'var(--color-text-primary)',
                      padding: 16,
                      borderRadius: 8,
                      overflow: 'auto',
                      maxHeight: 'calc(100vh - 200px)',
                      fontSize: 13,
                      lineHeight: 1.6,
                    }}
                  >
                    {JSON.stringify(selectedNodeData.output, null, 2)}
                  </pre>
                ) : (
                  <Text type="secondary">{t('execution.noData')}</Text>
                ),
              },
            ]}
          />
        )}
      </Drawer>
    </>
  )
}
