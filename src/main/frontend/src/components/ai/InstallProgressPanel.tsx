import React from 'react'
import { Progress, Typography, Collapse, Tag, Space } from 'antd'
import {
  CloudDownloadOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  CaretRightOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { getLocale } from '../../utils/locale'
import styles from './InstallProgressPanel.module.css'

const { Text } = Typography
const { Panel } = Collapse

export interface InstallStep {
  id: string
  name: string
  status: 'pending' | 'running' | 'completed' | 'error'
  progress?: number
  message?: string
  startTime?: number
  endTime?: number
}

export interface InstallLog {
  timestamp: number
  level: 'info' | 'warn' | 'error'
  message: string
}

interface InstallProgressPanelProps {
  nodeType: string
  nodeLabel: string
  status: 'installing' | 'completed' | 'error'
  steps: InstallStep[]
  logs?: InstallLog[]
  overallProgress: number
  estimatedTimeLeft?: number
  errorMessage?: string
  onRetry?: () => void
}

/**
 * Enhanced install progress panel with detailed steps and logs
 */
export const InstallProgressPanel: React.FC<InstallProgressPanelProps> = ({
  nodeType,
  nodeLabel,
  status,
  steps,
  logs = [],
  overallProgress,
  estimatedTimeLeft,
  errorMessage,
}) => {
  const { t } = useTranslation()

  const getStatusIcon = (stepStatus: InstallStep['status']) => {
    switch (stepStatus) {
      case 'completed':
        return <CheckCircleOutlined className={styles.iconSuccess} />
      case 'running':
        return <LoadingOutlined className={styles.iconRunning} spin />
      case 'error':
        return <WarningOutlined className={styles.iconError} />
      default:
        return <ClockCircleOutlined className={styles.iconPending} />
    }
  }

  const getStatusText = () => {
    switch (status) {
      case 'installing':
        return t('install.installing')
      case 'completed':
        return t('install.completed')
      case 'error':
        return t('install.failed')
      default:
        return t('install.preparing')
    }
  }

  const getProgressStatus = () => {
    if (status === 'error') return 'exception'
    if (status === 'completed') return 'success'
    return 'active'
  }

  const formatDuration = (ms: number): string => {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
  }

  const formatEstimatedTime = (seconds?: number): string => {
    if (!seconds) return ''
    if (seconds < 60) return t('install.estimatedSeconds', { count: seconds })
    return t('install.estimatedMinutes', { count: Math.ceil(seconds / 60) })
  }

  return (
    <div
      className={styles.container}
      role="region"
      aria-label={t('install.progressLabel', { name: nodeLabel })}
      aria-live="polite"
    >
      {/* Header */}
      <div className={styles.header}>
        <CloudDownloadOutlined className={styles.headerIcon} />
        <div className={styles.headerContent}>
          <Text strong>{nodeLabel}</Text>
          <Text type="secondary" className={styles.nodeType}>
            {nodeType}
          </Text>
        </div>
        <Tag color={status === 'error' ? 'error' : status === 'completed' ? 'success' : 'processing'}>
          {getStatusText()}
        </Tag>
      </div>

      {/* Overall Progress */}
      <div className={styles.progressSection}>
        <Progress
          percent={overallProgress}
          status={getProgressStatus()}
          strokeColor={{
            '0%': '#108ee9',
            '100%': '#87d068',
          }}
          aria-label={t('install.progressPercent', { percent: overallProgress })}
        />
        {estimatedTimeLeft !== undefined && status === 'installing' && (
          <Text type="secondary" className={styles.estimatedTime}>
            {t('install.estimatedTimeLeft')}: {formatEstimatedTime(estimatedTimeLeft)}
          </Text>
        )}
      </div>

      {/* Error Message */}
      {errorMessage && (
        <div className={styles.errorSection}>
          <WarningOutlined className={styles.errorIcon} />
          <Text type="danger">{errorMessage}</Text>
        </div>
      )}

      {/* Steps */}
      <div className={styles.stepsSection}>
        <Text strong className={styles.sectionTitle}>
          {t('install.steps')}
        </Text>
        <div className={styles.stepsList}>
          {steps.map((step) => (
            <div
              key={step.id}
              className={`${styles.stepItem} ${styles[`step${step.status.charAt(0).toUpperCase() + step.status.slice(1)}`]}`}
            >
              <span className={styles.stepIcon}>{getStatusIcon(step.status)}</span>
              <div className={styles.stepContent}>
                <Text className={styles.stepName}>{step.name}</Text>
                {step.message && (
                  <Text type="secondary" className={styles.stepMessage}>
                    {step.message}
                  </Text>
                )}
              </div>
              {step.status === 'running' && step.progress !== undefined && (
                <Progress
                  percent={step.progress}
                  size="small"
                  className={styles.stepProgress}
                  showInfo={false}
                />
              )}
              {step.status === 'completed' && step.startTime && step.endTime && (
                <Text type="secondary" className={styles.stepDuration}>
                  {formatDuration(step.endTime - step.startTime)}
                </Text>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Logs Section */}
      {logs.length > 0 && (
        <Collapse
          ghost
          expandIcon={({ isActive }) => (
            <CaretRightOutlined rotate={isActive ? 90 : 0} />
          )}
        >
          <Panel
            header={
              <Space>
                <Text type="secondary">{t('install.logs')}</Text>
                <Tag>{logs.length}</Tag>
              </Space>
            }
            key="logs"
          >
            <div className={styles.logsContainer}>
              {logs.map((log, index) => (
                <div
                  key={index}
                  className={`${styles.logEntry} ${styles[`log${log.level.charAt(0).toUpperCase() + log.level.slice(1)}`]}`}
                >
                  <Text type="secondary" className={styles.logTime}>
                    {new Date(log.timestamp).toLocaleTimeString(getLocale())}
                  </Text>
                  <Text className={styles.logMessage}>{log.message}</Text>
                </div>
              ))}
            </div>
          </Panel>
        </Collapse>
      )}
    </div>
  )
}

export default InstallProgressPanel
