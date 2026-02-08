import { useState, useEffect, useCallback } from 'react'
import {
  Alert,
  Card,
  Row,
  Col,
  Input,
  Select,
  Tag,
  Button,
  Avatar,
  Space,
  Tabs,
  Empty,
  Spin,
  Badge,
  Modal,
  message,
  Typography,
  Segmented,
} from 'antd'
import {
  SearchOutlined,
  CloudDownloadOutlined,
  AppstoreOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  DeleteOutlined,
  EyeOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type {
  MarketplacePlugin,
  MarketplaceCategory,
  PluginDetail,
} from '../api/marketplace'
import {
  searchPlugins,
  getCategories,
  getInstalledPlugins,
  getPluginDetail,
  installPlugin,
  uninstallPlugin,
  updatePlugin,
} from '../api/marketplace'
import logger from '../utils/logger'

const { Search } = Input
const { Text, Paragraph, Title } = Typography

// Tool Card Component
function ToolCard({
  plugin,
  onInstall,
  onUninstall,
  onUpdate,
  onViewDetails,
  loading,
}: {
  plugin: MarketplacePlugin
  onInstall: (id: string) => void
  onUninstall: (id: string) => void
  onUpdate: (id: string) => void
  onViewDetails: (id: string) => void
  loading: string | null
}) {
  const { t } = useTranslation()
  const isLoading = loading === plugin.id

  const hasUpdate = plugin.isInstalled && plugin.installedVersion !== plugin.version

  return (
    <Card
      hoverable
      style={{ height: '100%' }}
      cover={
        <div
          style={{
            height: 100,
            background: 'linear-gradient(135deg, var(--color-primary) 0%, #0D9488 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
          }}
        >
          {plugin.icon ? (
            <Avatar src={plugin.icon} size={56} shape="square" />
          ) : (
            <Avatar size={56} shape="square" style={{ background: 'rgba(255,255,255,0.2)' }}>
              <ToolOutlined style={{ fontSize: 28 }} />
            </Avatar>
          )}
          {plugin.isInstalled && (
            <Badge
              status={hasUpdate ? 'warning' : 'success'}
              style={{ position: 'absolute', top: 8, right: 8 }}
            />
          )}
        </div>
      }
      actions={[
        <Button
          key="view"
          type="text"
          icon={<EyeOutlined />}
          onClick={() => onViewDetails(plugin.id)}
        >
          {t('common.details')}
        </Button>,
        plugin.isInstalled ? (
          hasUpdate ? (
            <Button
              key="update"
              type="text"
              icon={<SyncOutlined />}
              loading={isLoading}
              onClick={() => onUpdate(plugin.id)}
            >
              {t('customTools.update')}
            </Button>
          ) : (
            <Button
              key="uninstall"
              type="text"
              danger
              icon={<DeleteOutlined />}
              loading={isLoading}
              onClick={() => onUninstall(plugin.id)}
            >
              {t('customTools.remove')}
            </Button>
          )
        ) : (
          <Button
            key="install"
            type="text"
            icon={<CloudDownloadOutlined />}
            loading={isLoading}
            onClick={() => onInstall(plugin.id)}
          >
            {t('customTools.pull')}
          </Button>
        ),
      ]}
    >
      <Card.Meta
        title={
          <Space>
            <span>{plugin.displayName}</span>
            {plugin.isInstalled && <CheckCircleOutlined style={{ color: 'var(--color-success)', fontSize: 14 }} />}
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 8, minHeight: 44 }}>
              {plugin.description}
            </Paragraph>
            <Space wrap size={[4, 4]}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {plugin.author}
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                v{plugin.version}
              </Text>
              <Tag color="green">{t('customTools.free')}</Tag>
            </Space>
          </Space>
        }
      />
    </Card>
  )
}

