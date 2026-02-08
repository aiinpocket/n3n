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
  Rate,
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
  DownloadOutlined,
  StarFilled,
  AppstoreOutlined,
  CloudDownloadOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  DeleteOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import {
  MarketplacePlugin,
  MarketplaceCategory,
  PluginDetail,
  searchPlugins,
  getCategories,
  getFeaturedPlugins,
  getInstalledPlugins,
  getPluginDetail,
  installPlugin,
  uninstallPlugin,
  updatePlugin,
} from '../api/marketplace'
import logger from '../utils/logger'

const { Search } = Input
const { Text, Paragraph, Title } = Typography

// Plugin Card Component
function PluginCard({
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

  const getPricingTag = () => {
    switch (plugin.pricing) {
      case 'free':
        return <Tag color="green">{t('marketplace.free')}</Tag>
      case 'paid':
        return <Tag color="gold">${plugin.price}</Tag>
      case 'freemium':
        return <Tag color="blue">{t('marketplace.freemium')}</Tag>
      default:
        return null
    }
  }

  const hasUpdate = plugin.isInstalled && plugin.installedVersion !== plugin.version

  return (
    <Card
      hoverable
      style={{ height: '100%' }}
      cover={
        <div
          style={{
            height: 120,
            background: 'linear-gradient(135deg, var(--color-ai) 0%, #5B21B6 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
          }}
        >
          {plugin.icon ? (
            <Avatar src={plugin.icon} size={64} shape="square" />
          ) : (
            <Avatar size={64} shape="square" style={{ background: 'rgba(255,255,255,0.2)' }}>
              <AppstoreOutlined style={{ fontSize: 32 }} />
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
              {t('marketplace.update')}
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
              {t('marketplace.uninstall')}
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
            {t('marketplace.install')}
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
              {getPricingTag()}
            </Space>
            <Space size={8}>
              <Space size={4}>
                <StarFilled style={{ color: 'var(--color-warning)', fontSize: 12 }} />
                <Text style={{ fontSize: 12 }}>{plugin.rating.toFixed(1)}</Text>
              </Space>
              <Space size={4}>
                <DownloadOutlined style={{ fontSize: 12 }} />
                <Text style={{ fontSize: 12 }}>{plugin.downloads.toLocaleString()}</Text>
              </Space>
            </Space>
          </Space>
        }
      />
    </Card>
  )
}

// Plugin Detail Modal
function PluginDetailModal({
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
              <AppstoreOutlined />
            </Avatar>
          )}
          <div>
            <div>{plugin.displayName}</div>
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              by {plugin.author} • v{plugin.version}
            </Text>
          </div>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Space size={16}>
          <Space>
            <Rate disabled defaultValue={plugin.rating} allowHalf />
            <Text>({plugin.ratingCount})</Text>
          </Space>
          <Text type="secondary">
            <DownloadOutlined /> {plugin.downloads.toLocaleString()} {t('marketplace.downloads')}
          </Text>
          {plugin.pricing === 'free' ? (
            <Tag color="green">{t('marketplace.free')}</Tag>
          ) : plugin.pricing === 'paid' ? (
            <Tag color="gold">${plugin.price}</Tag>
          ) : (
            <Tag color="blue">{t('marketplace.freemium')}</Tag>
          )}
        </Space>
      </div>

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
              {t('marketplace.updateTo')} v{plugin.version}
            </Button>
          ) : (
            <Space>
              <Button type="primary" disabled icon={<CheckCircleOutlined />}>
                {t('marketplace.installed')} (v{plugin.installedVersion})
              </Button>
              <Button
                danger
                icon={<DeleteOutlined />}
                loading={loading}
                onClick={() => onUninstall(plugin.id)}
              >
                {t('marketplace.uninstall')}
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
            {t('marketplace.install')}
          </Button>
        )}
      </div>

      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            key: 'overview',
            label: t('marketplace.overview'),
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
            label: t('marketplace.changelog'),
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
              <Empty description={t('marketplace.noChangelog')} />
            ),
          },
          {
            key: 'capabilities',
            label: t('marketplace.capabilities'),
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

// Main Marketplace Page
export default function MarketplacePage() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [plugins, setPlugins] = useState<MarketplacePlugin[]>([])
  const [featuredPlugins, setFeaturedPlugins] = useState<MarketplacePlugin[]>([])
  const [installedPlugins, setInstalledPlugins] = useState<MarketplacePlugin[]>([])
  const [categories, setCategories] = useState<MarketplaceCategory[]>([])
  const [selectedCategory, setSelectedCategory] = useState<string>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [pricingFilter, setPricingFilter] = useState<'all' | 'free' | 'paid' | 'freemium'>('all')
  const [sortBy, setSortBy] = useState<'popular' | 'recent' | 'rating' | 'name'>('popular')
  const [activeTab, setActiveTab] = useState('browse')
  const [isOffline, setIsOffline] = useState(false)
  const [detailModal, setDetailModal] = useState<{ visible: boolean; plugin: PluginDetail | null }>({
    visible: false,
    plugin: null,
  })

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [categoriesData, featuredData, installedData] = await Promise.all([
        getCategories(),
        getFeaturedPlugins(),
        getInstalledPlugins(),
      ])
      setCategories(categoriesData)
      setFeaturedPlugins(featuredData)
      setInstalledPlugins(installedData)

      // Load initial browse results
      const searchResult = await searchPlugins({ sortBy: 'popular', pageSize: 20 })
      setPlugins(searchResult.plugins)
    } catch (error) {
      logger.error('Failed to load marketplace data:', error)
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
        pricing: pricingFilter !== 'all' ? pricingFilter : undefined,
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
      message.success(t('marketplace.installSuccess'))
      loadData()
    } catch {
      message.error(t('marketplace.installFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleUninstall = async (id: string) => {
    Modal.confirm({
      title: t('marketplace.confirmUninstall'),
      content: t('marketplace.uninstallWarning'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoading(id)
        try {
          await uninstallPlugin(id)
          message.success(t('marketplace.uninstallSuccess'))
          loadData()
        } catch {
          message.error(t('marketplace.uninstallFailed'))
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
      message.success(t('marketplace.updateSuccess'))
      loadData()
    } catch {
      message.error(t('marketplace.updateFailed'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleViewDetails = async (id: string) => {
    try {
      const detail = await getPluginDetail(id)
      setDetailModal({ visible: true, plugin: detail })
    } catch {
      message.error(t('marketplace.loadDetailFailed'))
    }
  }

  const filteredPlugins = plugins.filter(plugin => {
    if (selectedCategory !== 'all' && plugin.category !== selectedCategory) return false
    if (pricingFilter !== 'all' && plugin.pricing !== pricingFilter) return false
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
      <Title level={3}>{t('marketplace.title')}</Title>

      {isOffline && (
        <Alert
          message={t('marketplace.offlineMode')}
          description={t('marketplace.offlineDesc')}
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
                {t('marketplace.browse')}
              </span>
            ),
            children: (
              <>
                {/* Featured Section */}
                {featuredPlugins.length > 0 && (
                  <div style={{ marginBottom: 24 }}>
                    <Title level={5}>{t('marketplace.featured')}</Title>
                    <Row gutter={[16, 16]}>
                      {featuredPlugins.map(plugin => (
                        <Col xs={24} sm={12} md={8} lg={8} key={plugin.id}>
                          <PluginCard
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
                  </div>
                )}

                {/* Filters */}
                <Card style={{ marginBottom: 16 }}>
                  <Row gutter={16} align="middle">
                    <Col flex="300px">
                      <Search
                        placeholder={t('marketplace.searchPlaceholder')}
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
                            { label: t('marketplace.all'), value: 'all' },
                            ...categories.map(c => ({ label: `${c.icon} ${c.displayName}`, value: c.id })),
                          ]}
                          value={selectedCategory}
                          onChange={value => setSelectedCategory(value as string)}
                        />
                      </Space>
                    </Col>
                    <Col>
                      <Space>
                        <Select
                          value={pricingFilter}
                          onChange={setPricingFilter}
                          style={{ width: 120 }}
                          options={[
                            { label: t('marketplace.allPricing'), value: 'all' },
                            { label: t('marketplace.free'), value: 'free' },
                            { label: t('marketplace.paid'), value: 'paid' },
                            { label: t('marketplace.freemium'), value: 'freemium' },
                          ]}
                        />
                        <Select
                          value={sortBy}
                          onChange={setSortBy}
                          style={{ width: 120 }}
                          options={[
                            { label: t('marketplace.sortPopular'), value: 'popular' },
                            { label: t('marketplace.sortRecent'), value: 'recent' },
                            { label: t('marketplace.sortRating'), value: 'rating' },
                            { label: t('marketplace.sortName'), value: 'name' },
                          ]}
                        />
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {/* Plugin Grid */}
                {filteredPlugins.length > 0 ? (
                  <Row gutter={[16, 16]}>
                    {filteredPlugins.map(plugin => (
                      <Col xs={24} sm={12} md={8} lg={6} key={plugin.id}>
                        <PluginCard
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
                  <Empty description={t('marketplace.noPlugins')} />
                )}
              </>
            ),
          },
          {
            key: 'installed',
            label: (
              <span>
                <CheckCircleOutlined />
                {t('marketplace.installed')} ({installedPlugins.length})
              </span>
            ),
            children: installedPlugins.length > 0 ? (
              <Row gutter={[16, 16]}>
                {installedPlugins.map(plugin => (
                  <Col xs={24} sm={12} md={8} lg={6} key={plugin.id}>
                    <PluginCard
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
              <Empty description={t('marketplace.noInstalledPlugins')} />
            ),
          },
        ]}
      />

      <PluginDetailModal
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
