import React, { useState, useEffect, useMemo } from 'react'
import { Typography, Progress, Tag } from 'antd'
import {
  LoadingOutlined,
  BulbOutlined,
  SearchOutlined,
  ToolOutlined,
  CheckCircleOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import styles from './AIThinkingIndicator.module.css'

const { Text, Paragraph } = Typography

export interface ThinkingStage {
  key: string
  label: string
  icon: React.ReactNode
  description: string
}

// Default stage keys - labels resolved via i18n in component
export const DEFAULT_STAGE_KEYS = [
  { key: 'understanding', labelKey: 'aiThinking.understanding', descKey: 'aiThinking.understandingDesc', icon: <BulbOutlined /> },
  { key: 'analyzing', labelKey: 'aiThinking.analyzing', descKey: 'aiThinking.analyzingDesc', icon: <SearchOutlined /> },
  { key: 'designing', labelKey: 'aiThinking.designing', descKey: 'aiThinking.designingDesc', icon: <ToolOutlined /> },
  { key: 'generating', labelKey: 'aiThinking.generating', descKey: 'aiThinking.generatingDesc', icon: <CheckCircleOutlined /> },
]

interface Props {
  stages?: ThinkingStage[]
  currentStage?: number
  showProgress?: boolean
  showThoughts?: boolean
  thoughts?: string[]
  animated?: boolean
}

/**
 * AI 思考指示器
 * 顯示 AI 處理過程中的階段性進度和思考過程
 */
export const AIThinkingIndicator: React.FC<Props> = ({
  stages: stagesProp,
  currentStage = 0,
  showProgress = true,
  showThoughts = true,
  thoughts = [],
  animated = true,
}) => {
  const { t } = useTranslation()

  const translatedDefaultStages: ThinkingStage[] = useMemo(() => [
    {
      key: 'understanding',
      label: t('aiThinking.understanding'),
      icon: <BulbOutlined />,
      description: t('aiThinking.understandingDesc'),
    },
    {
      key: 'analyzing',
      label: t('aiThinking.analyzing'),
      icon: <SearchOutlined />,
      description: t('aiThinking.analyzingDesc'),
    },
    {
      key: 'designing',
      label: t('aiThinking.designing'),
      icon: <ToolOutlined />,
      description: t('aiThinking.designingDesc'),
    },
    {
      key: 'generating',
      label: t('aiThinking.generating'),
      icon: <CheckCircleOutlined />,
      description: t('aiThinking.generatingDesc'),
    },
  ], [t])

  const stages = stagesProp ?? translatedDefaultStages

  const [dots, setDots] = useState('.')
  const [visibleThoughts, setVisibleThoughts] = useState<string[]>([])
  const [typingThought, setTypingThought] = useState('')
  const [typingIndex, setTypingIndex] = useState(0)

  // 動態省略號
  useEffect(() => {
    if (!animated) return
    const interval = setInterval(() => {
      setDots((prev) => (prev.length >= 3 ? '.' : prev + '.'))
    }, 500)
    return () => clearInterval(interval)
  }, [animated])

  // 打字效果顯示思考過程
  useEffect(() => {
    if (!showThoughts || thoughts.length === 0) return

    const currentThought = thoughts[visibleThoughts.length]
    if (!currentThought) return

    if (typingIndex < currentThought.length) {
      const timeout = setTimeout(() => {
        setTypingThought(currentThought.slice(0, typingIndex + 1))
        setTypingIndex((prev) => prev + 1)
      }, 20)
      return () => clearTimeout(timeout)
    } else {
      // 當前思考完成，加入可見列表
      const timeout = setTimeout(() => {
        setVisibleThoughts((prev) => [...prev, currentThought])
        setTypingThought('')
        setTypingIndex(0)
      }, 300)
      return () => clearTimeout(timeout)
    }
  }, [thoughts, visibleThoughts, typingIndex, showThoughts])

  const progress = useMemo(() => {
    if (stages.length === 0) return 0
    return Math.round(((currentStage + 1) / stages.length) * 100)
  }, [currentStage, stages.length])

  const currentStageData = stages[currentStage] || stages[0]

  return (
    <div
      className={styles.container}
      role="status"
      aria-live="polite"
      aria-busy={currentStage < stages.length - 1}
      aria-label={t('aiThinking.processing', { label: currentStageData?.label, description: currentStageData?.description })}
    >
      {/* 主要動畫區域 */}
      <div className={styles.mainArea}>
        <div
          className={`${styles.iconWrapper} ${animated ? styles.pulse : ''}`}
          aria-hidden="true"
        >
          <RobotOutlined className={styles.mainIcon} />
        </div>

        <div className={styles.textArea}>
          <Text strong className={styles.stageLabel}>
            <span aria-hidden="true">{currentStageData?.icon}</span> {currentStageData?.label}{dots}
          </Text>
          <Paragraph type="secondary" className={styles.description}>
            {currentStageData?.description}
          </Paragraph>
        </div>
      </div>

      {/* 進度條 */}
      {showProgress && (
        <div className={styles.progressArea}>
          <Progress
            percent={progress}
            size="small"
            strokeColor={{
              '0%': '#8B5CF6',
              '100%': '#eb2f96',
            }}
            status="active"
          />

          {/* 階段標籤 */}
          <div className={styles.stagesTags}>
            {stages.map((stage, index) => (
              <Tag
                key={stage.key}
                color={index < currentStage ? 'purple' : index === currentStage ? 'processing' : 'default'}
                icon={index < currentStage ? <CheckCircleOutlined /> : index === currentStage ? <LoadingOutlined /> : null}
                className={styles.stageTag}
              >
                {stage.label}
              </Tag>
            ))}
          </div>
        </div>
      )}

      {/* 思考過程 */}
      {showThoughts && (thoughts.length > 0 || visibleThoughts.length > 0) && (
        <div className={styles.thoughtsArea}>
          <Text type="secondary" className={styles.thoughtsTitle}>
            <BulbOutlined /> {t('aiThinking.thoughtProcess')}
          </Text>
          <div className={styles.thoughtsList}>
            {visibleThoughts.map((thought, index) => (
              <div key={index} className={styles.thoughtItem}>
                <CheckCircleOutlined className={styles.thoughtIcon} />
                <Text>{thought}</Text>
              </div>
            ))}
            {typingThought && (
              <div className={styles.thoughtItem}>
                <LoadingOutlined className={styles.thoughtIcon} />
                <Text>{typingThought}<span className={styles.cursor}>|</span></Text>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default AIThinkingIndicator
