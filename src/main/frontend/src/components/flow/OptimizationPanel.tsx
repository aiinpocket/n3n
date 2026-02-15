import React, { useEffect, useRef, useState } from 'react'
import {
  Drawer,
  Button,
  Space,
  Typography,
  Tag,
  Card,
  Empty,
  Spin,
  Alert,
  Collapse,
  Tooltip,
  Badge,
  Divider,
  Modal,
  message,
} from 'antd'
import {
  ThunderboltOutlined,
  RocketOutlined,
  BranchesOutlined,
  MergeOutlined,
  DeleteOutlined,
  OrderedListOutlined,
  CheckCircleOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  optimizerApi,
  OptimizationSuggestion,
  FlowOptimizationResponse,
  getSuggestionTypeColor,
  getSuggestionTypeName,
  getPriorityLabel,
} from '../../api/optimizer'
import { aiAssistantApi } from '../../api/aiAssistant'
import type { FlowDefinition } from '../../api/flow'
import { extractApiError } from '../../utils/errorMessages'

const { Text, Paragraph } = Typography

interface OptimizationPanelProps {
  visible: boolean
  onClose: () => void
  flowDefinition: FlowDefinition | null
  flowId?: string
  onHighlightNodes?: (nodeIds: string[]) => void
  onApplyOptimization?: (updatedDefinition: FlowDefinition) => void
}

