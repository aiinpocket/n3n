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
  ExclamationCircleOutlined,
  FileTextOutlined,
  CopyOutlined,
  CodeOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { executionApi, ExecutionResponse, NodeExecutionResponse, ApprovalResponse } from '../api/execution'
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
  const [cancelling, setCancelling] = useState(false)
  const [resumeModalOpen, setResumeModalOpen] = useState(false)
  const [resumeDataInput, setResumeDataInput] = useState('')
  const [resuming, setResuming] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [dataDrawerOpen, setDataDrawerOpen] = useState(false)
  const [selectedNodeData, setSelectedNodeData] = useState<{
    nodeId: string;
    input: Record<string, unknown> | null;
    output: Record<string, unknown> | null;
  } | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loadingNodeData, setLoadingNodeData] = useState(false)
  const [approvalData, setApprovalData] = useState<ApprovalResponse | null>(null)
  const [approvalComment, setApprovalComment] = useState('')
  const [submittingApproval, setSubmittingApproval] = useState(false)
  const [executionOutput, setExecutionOutput] = useState<Record<string, unknown> | null>(null)
  const [expandedErrorNode, setExpandedErrorNode] = useState<string | null>(null)

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

      // Load approval data if execution is waiting
      if (data.status === 'waiting') {
        const approval = await executionApi.getApproval(id)
        setApprovalData(approval)
      }

      // Load execution output if completed
      if (data.status === 'completed') {
        try {
          const output = await executionApi.getOutput(id)
          setExecutionOutput(output)
        } catch {
          // Output may not be available for all executions
        }
      }
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
          message.error(extractApiError(error, t('common.loadFailed')))
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
    let cancelled = false
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
          executionApi.getNodeExecutions(id)
            .then((data) => { if (!cancelled) setNodeExecutions(data) })
            .catch((err) => { logger.warn('Failed to refresh node executions:', err) })
          // Reload approval data when execution enters waiting status
          if (execution.status === 'waiting') {
            executionApi.getApproval(id)
              .then((data) => { if (!cancelled) setApprovalData(data) })
              .catch((err) => { logger.warn('Failed to load approval data:', err) })
          }
        }
      }
    }
    return () => { cancelled = true }
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
    setCancelling(true)
    try {
      await cancelExecution(id, cancelReason)
      message.success(t('execution.cancelSuccess'))
      setCancelModalOpen(false)
      setCancelReason('')
      loadExecution()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('execution.cancelFailed')))
    } finally {
      setCancelling(false)
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
    setResuming(true)
    try {
      let resumeData: Record<string, unknown> | undefined
      if (resumeDataInput.trim()) {
        try {
          resumeData = JSON.parse(resumeDataInput.trim())
        } catch {
          message.error(t('execution.invalidResumeData'))
          setResuming(false)
          return
        }
      }
      await executionApi.resume(id, resumeData)
      message.success(t('execution.resumeSuccess'))
      setResumeModalOpen(false)
      setResumeDataInput('')
      loadExecution()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('execution.resumeFailed')))
    } finally {
      setResuming(false)
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
      // Fallback to empty data if the API call fails
      setSelectedNodeData({
        nodeId: node.nodeId,
        input: null,
        output: null,
      })
    } finally {
      setLoadingNodeData(false)
    }
  }

  const handleApprovalAction = async (action: 'approve' | 'reject') => {
    if (!id) return
    setSubmittingApproval(true)
    try {
      await executionApi.submitApproval(id, action, approvalComment || undefined)
      message.success(t(`approval.${action === 'approve' ? 'approved' : 'rejected'}`))
      setApprovalComment('')
      setApprovalData(null)
      loadExecution()
    } catch (error) {
      message.error(extractApiError(error, t('common.operationFailed')))
    } finally {
      setSubmittingApproval(false)
    }
  }

  // New execution mode
  if (!id && flowId) {
    return (
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} aria-label={t('common.back')} />
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
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate(-1)} aria-label={t('common.back')} />
            <span>{t('execution.detail')}</span>
            <Tag icon={statusIcons[executionData.status]} color={statusColors[executionData.status]}>
              {t(`execution.${executionData.status.toLowerCase()}`, { defaultValue: executionData.status.toUpperCase() })}
            </Tag>
            {isConnected && <Tag color="green">{t('execution.realtime')}</Tag>}
            {!isConnected && isRunning && <Tag color="orange">{t('execution.disconnected')}</Tag>}
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
            {(isFailed || isCancelled) && executionData.canRetry !== false && (
              <Button icon={<RedoOutlined />} onClick={handleRetryExecution} loading={retrying}>
                {t('execution.retry')}
              </Button>
            )}
            {(isRunning || isWaiting) && (
              <Button danger icon={<StopOutlined />} onClick={() => setCancelModalOpen(true)}>
                {t('execution.cancel')}
              </Button>
            )}
            <Button icon={<FileTextOutlined />} onClick={() => navigate(`/logs?search=${id}`)}>
              {t('execution.viewLogs')}
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Descriptions bordered column={2}>
            <Descriptions.Item label={t('execution.executionId')}>{executionData.id}</Descriptions.Item>
            <Descriptions.Item label={t('execution.flowName')}>
              {executionData.flowName && executionData.flowId ? (
                <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/flows/${executionData.flowId}/edit`)}>
                  {executionData.flowName}
                </Button>
              ) : (
                executionData.flowName || '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('flow.version')}>{executionData.flowVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.triggerType')}>{executionData.triggerType}</Descriptions.Item>
            <Descriptions.Item label={t('execution.startTime')}>{executionData.startedAt ? new Date(executionData.startedAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.endTime')}>{executionData.completedAt ? new Date(executionData.completedAt).toLocaleString(getLocale()) : '-'}</Descriptions.Item>
            <Descriptions.Item label={t('execution.duration')}>{formatDuration(executionData.durationMs)}</Descriptions.Item>
            {executionData.cancelReason && <Descriptions.Item label={t('execution.cancelReason')}>{executionData.cancelReason}</Descriptions.Item>}
            {executionData.pauseReason && <Descriptions.Item label={t('execution.pauseReason')}>{executionData.pauseReason}</Descriptions.Item>}
            {executionData.waitingNodeId && <Descriptions.Item label={t('execution.waitingNode')}>{executionData.waitingNodeId}</Descriptions.Item>}
            {executionData.resumeCondition && <Descriptions.Item label={t('execution.resumeCondition')}>{JSON.stringify(executionData.resumeCondition)}</Descriptions.Item>}
          </Descriptions>

          {approvalData && approvalData.status === 'pending' && (
            <Card
              title={
                <Space>
                  <ExclamationCircleOutlined style={{ color: 'var(--color-warning)' }} />
                  <span>{t('approval.title')}</span>
                </Space>
              }
              style={{ borderColor: 'var(--color-warning)', borderWidth: 2 }}
              size="small"
            >
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {approvalData.message && (
                  <div>
                    <Text strong>{t('approval.message')}:</Text>
                    <div style={{ marginTop: 4, padding: 12, background: 'var(--color-bg-elevated)', borderRadius: 8 }}>
                      {approvalData.message}
                    </div>
                  </div>
                )}
                <Descriptions column={2} size="small">
                  <Descriptions.Item label={t(`approval.mode.${approvalData.approvalMode}`)}>{approvalData.approvalMode}</Descriptions.Item>
                  <Descriptions.Item label={t('approval.waitingFor', { count: approvalData.requiredApprovers })}>{approvalData.requiredApprovers}</Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvedCount', { count: approvalData.approvedCount })}>{approvalData.approvedCount}</Descriptions.Item>
                  <Descriptions.Item label={t('approval.rejectedCount', { count: approvalData.rejectedCount })}>{approvalData.rejectedCount}</Descriptions.Item>
                </Descriptions>
                <Input.TextArea
                  placeholder={t('approval.commentPlaceholder')}
                  value={approvalComment}
                  onChange={(e) => setApprovalComment(e.target.value)}
                  rows={2}
                />
                <Space>
                  <Button
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={() => handleApprovalAction('approve')}
                    loading={submittingApproval}
                    style={{ background: 'var(--color-success)', borderColor: 'var(--color-success)' }}
                  >
                    {t('approval.approve')}
                  </Button>
                  <Button
                    danger
                    icon={<CloseCircleOutlined />}
                    onClick={() => handleApprovalAction('reject')}
                    loading={submittingApproval}
                  >
                    {t('approval.reject')}
                  </Button>
                </Space>
              </Space>
            </Card>
          )}

          <Card title={`${t('execution.nodeExecutions')} (${nodeExecutions.length})`} size="small">
            {nodeExecutions.length === 0 ? (
              <Text type="secondary">{t('execution.noNodeExecutions')}</Text>
            ) : (
              <Timeline
                items={nodeExecutions.slice(0, 200).map((node) => ({
                  color: node.status === 'completed' ? 'green' : node.status === 'failed' ? 'red' : node.status === 'running' ? 'blue' : 'gray',
                  dot: statusIcons[node.status],
                  children: (
                    <div>
                      <Space>
                        <Text strong>{node.nodeId}</Text>
                        <Tag>{node.componentName}</Tag>
                        <Tag color={statusColors[node.status]}>{node.status}</Tag>
                        {node.durationMs != null && <Text type="secondary">{node.durationMs}ms</Text>}
                        {node.retryCount != null && node.retryCount > 0 && (
                          <Tag color="warning">{t('execution.retryCount', { count: node.retryCount })}</Tag>
                        )}
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
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            <Space>
                              <Text type="danger">{node.errorMessage}</Text>
                              <Button
                                type="text"
                                size="small"
                                icon={<CopyOutlined />}
                                onClick={() => {
                                  navigator.clipboard.writeText(node.errorMessage || '')
                                  message.success(t('common.copied'))
                                }}
                                aria-label={t('execution.copyError')}
                              />
                            </Space>
                            {node.errorStack && (
                              <>
                                <Button
                                  type="link"
                                  size="small"
                                  icon={<CodeOutlined />}
                                  onClick={() => setExpandedErrorNode(expandedErrorNode === node.nodeId ? null : node.nodeId)}
                                >
                                  {expandedErrorNode === node.nodeId ? t('execution.hideErrorStack') : t('execution.showErrorStack')}
                                </Button>
                                {expandedErrorNode === node.nodeId && (
                                  <pre style={{
                                    background: 'var(--color-bg-elevated)',
                                    color: 'var(--color-text-primary)',
                                    padding: 12,
                                    borderRadius: 6,
                                    overflow: 'auto',
                                    maxHeight: 300,
                                    fontSize: 12,
                                    lineHeight: 1.5,
                                    margin: 0,
                                  }}>
                                    {node.errorStack}
                                  </pre>
                                )}
                              </>
                            )}
                          </Space>
                        </div>
                      )}
                    </div>
                  ),
                }))}
              />
            )}
          </Card>

          {executionOutput && Object.keys(executionOutput).length > 0 && (
            <Card title={t('execution.outputData')} size="small">
              <pre
                style={{
                  background: 'var(--color-bg-elevated)',
                  color: 'var(--color-text-primary)',
                  padding: 16,
                  borderRadius: 8,
                  overflow: 'auto',
                  maxHeight: 400,
                  fontSize: 13,
                  lineHeight: 1.6,
                }}
              >
                {JSON.stringify(executionOutput, null, 2)}
              </pre>
            </Card>
          )}
        </Space>
      </Card>

      <Modal title={t('execution.cancelExecution')} open={cancelModalOpen} onOk={handleCancelExecution} onCancel={() => { setCancelModalOpen(false); setCancelReason('') }} confirmLoading={cancelling} okText={t('common.confirm')} cancelText={t('common.cancel')}>
        <Input.TextArea placeholder={t('execution.cancelReasonPlaceholder')} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} rows={3} />
      </Modal>

      <Modal
        title={t('execution.pauseExecution')}
        open={pauseModalOpen}
        onOk={handlePauseExecution}
        onCancel={() => { setPauseModalOpen(false); setPauseReason('') }}
        confirmLoading={pausing}
        okText={t('execution.pause')}
        cancelText={t('common.cancel')}
      >
        <p>{t('execution.pauseConfirm')}</p>
        <Input.TextArea placeholder={t('execution.pauseReasonPlaceholder')} value={pauseReason} onChange={(e) => setPauseReason(e.target.value)} rows={3} />
      </Modal>

      <Modal
        title={t('execution.resumeExecution')}
        open={resumeModalOpen}
        onOk={handleResumeExecution}
        onCancel={() => { setResumeModalOpen(false); setResumeDataInput('') }}
        confirmLoading={resuming}
        okText={t('execution.resume')}
        cancelText={t('common.cancel')}
      >
        {executionData?.pauseReason && (
          <p><Text strong>{t('execution.pauseReason')}:</Text> {executionData.pauseReason}</p>
        )}
        {executionData?.resumeCondition && (
          <p><Text strong>{t('execution.resumeCondition')}:</Text> {JSON.stringify(executionData.resumeCondition)}</p>
        )}
        <p>{t('execution.resumeConfirm')}</p>
        <div style={{ marginTop: 12 }}>
          <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>{t('execution.resumeDataLabel')}</Text>
          <Input.TextArea
            placeholder={t('execution.resumeDataPlaceholder')}
            value={resumeDataInput}
            onChange={(e) => setResumeDataInput(e.target.value)}
            rows={4}
            style={{ fontFamily: 'monospace', fontSize: 12 }}
          />
        </div>
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
