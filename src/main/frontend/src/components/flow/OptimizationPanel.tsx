import React, { useState } from 'react'
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
import type { FlowDefinition } from '../../api/flow'

const { Text, Paragraph } = Typography

interface OptimizationPanelProps {
  visible: boolean
  onClose: () => void
  flowDefinition: FlowDefinition | null
  onHighlightNodes?: (nodeIds: string[]) => void
}

const OptimizationPanel: React.FC<OptimizationPanelProps> = ({
  visible,
  onClose,
  flowDefinition,
  onHighlightNodes,
}) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<FlowOptimizationResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleAnalyze = async () => {
    if (!flowDefinition) return

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await optimizerApi.analyzeFlow(flowDefinition)
      setResult(response)

      if (!response.success && response.error) {
        setError(response.error)
      }
    } catch {
      setError(t('optimizer.analyzeFailed'))
    } finally {
      setLoading(false)
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

  const renderSuggestion = (suggestion: OptimizationSuggestion, index: number) => {
    const priorityInfo = getPriorityLabel(suggestion.priority)

    return (
      <Card
        key={index}
        size="small"
        style={{
          marginBottom: 12,
          borderLeft: `3px solid ${getSuggestionTypeColor(suggestion.type)}`,
        }}
        bodyStyle={{ padding: 12 }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
          <Space>
            <span style={{ color: getSuggestionTypeColor(suggestion.type), fontSize: 16 }}>
              {getSuggestionIcon(suggestion.type)}
            </span>
            <Text strong>{suggestion.title}</Text>
          </Space>
          <Tag color={priorityInfo.color} style={{ margin: 0 }}>
            {t(priorityInfo.text)}
          </Tag>
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

    const grouped = result.suggestions.reduce((acc, suggestion) => {
      if (!acc[suggestion.type]) {
        acc[suggestion.type] = []
      }
      acc[suggestion.type].push(suggestion)
      return acc
    }, {} as Record<string, OptimizationSuggestion[]>)

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
          children: suggestions.map((s, i) => renderSuggestion(s, i)),
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
        <Button
          type="primary"
          icon={loading ? <Spin size="small" /> : <ThunderboltOutlined />}
          onClick={handleAnalyze}
          loading={loading}
          disabled={!flowDefinition || nodeCount === 0}
        >
          {loading ? t('optimizer.analyzing') : t('optimizer.analyze')}
        </Button>
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
          <Button
            type="primary"
            icon={<ThunderboltOutlined />}
            onClick={handleAnalyze}
            disabled={nodeCount === 0}
          >
            {t('optimizer.startAnalysis')}
          </Button>
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
          type="error"
          message={t('optimizer.analysisFailed')}
          description={error}
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
              <Alert
                type="info"
                message={t('optimizer.suggestionsFound', { count: result.suggestions.length })}
                style={{ marginBottom: 16 }}
                showIcon
              />

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