const OptimizationPanel: React.FC<OptimizationPanelProps> = ({
  visible,
  onClose,
  flowDefinition,
  flowId,
  onHighlightNodes,
  onApplyOptimization,
}) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<FlowOptimizationResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [appliedIds, setAppliedIds] = useState<Set<string>>(new Set())
  const [applying, setApplying] = useState(false)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  // Reset state when panel closes
  useEffect(() => {
    if (visible) {
      setResult(null)
      setError(null)
      setAppliedIds(new Set())
      setApplying(false)
    }
  }, [visible])

  const handleAnalyze = async () => {
    if (!flowDefinition) return

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await optimizerApi.analyzeFlow(flowDefinition)
      if (!mountedRef.current) return
      setResult(response)

      if (!response.success) {
        setError(response.error || t('optimizer.analyzeFailed'))
      }
    } catch {
      if (!mountedRef.current) return
      setError(t('optimizer.analyzeFailed'))
    } finally {
      if (mountedRef.current) setLoading(false)
    }
  }

  const getSuggestionIcon = (type: OptimizationSuggestion['type']) => {
    const icons: Record<string, React.ReactNode> = {
      parallel: <BranchesOutlined />,
      merge: <MergeOutlined />,
      remove: <DeleteOutlined />,
      reorder: <OrderedListOutlined />,
    }
    return icons[type] || <InfoCircleOutlined />
  }

  const handleNodeClick = (nodeIds: string[]) => {
    onHighlightNodes?.(nodeIds)
  }

  const handleApplySuggestions = async (suggestionIds: string[]) => {
    if (!flowDefinition || !result?.suggestions) return

    setApplying(true)
    try {
      const suggestionsToApply = result.suggestions
        .filter((_, i) => suggestionIds.includes(`suggestion-${i}`))
        .map((s, i) => ({
          id: `suggestion-${i}`,
          type: s.type,
          affectedNodes: s.affectedNodes,
        }))

      const response = await aiAssistantApi.applySuggestions({
        flowId: flowId || '',
        version: '',
        suggestionIds: suggestionsToApply.map(s => s.id),
      })

      if (!mountedRef.current) return

      if (response.success && response.updatedDefinition) {
        const newApplied = new Set(appliedIds)
        response.appliedSuggestions.forEach(id => newApplied.add(id))
        setAppliedIds(newApplied)

        onApplyOptimization?.(response.updatedDefinition as unknown as FlowDefinition)
        message.success(t('optimizer.applySuccess', { count: response.appliedCount }))
      } else {
        message.error(response.error || t('optimizer.applyFailed'))
      }
    } catch (err) {
      if (!mountedRef.current) return
      message.error(extractApiError(err, t('optimizer.applyFailed')))
    } finally {
      setApplying(false)
    }
  }

  const handleApplyAll = () => {
    if (!result?.suggestions) return
    const allIds = result.suggestions.map((_, i) => `suggestion-${i}`)
      .filter(id => !appliedIds.has(id))
    if (allIds.length === 0) return
    Modal.confirm({
      title: t('optimizer.applyAllConfirmTitle'),
      content: t('optimizer.applyAllConfirmContent', { count: allIds.length }),
      okText: t('optimizer.applyAll'),
      cancelText: t('common.cancel'),
      onOk: () => handleApplySuggestions(allIds),
    })
    return // prevent immediate execution
  }

  const renderSuggestion = (suggestion: OptimizationSuggestion, index: number) => {
    const priorityInfo = getPriorityLabel(suggestion.priority)
    const suggestionId = `suggestion-${index}`
    const isApplied = appliedIds.has(suggestionId)

    return (
      <Card
        key={index}
        size="small"
        style={{
          marginBottom: 12,
          borderLeft: `3px solid ${isApplied ? '#22C55E' : getSuggestionTypeColor(suggestion.type)}`,
          opacity: isApplied ? 0.7 : 1,
        }}
        bodyStyle={{ padding: 12 }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
          <Space>
            <span style={{ color: isApplied ? '#22C55E' : getSuggestionTypeColor(suggestion.type), fontSize: 16 }}>
              {isApplied ? <CheckCircleOutlined /> : getSuggestionIcon(suggestion.type)}
            </span>
            <Text strong style={isApplied ? { textDecoration: 'line-through', opacity: 0.6 } : undefined}>
              {suggestion.title}
            </Text>
          </Space>
          <Space size={4}>
            <Tag color={priorityInfo.color} style={{ margin: 0 }}>
              {t(priorityInfo.text)}
            </Tag>
            {!isApplied && (
              <Tooltip title={t('optimizer.applySuggestion')}>
                <Button
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => handleApplySuggestions([suggestionId])}
                  loading={applying}
                  style={{ padding: 0 }}
                />
              </Tooltip>
            )}
            {isApplied && (
              <Tag color="success" style={{ margin: 0 }}>{t('optimizer.applied')}</Tag>
            )}
          </Space>
        </div>

        <Paragraph
          style={{ marginBottom: 8, color: 'var(--color-text-secondary)' }}
          ellipsis={{ rows: 3, expandable: true }}
        >
          {suggestion.description}
        </Paragraph>

        {suggestion.affectedNodes.length > 0 && (
          <div style={{ marginTop: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {t('optimizer.affectedNodes')}:
            </Text>
            <div style={{ marginTop: 4 }}>
              {suggestion.affectedNodes.map((nodeId) => (
                <Tooltip key={nodeId} title={t('optimizer.clickToHighlight')}>
                  <Tag
                    style={{ cursor: 'pointer', marginBottom: 4 }}
                    onClick={() => handleNodeClick([nodeId])}
                  >
                    {nodeId}
                  </Tag>
                </Tooltip>
              ))}
            </div>
          </div>
        )}
      </Card>
    )
  }

  const renderSuggestionsByType = () => {
    if (!result?.suggestions?.length) return null

    const grouped = result.suggestions.reduce((acc, suggestion, index) => {
      if (!acc[suggestion.type]) {
        acc[suggestion.type] = []
      }
      acc[suggestion.type].push({ suggestion, originalIndex: index })
      return acc
    }, {} as Record<string, { suggestion: OptimizationSuggestion; originalIndex: number }[]>)

    return (
      <Collapse
        defaultActiveKey={Object.keys(grouped)}
        ghost
        items={Object.entries(grouped).map(([type, suggestions]) => ({
          key: type,
          label: (
            <Space>
              <span style={{ color: getSuggestionTypeColor(type as OptimizationSuggestion['type']) }}>
                {getSuggestionIcon(type as OptimizationSuggestion['type'])}
              </span>
              <span>{t(getSuggestionTypeName(type as OptimizationSuggestion['type']))}</span>
              <Badge count={suggestions.length} style={{ backgroundColor: 'var(--color-text-muted)' }} />
            </Space>
          ),
          children: suggestions.map(({ suggestion: s, originalIndex }) => renderSuggestion(s, originalIndex)),
        }))}
      />
    )
  }

  const nodeCount = flowDefinition?.nodes?.length || 0
  const edgeCount = flowDefinition?.edges?.length || 0

  return (
    <Drawer
      title={
        <Space>
          <RocketOutlined style={{ color: 'var(--color-primary)' }} />
          <span>{t('optimizer.title')}</span>
        </Space>
      }
      placement="right"
      width={420}
      open={visible}
      onClose={onClose}
      extra={
        <Tooltip
          title={
            !flowDefinition ? t('optimizer.noFlow') :
            nodeCount === 0 ? t('optimizer.requiresNodes') :
            undefined
          }
        >
          <Button
            type="primary"
            icon={loading ? <Spin size="small" /> : <ThunderboltOutlined />}
            onClick={handleAnalyze}
            loading={loading}
            disabled={!flowDefinition || nodeCount === 0}
          >
            {loading ? t('optimizer.analyzing') : t('optimizer.analyze')}
          </Button>
        </Tooltip>
      }
    >
      {/* Flow Info */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space split={<Divider type="vertical" />}>
          <span>
            <Text type="secondary">{t('optimizer.nodes')}: </Text>
            <Text strong>{nodeCount}</Text>
          </span>
          <span>
            <Text type="secondary">{t('optimizer.edges')}: </Text>
            <Text strong>{edgeCount}</Text>
          </span>
        </Space>
      </Card>

      {/* Initial State */}
      {!loading && !result && !error && (
        <Empty
          image={<RocketOutlined style={{ fontSize: 64, color: 'var(--color-text-muted)' }} />}
          description={
            <div>
              <Paragraph>{t('optimizer.description')}</Paragraph>
              <Paragraph type="secondary" style={{ fontSize: 12 }}>
                {t('optimizer.poweredBy')}
              </Paragraph>
            </div>
          }
        >
          <Tooltip title={nodeCount === 0 ? t('optimizer.requiresNodes') : undefined}>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={handleAnalyze}
              disabled={nodeCount === 0}
            >
              {t('optimizer.startAnalysis')}
            </Button>
          </Tooltip>
        </Empty>
      )}

      {/* Loading */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>{t('optimizer.analyzingFlow')}</Paragraph>
          <Text type="secondary">{t('optimizer.aiThinking')}</Text>
        </div>
      )}

      {/* Error */}
      {error && (
        <Alert
          type="warning"
          message={t('optimizer.analysisFailed')}
          description={
            <div>
              <Paragraph style={{ marginBottom: 8 }}>{error}</Paragraph>
              <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
                {t('optimizer.serviceHint')}
              </Paragraph>
            </div>
          }
          showIcon
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={handleAnalyze}>
              {t('common.retry')}
            </Button>
          }
        />
      )}

      {/* Results */}
      {result && !loading && (
        <div>
          {result.success && result.suggestions.length === 0 && (
            <Alert
              type="success"
              message={t('optimizer.noSuggestions')}
              description={t('optimizer.flowIsOptimal')}
              icon={<CheckCircleOutlined />}
              showIcon
            />
          )}

          {result.suggestions.length > 0 && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <Alert
                  type="info"
                  message={t('optimizer.suggestionsFound', { count: result.suggestions.length })}
                  showIcon
                  style={{ flex: 1, marginRight: 8 }}
                />
                {appliedIds.size < result.suggestions.length && (
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={handleApplyAll}
                    loading={applying}
                  >
                    {t('optimizer.applyAll')}
                  </Button>
                )}
              </div>

              {result.analysisTimeMs && (
                <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
                  {t('optimizer.analysisTime', { time: (result.analysisTimeMs / 1000).toFixed(1) })}
                </Text>
              )}

              {renderSuggestionsByType()}
            </>
          )}
        </div>
      )}
    </Drawer>
  )
}

export default OptimizationPanel
