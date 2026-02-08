import React, { useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Drawer,
  Input,
  Tabs,
  Card,
  Button,
  Tag,
  Space,
  Spin,
  Empty,
  Alert,
  Typography,
  List,
  Tooltip,
} from 'antd'
import {
  SearchOutlined,
  PlusOutlined,
  RobotOutlined,
  AppstoreOutlined,
  CheckCircleOutlined,
  InfoCircleOutlined,
  StarFilled,
  DownloadOutlined,
} from '@ant-design/icons'
import {
  aiAssistantApi,
  NodeCategoryInfo,
  InstalledNodeInfo,
  NodeRecommendation,
  getCategoryIcon,
} from '../../api/aiAssistant'
import logger from '../../utils/logger'

const { Text, Paragraph } = Typography

interface Props {
  open: boolean
  onClose: () => void
  currentFlow?: { nodes: unknown[]; edges: unknown[] }
  onAddNode?: (nodeType: string) => void
}

export const NodeRecommendationDrawer: React.FC<Props> = ({
  open,
  onClose,
  currentFlow,
  onAddNode,
}) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [categories, setCategories] = useState<NodeCategoryInfo[]>([])
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)
  const [installedNodes, setInstalledNodes] = useState<InstalledNodeInfo[]>([])
  const [aiRecommendations, setAiRecommendations] = useState<NodeRecommendation[]>([])
  const [aiAvailable, setAiAvailable] = useState(true)

  const loadCategories = useCallback(async () => {
    try {
      const cats = await aiAssistantApi.getNodeCategories()
      setCategories(cats)
    } catch (error) {
      logger.error('Failed to load categories:', error)
    }
  }, [])

  const loadInstalledNodes = useCallback(async (category?: string) => {
    try {
      const nodes = await aiAssistantApi.getInstalledNodes(category || undefined)
      setInstalledNodes(nodes)
    } catch (error) {
      logger.error('Failed to load installed nodes:', error)
    }
  }, [])

  const loadRecommendations = useCallback(async () => {
    if (!currentFlow) return

    setLoading(true)
    try {
      const response = await aiAssistantApi.recommendNodes({
        currentFlow,
        searchQuery: searchQuery || undefined,
        category: selectedCategory || undefined,
      })
      setAiAvailable(response.aiAvailable)
      setAiRecommendations(response.aiRecommendations || [])
    } catch (error) {
      logger.error('Failed to load recommendations:', error)
    } finally {
      setLoading(false)
    }
  }, [currentFlow, searchQuery, selectedCategory])

  useEffect(() => {
    if (open) {
      loadCategories()
      loadInstalledNodes()
      loadRecommendations()
    }
  }, [open, loadCategories, loadInstalledNodes, loadRecommendations])

  useEffect(() => {
    if (open && selectedCategory) {
      loadInstalledNodes(selectedCategory)
    }
  }, [selectedCategory, open, loadInstalledNodes])

  const handleSearch = () => {
    loadRecommendations()
  }

  const handleAddNode = (nodeType: string) => {
    onAddNode?.(nodeType)
  }

  const renderCategoryGrid = () => (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
      <Tag
        style={{ cursor: 'pointer', padding: '4px 12px' }}
        color={selectedCategory === null ? 'blue' : undefined}
        onClick={() => setSelectedCategory(null)}
      >
        {t('nodeRecommendation.all')}
      </Tag>
      {categories.map((cat) => (
        <Tag
          key={cat.id}
          style={{ cursor: 'pointer', padding: '4px 12px' }}
          color={selectedCategory === cat.id ? 'blue' : undefined}
          onClick={() => setSelectedCategory(cat.id)}
        >
          {getCategoryIcon(cat.icon)} {cat.displayName} ({cat.installedCount})
        </Tag>
      ))}
    </div>
  )

  const renderInstalledNode = (node: InstalledNodeInfo) => (
    <Card
      key={node.nodeType}
      size="small"
      style={{ marginBottom: 8 }}
      actions={[
        <Button
          key="add"
          type="link"
          icon={<PlusOutlined />}
          onClick={() => handleAddNode(node.nodeType)}
        >
          {t('nodeRecommendation.addToFlow')}
        </Button>,
      ]}
    >
      <Card.Meta
        title={
          <Space>
            <Text strong>{node.displayName}</Text>
            <Tag color="green" icon={<CheckCircleOutlined />}>
              {t('nodeRecommendation.installed')}
            </Tag>
          </Space>
        }
        description={
          <>
            <Text type="secondary">{node.description}</Text>
            <br />
            <Tag style={{ marginTop: 4 }}>{node.category}</Tag>
          </>
        }
      />
    </Card>
  )

  const formatDownloads = (count?: number) => {
    if (!count) return null
    if (count >= 1000000) return `${(count / 1000000).toFixed(1)}M`
    if (count >= 1000) return `${(count / 1000).toFixed(1)}K`
    return count.toString()
  }

  const renderRecommendation = (rec: NodeRecommendation) => (
    <Card
      key={rec.nodeType}
      size="small"
      style={{ marginBottom: 8 }}
      actions={[
        <Button
          key="add"
          type="link"
          icon={<PlusOutlined />}
          onClick={() => handleAddNode(rec.nodeType)}
        >
          {rec.needsInstall ? t('nodeRecommendation.install') : t('nodeRecommendation.addToFlow')}
        </Button>,
      ]}
    >
      <Card.Meta
        title={
          <Space>
            <Text strong>{rec.displayName}</Text>
            {rec.needsInstall ? (
              <Tag color="blue">{t('nodeRecommendation.needsInstall')}</Tag>
            ) : (
              <Tag color="green">{t('nodeRecommendation.available')}</Tag>
            )}
          </Space>
        }
        description={
          <>
            {/* Rating and Downloads */}
            {(rec.rating || rec.downloads) && (
              <Space size="small" style={{ marginBottom: 8 }}>
                {rec.rating && (
                  <Tag color="gold" style={{ margin: 0 }}>
                    <StarFilled style={{ marginRight: 2 }} />
                    {rec.rating.toFixed(1)}
                  </Tag>
                )}
                {rec.downloads && (
                  <Tag color="blue" style={{ margin: 0 }}>
                    <DownloadOutlined style={{ marginRight: 2 }} />
                    {formatDownloads(rec.downloads)}
                  </Tag>
                )}
                {rec.source && rec.source !== 'builtin' && (
                  <Tag style={{ margin: 0 }}>
                    {rec.source === 'marketplace' ? t('nodeRecommendation.dockerHub') : 'Docker'}
                  </Tag>
                )}
              </Space>
            )}

            <Paragraph
              type="secondary"
              ellipsis={{ rows: 2 }}
              style={{ marginBottom: 8 }}
            >
              <RobotOutlined style={{ marginRight: 4 }} />
              {rec.matchReason}
            </Paragraph>
            {rec.pros.length > 0 && (
              <div style={{ marginBottom: 4 }}>
                {rec.pros.map((pro, i) => (
                  <Tag key={i} color="green" style={{ marginBottom: 2 }}>
                    ✓ {pro}
                  </Tag>
                ))}
              </div>
            )}
            {rec.cons.length > 0 && (
              <div>
                {rec.cons.map((con, i) => (
                  <Tooltip key={i} title={con}>
                    <Tag color="orange" style={{ marginBottom: 2 }}>
                      <InfoCircleOutlined /> {t('nodeRecommendation.caution')}
                    </Tag>
                  </Tooltip>
                ))}
              </div>
            )}
          </>
        }
      />
    </Card>
  )

  return (
    <Drawer
      title={t('nodeRecommendation.title')}
      placement="right"
      width={480}
      open={open}
      onClose={onClose}
      extra={
        <Button type="link" onClick={loadRecommendations}>
          {t('common.refresh')}
        </Button>
      }
    >
      <Input
        placeholder={t('nodeRecommendation.searchPlaceholder')}
        prefix={<SearchOutlined />}
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onPressEnter={handleSearch}
        style={{ marginBottom: 16 }}
      />

      {renderCategoryGrid()}

      <Tabs
        defaultActiveKey="installed"
        items={[
          {
            key: 'installed',
            label: (
              <span>
                <AppstoreOutlined /> {t('nodeRecommendation.installed')} ({installedNodes.length})
              </span>
            ),
            children: (
              <List
                dataSource={installedNodes}
                renderItem={renderInstalledNode}
                locale={{ emptyText: <Empty description={t('nodeRecommendation.noInstalledNodes')} /> }}
              />
            ),
          },
          {
            key: 'ai',
            label: (
              <span>
                <RobotOutlined /> {t('nodeRecommendation.aiRecommend')}
              </span>
            ),
            children: loading ? (
              <div style={{ textAlign: 'center', padding: 40 }}>
                <Spin tip={t('nodeRecommendation.aiAnalyzing')} />
              </div>
            ) : !aiAvailable ? (
              <Alert
                type="info"
                message={t('nodeRecommendation.aiUnavailable')}
                description={t('nodeRecommendation.aiUnavailableDesc')}
                showIcon
              />
            ) : aiRecommendations.length === 0 ? (
              <Empty description={t('nodeRecommendation.noRecommendations')} />
            ) : (
              <List
                dataSource={aiRecommendations}
                renderItem={renderRecommendation}
              />
            ),
          },
        ]}
      />
    </Drawer>
  )
}

export default NodeRecommendationDrawer
