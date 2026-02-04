import React, { useState, useEffect, useCallback } from 'react'
import { List, Card, Tag, Typography, Skeleton, Button, Space, Tooltip } from 'antd'
import {
  FolderOutlined,
  NodeIndexOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  EyeOutlined,
  StarOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useDebounce } from '../../hooks/useDebounce'
import api from '../../api/client'
import styles from './SimilarFlowsPanel.module.css'

const { Text, Paragraph } = Typography

interface SimilarFlow {
  flowId: string
  name: string
  description: string
  similarity: number
  nodeCount: number
  nodeTypes: string[]
  createdAt: string
  matchedKeywords: string[]
  isTemplate: boolean
}

interface Props {
  query: string
  onSelectFlow?: (flowId: string) => void
  onUseAsTemplate?: (flowId: string) => void
  minQueryLength?: number
  maxResults?: number
}

/**
 * 類似流程推薦面板
 * 根據用戶輸入的描述推薦相似的現有流程
 */
export const SimilarFlowsPanel: React.FC<Props> = ({
  query,
  onSelectFlow,
  onUseAsTemplate,
  minQueryLength = 5,
  maxResults = 5,
}) => {
  const { t } = useTranslation()
  const [flows, setFlows] = useState<SimilarFlow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 防抖處理
  const debouncedQuery = useDebounce(query, 500)

  const fetchSimilarFlows = useCallback(async (searchQuery: string) => {
    if (searchQuery.length < minQueryLength) {
      setFlows([])
      return
    }

    setLoading(true)
    setError(null)

    try {
      const response = await api.get<SimilarFlow[]>('/ai-assistant/similar-flows', {
        params: { query: searchQuery, limit: maxResults },
      })
      setFlows(response.data)
    } catch (err) {
      console.error('Failed to fetch similar flows:', err)
      setError('無法載入類似流程')
      setFlows([])
    } finally {
      setLoading(false)
    }
  }, [minQueryLength, maxResults])

  useEffect(() => {
    fetchSimilarFlows(debouncedQuery)
  }, [debouncedQuery, fetchSimilarFlows])

  // 格式化相似度顯示
  const formatSimilarity = (similarity: number) => {
    const percent = Math.round(similarity * 100)
    if (percent >= 80) return { text: `${percent}% 高度相似`, color: 'success' }
    if (percent >= 50) return { text: `${percent}% 中度相似`, color: 'warning' }
    return { text: `${percent}% 相似`, color: 'default' }
  }

  // 格式化時間
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    const now = new Date()
    const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return '今天'
    if (diffDays === 1) return '昨天'
    if (diffDays < 7) return `${diffDays} 天前`
    if (diffDays < 30) return `${Math.floor(diffDays / 7)} 週前`
    return date.toLocaleDateString('zh-TW')
  }

  if (query.length < minQueryLength) {
    return null  // 查詢太短時不顯示
  }

  if (loading) {
    return (
      <div className={styles.container}>
        <Text type="secondary" className={styles.title}>
          <FolderOutlined /> {t('aiAssistant.similarFlows', '類似流程')}
        </Text>
        <Skeleton active paragraph={{ rows: 2 }} />
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.container}>
        <Text type="secondary">{error}</Text>
      </div>
    )
  }

  if (flows.length === 0) {
    return null  // 沒有類似流程時不顯示
  }

  return (
    <div className={styles.container}>
      <Text type="secondary" className={styles.title}>
        <FolderOutlined /> {t('aiAssistant.similarFlows', '類似流程')} ({flows.length})
      </Text>

      <List
        size="small"
        dataSource={flows}
        renderItem={(flow) => {
          const similarity = formatSimilarity(flow.similarity)

          return (
            <List.Item className={styles.flowItem}>
              <Card size="small" className={styles.flowCard} hoverable>
                <div className={styles.flowHeader}>
                  <div className={styles.flowName}>
                    {flow.isTemplate && (
                      <Tooltip title="模板">
                        <StarOutlined className={styles.templateIcon} />
                      </Tooltip>
                    )}
                    <Text strong ellipsis={{ tooltip: flow.name }}>
                      {flow.name}
                    </Text>
                  </div>
                  <Tag color={similarity.color as 'success' | 'warning' | 'default'}>
                    {similarity.text}
                  </Tag>
                </div>

                {flow.description && (
                  <Paragraph
                    type="secondary"
                    className={styles.flowDescription}
                    ellipsis={{ rows: 2 }}
                  >
                    {flow.description}
                  </Paragraph>
                )}

                <div className={styles.flowMeta}>
                  <Space size={12}>
                    <Text type="secondary" className={styles.metaItem}>
                      <NodeIndexOutlined /> {flow.nodeCount} 個節點
                    </Text>
                    <Text type="secondary" className={styles.metaItem}>
                      <ClockCircleOutlined /> {formatDate(flow.createdAt)}
                    </Text>
                  </Space>
                </div>

                {flow.nodeTypes.length > 0 && (
                  <div className={styles.nodeTypes}>
                    {flow.nodeTypes.slice(0, 4).map((type) => (
                      <Tag key={type} className={styles.nodeTypeTag}>
                        {type}
                      </Tag>
                    ))}
                    {flow.nodeTypes.length > 4 && (
                      <Tag className={styles.nodeTypeTag}>
                        +{flow.nodeTypes.length - 4}
                      </Tag>
                    )}
                  </div>
                )}

                {flow.matchedKeywords.length > 0 && (
                  <div className={styles.matchedKeywords}>
                    <Text type="secondary" className={styles.matchLabel}>
                      匹配：
                    </Text>
                    {flow.matchedKeywords.slice(0, 3).map((keyword) => (
                      <Tag key={keyword} color="purple" className={styles.keywordTag}>
                        {keyword}
                      </Tag>
                    ))}
                  </div>
                )}

                <div className={styles.flowActions}>
                  <Button
                    type="link"
                    size="small"
                    icon={<EyeOutlined />}
                    onClick={() => onSelectFlow?.(flow.flowId)}
                  >
                    查看
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={() => onUseAsTemplate?.(flow.flowId)}
                  >
                    參考此流程
                  </Button>
                </div>
              </Card>
            </List.Item>
          )
        }}
      />
    </div>
  )
}

export default SimilarFlowsPanel