// Tool Detail Modal
function ToolDetailModal({
  visible,
  plugin,
  onClose,
  onInstall,
  onUninstall,
  onUpdate,
  loading,
}: {
  visible: boolean
  plugin: PluginDetail | null
  onClose: () => void
  onInstall: (id: string) => void
  onUninstall: (id: string) => void
  onUpdate: (id: string) => void
  loading: boolean
}) {
  const { t } = useTranslation()

  if (!plugin) return null

  const hasUpdate = plugin.isInstalled && plugin.installedVersion !== plugin.version

  return (
    <Modal
      open={visible}
      onCancel={onClose}
      width={800}
      footer={null}
      title={
        <Space>
          {plugin.icon ? (
            <Avatar src={plugin.icon} size={40} shape="square" />
          ) : (
            <Avatar size={40} shape="square">
              <ToolOutlined />
            </Avatar>
          )}
          <div>
            <div>{plugin.displayName}</div>
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              {plugin.author} • v{plugin.version}
            </Text>
          </div>
        </Space>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        {plugin.tags.map(tag => (
          <Tag key={tag}>{tag}</Tag>
        ))}
      </Space>

      <div style={{ marginBottom: 16 }}>
        {plugin.isInstalled ? (
          hasUpdate ? (
            <Button
              type="primary"
              icon={<SyncOutlined />}
              loading={loading}
              onClick={() => onUpdate(plugin.id)}
            >
              {t('customTools.updateTo')} v{plugin.version}
            </Button>
          ) : (
            <Space>
              <Button type="primary" disabled icon={<CheckCircleOutlined />}>
                {t('customTools.installed')} (v{plugin.installedVersion})
              </Button>
              <Button
                danger
                icon={<DeleteOutlined />}
                loading={loading}
                onClick={() => onUninstall(plugin.id)}
              >
                {t('customTools.remove')}
              </Button>
            </Space>
          )
        ) : (
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            loading={loading}
            onClick={() => onInstall(plugin.id)}
          >
            {t('customTools.pull')}
          </Button>
        )}
      </div>

      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            key: 'overview',
            label: t('customTools.overview'),
            children: (
              <>
                <Paragraph>{plugin.description}</Paragraph>
                {plugin.readme && (
                  <div
                    style={{
                      background: 'var(--color-bg-elevated)',
                      padding: 16,
                      borderRadius: 8,
                      maxHeight: 400,
                      overflow: 'auto',
                    }}
                  >
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{plugin.readme}</pre>
                  </div>
                )}
              </>
            ),
          },
          {
            key: 'changelog',
            label: t('customTools.changelog'),
            children: plugin.changelog ? (
              <div
                style={{
                  background: 'var(--color-bg-elevated)',
                  padding: 16,
                  borderRadius: 8,
                  maxHeight: 400,
                  overflow: 'auto',
                }}
              >
                <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{plugin.changelog}</pre>
              </div>
            ) : (
              <Empty description={t('customTools.noChangelog')} />
            ),
          },
          {
            key: 'capabilities',
            label: t('customTools.capabilities'),
            children: (
              <Space wrap>
                {plugin.capabilities.map(cap => (
                  <Tag key={cap} color="blue">
                    {cap}
                  </Tag>
                ))}
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  )
}

// Main Custom Tools Page
export default function MarketplacePage() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [plugins, setPlugins] = useState<MarketplacePlugin[]>([])
  const [installedPlugins, setInstalledPlugins] = useState<MarketplacePlugin[]>([])
  const [categories, setCategories] = useState<MarketplaceCategory[]>([])
  const [selectedCategory, setSelectedCategory] = useState<string>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [sortBy, setSortBy] = useState<'popular' | 'recent' | 'rating' | 'name'>('recent')
  const [activeTab, setActiveTab] = useState('browse')
  const [isOffline, setIsOffline] = useState(false)
  const [detailModal, setDetailModal] = useState<{ visible: boolean; plugin: PluginDetail | null }>({
    visible: false,
    plugin: null,
  })

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [categoriesData, installedData] = await Promise.all([
        getCategories(),
        getInstalledPlugins(),
      ])
      setCategories(categoriesData)
      setInstalledPlugins(installedData)

      // Load initial browse results
      const searchResult = await searchPlugins({ sortBy: 'recent', pageSize: 20 })
      setPlugins(searchResult.plugins)
    } catch (error) {
      logger.error('Failed to load custom tools data:', error)
      setIsOffline(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleSearch = async (value: string) => {
    setSearchQuery(value)
    try {
      const result = await searchPlugins({
        query: value,
        category: selectedCategory !== 'all' ? selectedCategory : undefined,
        sortBy,
      })
      setPlugins(result.plugins)
    } catch {
      // Keep current data on error
    }
  }

  const handleInstall = async (id: string) => {
    setActionLoading(id)
    try {
      await installPlugin(id)
      message.success(t('customTools.pullSuccess'))
      loadData()
    } catch {
      message.error(t('customTools.pullFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleUninstall = async (id: string) => {
    Modal.confirm({
      title: t('customTools.confirmRemove'),
      content: t('customTools.removeWarning'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoading(id)
        try {
          await uninstallPlugin(id)
          message.success(t('customTools.removeSuccess'))
          loadData()
        } catch {
          message.error(t('customTools.removeFailed'))
        } finally {
          setActionLoading(null)
        }
      },
    })
  }

  const handleUpdate = async (id: string) => {
    setActionLoading(id)
    try {
      await updatePlugin(id)
      message.success(t('customTools.updateSuccess'))
      loadData()
    } catch {
      message.error(t('customTools.updateFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleViewDetails = async (id: string) => {
    try {
      const detail = await getPluginDetail(id)
      setDetailModal({ visible: true, plugin: detail })
    } catch {
      message.error(t('customTools.loadDetailFailed'))
    }
  }

  const filteredPlugins = plugins.filter(plugin => {
    if (selectedCategory !== 'all' && plugin.category !== selectedCategory) return false
    if (searchQuery && !plugin.displayName.toLowerCase().includes(searchQuery.toLowerCase())) return false
    return true
  })

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Title level={3}>{t('customTools.title')}</Title>
        <Text type="secondary">{t('customTools.subtitle')}</Text>
      </div>

      {isOffline && (
        <Alert
          message={t('customTools.offlineMode')}
          description={t('customTools.offlineDesc')}
          type="warning"
          showIcon
          closable
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={loadData}>
              {t('common.retry')}
            </Button>
          }
        />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'browse',
            label: (
              <span>
                <AppstoreOutlined />
                {t('customTools.browse')}
              </span>
            ),
            children: (
              <>
                {/* Filters */}
                <Card style={{ marginBottom: 16 }}>
                  <Row gutter={16} align="middle">
                    <Col flex="300px">
                      <Search
                        placeholder={t('customTools.searchPlaceholder')}
                        allowClear
                        enterButton={<SearchOutlined />}
                        onSearch={handleSearch}
                        onChange={e => !e.target.value && handleSearch('')}
                      />
                    </Col>
                    <Col flex="auto">
                      <Space wrap>
                        <Segmented
                          options={[
                            { label: t('customTools.all'), value: 'all' },
                            ...categories.map(c => ({ label: `${c.icon} ${c.displayName}`, value: c.id })),
                          ]}
                          value={selectedCategory}
                          onChange={value => setSelectedCategory(value as string)}
                        />
                      </Space>
                    </Col>
                    <Col>
                      <Select
                        value={sortBy}
                        onChange={setSortBy}
                        style={{ width: 140 }}
                        options={[
                          { label: t('customTools.sortRecent'), value: 'recent' },
                          { label: t('customTools.sortPopular'), value: 'popular' },
                          { label: t('customTools.sortName'), value: 'name' },
                        ]}
                      />
                    </Col>
                  </Row>
                </Card>

                {/* Tool Grid */}
                {filteredPlugins.length > 0 ? (
                  <Row gutter={[16, 16]}>
                    {filteredPlugins.map(plugin => (
                      <Col xs={24} sm={12} md={8} lg={6} key={plugin.id}>
                        <ToolCard
                          plugin={plugin}
                          onInstall={handleInstall}
                          onUninstall={handleUninstall}
                          onUpdate={handleUpdate}
                          onViewDetails={handleViewDetails}
                          loading={actionLoading}
                        />
                      </Col>
                    ))}
                  </Row>
                ) : (
                  <Empty description={t('customTools.noTools')} />
                )}
              </>
            ),
          },
          {
            key: 'installed',
            label: (
              <span>
                <CheckCircleOutlined />
                {t('customTools.installed')} ({installedPlugins.length})
              </span>
            ),
            children: installedPlugins.length > 0 ? (
              <Row gutter={[16, 16]}>
                {installedPlugins.map(plugin => (
                  <Col xs={24} sm={12} md={8} lg={6} key={plugin.id}>
                    <ToolCard
                      plugin={plugin}
                      onInstall={handleInstall}
                      onUninstall={handleUninstall}
                      onUpdate={handleUpdate}
                      onViewDetails={handleViewDetails}
                      loading={actionLoading}
                    />
                  </Col>
                ))}
              </Row>
            ) : (
              <Empty description={t('customTools.noInstalledTools')} />
            ),
          },
        ]}
      />

      <ToolDetailModal
        visible={detailModal.visible}
        plugin={detailModal.plugin}
        onClose={() => setDetailModal({ visible: false, plugin: null })}
        onInstall={handleInstall}
        onUninstall={handleUninstall}
        onUpdate={handleUpdate}
        loading={actionLoading !== null}
      />
    </div>
  )
}
