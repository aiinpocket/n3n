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
import styles from './AIThinkingIndicator.module.css'

const { Text, Paragraph } = Typography

export interface ThinkingStage {
  key: string
  label: string
  icon: React.ReactNode
  description: string
}

// 預設的思考階段
export const DEFAULT_STAGES: ThinkingStage[] = [
  {
    key: 'understanding',
    label: '理解需求',
    icon: <BulbOutlined />,
    description: '正在分析您的描述...',
  },
  {
    key: 'analyzing',
    label: '分析元件',
    icon: <SearchOutlined />,
    description: '識別所需的節點類型...',
  },
  {
    key: 'designing',
    label: '設計流程',
    icon: <ToolOutlined />,
    description: '規劃節點連接和邏輯...',
  },
  {
    key: 'generating',
    label: '生成結果',
    icon: <CheckCircleOutlined />,
    description: '完成流程定義...',
  },
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
  stages = DEFAULT_STAGES,
  currentStage = 0,
  showProgress = true,
  showThoughts = true,
  thoughts = [],
  animated = true,
}) => {
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
      aria-label={`AI 處理中：${currentStageData?.label} - ${currentStageData?.description}`}
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
              '0%': '#722ed1',
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
            <BulbOutlined /> AI 思考過程：
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
