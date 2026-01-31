import { useMemo } from 'react'
import { Badge, Button, Space, Tag, Progress, Card } from 'antd'
import {
  PlayCircleOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  StopOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useExecutionStore, NodeExecutionState } from '../../stores/executionStore'
import { useExecutionMonitor, useExecutionActions } from '../../hooks/useExecutionMonitor'

interface ExecutionOverlayProps {
  executionId: string | null
  flowId: string
  onClose: () => void
  onExecutionStart?: (executionId: string) => void
}

export default function ExecutionOverlay({
  executionId,
  flowId,
  onClose,
  onExecutionStart,
}: ExecutionOverlayProps) {
  const { t } = useTranslation()
  const { execution, isConnected } = useExecutionMonitor(executionId || undefined)
  const { startExecution, cancelExecution } = useExecutionActions()

  const handleStart = async () => {
    try {
      const response = await startExecution({ flowId })
      onExecutionStart?.(response.id)
    } catch (error) {
      console.error('Failed to start execution:', error)
    }
  }

  const handleCancel = async () => {
    if (executionId) {
      await cancelExecution(executionId)
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

  const progressPercent = useMemo(() => {
    if (nodeStats.total === 0) return 0
    return Math.round((nodeStats.completed / nodeStats.total) * 100)
  }, [nodeStats])

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
        <Button type="text" size="small" onClick={onClose}>
          ×
        </Button>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        {/* Connection Status */}
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
          <span>{t('execution.realtime')}</span>
          <Badge status={isConnected ? 'success' : 'error'} text={isConnected ? 'Live' : 'Disconnected'} />
        </div>

        {/* Progress */}
        {execution?.status === 'running' && (
          <div>
            <Progress
              percent={progressPercent}
              size="small"
              status="active"
              strokeColor={{
                '0%': '#108ee9',
                '100%': '#87d068',
              }}
            />
            <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>
              {nodeStats.completed}/{nodeStats.total} 節點完成
              {nodeStats.running > 0 && ` (${nodeStats.running} 執行中)`}
            </div>
          </div>
        )}

        {/* Node Stats */}
        <div style={{ display: 'flex', gap: 8 }}>
          <Tag color="success">{nodeStats.completed} 完成</Tag>
          {nodeStats.running > 0 && <Tag color="processing">{nodeStats.running} 執行中</Tag>}
          {nodeStats.failed > 0 && <Tag color="error">{nodeStats.failed} 失敗</Tag>}
        </div>

        {/* Error Message */}
        {execution?.error && (
          <div
            style={{
              background: '#fff2f0',
              border: '1px solid #ffccc7',
              borderRadius: 4,
              padding: 8,
              fontSize: 12,
              color: '#cf1322',
            }}
          >
            {execution.error}
          </div>
        )}

        {/* Actions */}
        <Space>
          {execution?.status === 'running' && (
            <Button size="small" danger onClick={handleCancel}>
              {t('execution.cancel')}
            </Button>
          )}
          {(execution?.status === 'completed' ||
            execution?.status === 'failed' ||
            execution?.status === 'cancelled') && (
            <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={handleStart}>
              重新執行
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
