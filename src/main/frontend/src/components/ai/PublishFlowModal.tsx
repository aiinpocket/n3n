import React, { useState, useEffect, useCallback } from 'react'
import {
  Modal,
  Steps,
  Button,
  Space,
  Typography,
  Card,
  Spin,
  Alert,
  Divider,
  message,
} from 'antd'
import {
  RocketOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import SuggestionCard from './SuggestionCard'
import {
  aiAssistantApi,
  type PublishAnalysisResponse,
  type OptimizationSuggestion,
} from '../../api/aiAssistant'
import logger from '../../utils/logger'

const { Text, Paragraph, Title } = Typography

interface PublishFlowModalProps {
  open: boolean
  onClose: () => void
  flowDefinition: {
    nodes: unknown[]
    edges: unknown[]
  }
  flowId: string
  version: string
  onPublish: () => Promise<void>
  onHighlightNodes?: (nodeIds: string[]) => void
}

type Step = 'analyzing' | 'review' | 'publishing' | 'complete'

const PublishFlowModal: React.FC<PublishFlowModalProps> = ({
  open,
  onClose,
  flowDefinition,
  flowId,
  version,
  onPublish,
  onHighlightNodes,
}) => {
  const { t } = useTranslation()
  const [step, setStep] = useState<Step>('analyzing')
  const [analysis, setAnalysis] = useState<PublishAnalysisResponse | null>(null)
  const [selectedSuggestions, setSelectedSuggestions] = useState<Set<string>>(new Set())
  const [applying, setApplying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const startAnalysis = useCallback(async () => {
    setStep('analyzing')
    setError(null)
    setAnalysis(null)
    setSelectedSuggestions(new Set())

    try {
      const result = await aiAssistantApi.analyzeForPublish({
        definition: flowDefinition,
        flowId,
        version,
      })
      setAnalysis(result)
      setStep('review')

      // Auto-select high priority suggestions
      if (result.suggestions) {
        const highPriority = result.suggestions
          .filter((s) => s.priority === 1)
          .map((s) => s.id)
        setSelectedSuggestions(new Set(highPriority))
      }
    } catch (err) {
      logger.error('Analysis failed:', err)
      setError(t('aiAssistant.analyzeFailed'))
      // Allow publishing even if analysis fails
      setStep('review')
    }
  }, [flowDefinition, flowId, version, t])

  // Start analysis when modal opens
  useEffect(() => {
    if (open) {
      startAnalysis()
    } else {
      // Reset state when modal closes
      setStep('analyzing')
      setAnalysis(null)
      setSelectedSuggestions(new Set())
      setError(null)
    }
  }, [open, startAnalysis])

  const toggleSuggestion = (id: string) => {
    setSelectedSuggestions((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(id)) {
        newSet.delete(id)
      } else {
        newSet.add(id)
      }
      return newSet
    })
  }

  const handleApplyAndPublish = async () => {
    setApplying(true)

    try {
      // Apply suggestions if any selected
      if (selectedSuggestions.size > 0) {
        await aiAssistantApi.applySuggestions({
          flowId,
          version,
          suggestionIds: Array.from(selectedSuggestions),
        })
        message.success(
          t('aiAssistant.suggestionsApplied', {
            count: selectedSuggestions.size,
          })
        )
      }

      setStep('publishing')
      await onPublish()
      setStep('complete')

      // Auto close after success
      setTimeout(() => {
        onClose()
      }, 1500)
    } catch (err) {
      logger.error('Publish failed:', err)
      message.error(t('flow.publishFailed'))
      setStep('review')
    } finally {
      setApplying(false)
    }
  }

  const handleSkipAndPublish = async () => {
    setStep('publishing')
    try {
      await onPublish()
      setStep('complete')
      setTimeout(() => {
        onClose()
      }, 1500)
    } catch (err) {
      logger.error('Publish failed:', err)
      message.error(t('flow.publishFailed'))
      setStep('review')
    }
  }

  const handleViewNodes = (nodeIds: string[]) => {
    onHighlightNodes?.(nodeIds)
  }

  const getStepIndex = () => {
    switch (step) {
      case 'analyzing':
        return 0
      case 'review':
        return 1
      case 'publishing':
      case 'complete':
        return 2
      default:
        return 0
    }
  }

  const nodeCount = flowDefinition?.nodes?.length || 0
  const edgeCount = flowDefinition?.edges?.length || 0

  return (
    <Modal
      title={
        <Space>
          <RocketOutlined style={{ color: 'var(--color-primary)' }} />
          <span>{t('flow.publish')}</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={640}
      footer={null}
      maskClosable={false}
    >
      {/* Steps indicator */}
      <Steps
        current={getStepIndex()}
        items={[
          { title: t('aiAssistant.analyzing') },
          { title: t('aiAssistant.reviewSuggestions') },
          { title: t('common.complete') },
        ]}
        style={{ marginBottom: 24 }}
      />

      {/* Analyzing state */}
      {step === 'analyzing' && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>
            {t('aiAssistant.analyzingFlow')}
          </Paragraph>
          <Text type="secondary">{t('aiAssistant.aiThinking')}</Text>
        </div>
      )}

      {/* Review state */}
      {step === 'review' && (
        <>
          {/* Flow Summary */}
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space split={<Divider type="vertical" />}>
              <span>
                <Text type="secondary">{t('optimizer.nodes')}: </Text>
                <Text strong>{analysis?.summary?.nodeCount || nodeCount}</Text>
              </span>
              <span>
                <Text type="secondary">{t('optimizer.edges')}: </Text>
                <Text strong>{analysis?.summary?.edgeCount || edgeCount}</Text>
              </span>
              <span>
                <Text type="secondary">{t('flow.version')}: </Text>
                <Text strong>{version}</Text>
              </span>
            </Space>
          </Card>

          {/* Error message */}
          {error && (
            <Alert
              type="warning"
              message={error}
              description={t('aiAssistant.canStillPublish')}
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          {/* No suggestions */}
          {analysis?.success && (!analysis.suggestions || analysis.suggestions.length === 0) && (
            <Alert
              type="success"
              message={t('aiAssistant.noSuggestions')}
              description={t('aiAssistant.flowIsOptimal')}
              icon={<CheckCircleOutlined />}
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          {/* Suggestions list */}
          {analysis?.suggestions && analysis.suggestions.length > 0 && (
            <>
              <Alert
                type="info"
                message={t('aiAssistant.suggestionsFound', {
                  count: analysis.suggestions.length,
                })}
                showIcon
                icon={<ThunderboltOutlined />}
                style={{ marginBottom: 16 }}
              />

              {analysis.analysisTimeMs && (
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
                  {t('aiAssistant.analysisTime', {
                    time: (analysis.analysisTimeMs / 1000).toFixed(1),
                  })}
                </Text>
              )}

              <div style={{ maxHeight: 320, overflowY: 'auto', marginBottom: 16 }}>
                {analysis.suggestions.map((suggestion: OptimizationSuggestion) => (
                  <SuggestionCard
                    key={suggestion.id}
                    suggestion={suggestion}
                    selected={selectedSuggestions.has(suggestion.id)}
                    onToggle={() => toggleSuggestion(suggestion.id)}
                    onViewNodes={() => handleViewNodes(suggestion.affectedNodes)}
                  />
                ))}
              </div>

              <Alert
                type="info"
                showIcon={false}
                message={
                  <Space>
                    <InfoCircleOutlined />
                    <span>
                      {t('aiAssistant.suggestionHint')}
                    </span>
                  </Space>
                }
                style={{ marginBottom: 16, backgroundColor: 'var(--color-bg-secondary)' }}
              />
            </>
          )}

          <Divider style={{ margin: '16px 0' }} />

          {/* Actions */}
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Button onClick={handleSkipAndPublish}>
              {t('aiAssistant.skipAndPublish')}
            </Button>
            <Button
              type="primary"
              onClick={handleApplyAndPublish}
              loading={applying}
              icon={<RocketOutlined />}
            >
              {selectedSuggestions.size > 0
                ? t('aiAssistant.applyAndPublish', {
                    count: selectedSuggestions.size,
                  })
                : t('flow.publish')}
            </Button>
          </div>
        </>
      )}

      {/* Publishing state */}
      {step === 'publishing' && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>{t('flow.publishing')}</Paragraph>
        </div>
      )}

      {/* Complete state */}
      {step === 'complete' && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <CheckCircleOutlined style={{ fontSize: 64, color: 'var(--color-success)' }} />
          <Title level={3} style={{ marginTop: 16 }}>
            {t('flow.publishSuccess')}
          </Title>
        </div>
      )}
    </Modal>
  )
}

export default PublishFlowModal
