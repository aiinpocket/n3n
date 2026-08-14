import { useMemo, useState } from 'react'
import { Badge, Button, Space, Tag, Progress, Card, Popconfirm } from 'antd'
import { message } from '../../utils/feedback'
import {
  PlayCircleOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  StopOutlined,
  ClockCircleOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useExecutionStore, NodeExecutionState } from '../../stores/executionStore'
import { useAIAssistantStore } from '../../stores/aiAssistantStore'
import { useExecutionMonitor, useExecutionActions } from '../../hooks/useExecutionMonitor'
import logger from '../../utils/logger'
import { extractApiError } from '../../utils/errorMessages'

interface ExecutionOverlayProps {
  executionId: string | null
  flowId: string
  /** Version to execute (e.g. current draft); keeps re-runs consistent with the toolbar */
  version?: string
  /** Total node count of the flow, for accurate progress */
  totalNodes?: number
  onClose: () => void
  onExecutionStart?: (executionId: string) => void
}

export default function ExecutionOverlay({
  executionId,
  flowId,
  version,
  totalNodes,
  onClose,
  onExecutionStart,
}: ExecutionOverlayProps) {
  const { t } = useTranslation()
  const { execution, isConnected } = useExecutionMonitor(executionId || undefined)
  const { startExecution, cancelExecution } = useExecutionActions()

  const [starting, setStarting] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const handleStart = async () => {
    if (starting) return
    setStarting(true)
    try {
      const response = await startExecution({ flowId, version })
      onExecutionStart?.(response.id)
    } catch (error) {
      logger.error('Failed to start execution:', error)
      message.error(extractApiError(error, t('execution.startFailed')))
    } finally {
      setStarting(false)
    }
  }

  const handleCancel = async () => {
    if (!executionId || cancelling) return
    setCancelling(true)
    try {
      await cancelExecution(executionId)
      message.success(t('execution.cancelled'))
    } catch (error) {
      logger.error('Failed to cancel execution:', error)
      message.error(extractApiError(error, t('execution.cancelFailed')))
    } finally {
      setCancelling(false)
    }
  }

  const statusConfig = useMemo(() => {
    if (!execution) return null

    switch (execution.status) {
      case 'pending':
        return {
          icon: <ClockCircleOutlined />,
          color: 'default',
          text: t('execution.pending'),
        }
      case 'running':
        return {
          icon: <LoadingOutlined spin />,
          color: 'processing',
          text: t('execution.running'),
        }
      case 'completed':
        return {
          icon: <CheckCircleOutlined />,
          color: 'success',
          text: t('execution.completed'),
        }
      case 'failed':
        return {
          icon: <CloseCircleOutlined />,
          color: 'error',
          text: t('execution.failed'),
        }
      case 'cancelled':
        return {
          icon: <StopOutlined />,
          color: 'warning',
          text: t('execution.cancelled'),
        }
      default:
        return null
    }
  }, [execution, t])

  const nodeStats = useMemo(() => {
    if (!execution) return { total: 0, completed: 0, running: 0, failed: 0 }

    const states = Array.from(execution.nodeStates.values())
    return {
      total: states.length,
      completed: states.filter((s) => s.status === 'completed').length,
      running: states.filter((s) => s.status === 'running').length,
      failed: states.filter((s) => s.status === 'failed').length,
    }
  }, [execution])

  // Use the flow's real node count as denominator when available; the number of
  // reported node states starts at 1 and would show a misleading 100% early on
  const progressPercent = useMemo(() => {
    const denominator = totalNodes && totalNodes > 0 ? totalNodes : nodeStats.total
    if (denominator === 0) return 0
    return Math.min(100, Math.round((nodeStats.completed / denominator) * 100))
  }, [nodeStats, totalNodes])

  if (!executionId) {
    return (
      <div
        style={{
          position: 'absolute',
          top: 16,
          right: 16,
          zIndex: 1000,
        }}
      >
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          onClick={handleStart}
          size="large"
          loading={starting}
          disabled={starting}
        >
          {t('execution.startExecution')}
        </Button>
      </div>
    )
  }

  return (
    <Card
      size="small"
      style={{
        position: 'absolute',
        top: 16,
        right: 16,
        zIndex: 1000,
        width: 280,
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
      }}
      title={
        <Space>
          {statusConfig?.icon}
          <span>{t('execution.detail')}</span>
          <Tag color={statusConfig?.color as string}>{statusConfig?.text}</Tag>
        </Space>
      }
      extra={
        <Button type="text" size="small" onClick={onClose} aria-label={t('common.close')}>
          ×
        </Button>
      }
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        {/* Connection Status */}
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
          <span>{t('execution.realtime')}</span>
          <Badge status={isConnected ? 'success' : 'error'} text={isConnected ? t('execution.live') : t('execution.disconnected')} />
        </div>

        {/* Progress */}
        {execution?.status === 'running' && (
          <div>
            <Progress
              percent={progressPercent}
              size="small"
              status="active"
              strokeColor={{
                '0%': '#8D7BB0',
                '100%': '#7F9375',
              }}
            />
            <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>
              {t('execution.nodesCompleted', { completed: nodeStats.completed, total: totalNodes && totalNodes > 0 ? totalNodes : nodeStats.total })}
              {nodeStats.running > 0 && ` (${nodeStats.running} ${t('execution.running')})`}
            </div>
          </div>
        )}

        {/* Node Stats */}
        <div style={{ display: 'flex', gap: 8 }}>
          <Tag color="success">{nodeStats.completed} {t('execution.completed')}</Tag>
          {nodeStats.running > 0 && <Tag color="processing">{nodeStats.running} {t('execution.running')}</Tag>}
          {nodeStats.failed > 0 && <Tag color="error">{nodeStats.failed} {t('execution.failed')}</Tag>}
        </div>

        {/* Error Message */}
        {execution?.error && (
          <div
            style={{
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid var(--color-error)',
              borderRadius: 4,
              padding: 8,
              fontSize: 12,
              color: 'var(--color-error)',
              maxHeight: 120,
              overflow: 'auto',
              wordBreak: 'break-word',
            }}
          >
            {execution.error}
          </div>
        )}

        {/* Actions */}
        <Space>
          {execution?.status === 'running' && (
            <Popconfirm
              title={t('execution.cancelConfirm')}
              okText={t('common.confirm')}
              cancelText={t('common.cancel')}
              onConfirm={handleCancel}
            >
              <Button size="small" danger loading={cancelling}>
                {t('execution.cancel')}
              </Button>
            </Popconfirm>
          )}
          {(execution?.status === 'completed' ||
            execution?.status === 'failed' ||
            execution?.status === 'cancelled') && (
            <Button
              size="small"
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleStart}
              loading={starting}
              disabled={starting}
            >
              {t('execution.reExecute')}
            </Button>
          )}
          {(execution?.status === 'failed' || nodeStats.failed > 0) && executionId && (
            <Button
              size="small"
              icon={<RobotOutlined />}
              onClick={() => useAIAssistantStore.getState().requestExecutionAnalysis(executionId)}
            >
              {t('execution.aiAnalyze')}
            </Button>
          )}
        </Space>
      </Space>
    </Card>
  )
}

// Hook to get node execution states for the flow editor
export function useNodeExecutionStates(executionId: string | null): Map<string, NodeExecutionState> {
  const getExecution = useExecutionStore((state) => state.getExecution)
  const execution = executionId ? getExecution(executionId) : undefined
  return execution?.nodeStates || new Map()
}
