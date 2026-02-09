import { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Row,
  Col,
  Input,
  Select,
  Tag,
  Button,
  Space,
  Tabs,
  Empty,
  Spin,
  Modal,
  Form,
  message,
  Typography,
  Segmented,
  Pagination,
  Alert,
  Tooltip,
} from 'antd'
import {
  SearchOutlined,
  BookOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  UserOutlined,
  FireOutlined,
  TagOutlined,
  CrownOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { templateApi } from '../api/template'
import type { Template, CreateTemplateRequest } from '../api/template'
import { extractApiError } from '../utils/errorMessages'
import { formatDateTime } from '../utils/locale'
import logger from '../utils/logger'

const { Search } = Input
const { Text, Paragraph, Title } = Typography

// Category color mapping
const categoryColors: Record<string, string> = {
  automation: 'blue',
  data: 'purple',
  integration: 'green',
  notification: 'orange',
  monitoring: 'cyan',
  ai: 'magenta',
  utility: 'default',
}

// Template Card Component
function TemplateCard({
  template,
  onUse,
  onDelete,
  showDelete,
  actionLoading,
}: {
  template: Template
  onUse: (template: Template) => void
  onDelete: (id: string) => void
  showDelete: boolean
  actionLoading: string | null
}) {
  const { t } = useTranslation()
  const isLoading = actionLoading === template.id

  return (
    <Card
      hoverable
      style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
      cover={
        <div
          style={{
            height: 80,
            background: template.isOfficial
              ? 'linear-gradient(135deg, var(--color-primary) 0%, #0D9488 100%)'
              : 'linear-gradient(135deg, #334155 0%, #1E293B 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
          }}
        >
          <BookOutlined style={{ fontSize: 32, color: 'rgba(255,255,255,0.6)' }} />
          {template.isOfficial && (
            <Tooltip title={t('template.official')}>
              <CrownOutlined
                style={{
                  position: 'absolute',
                  top: 8,
                  right: 8,
                  fontSize: 16,
                  color: 'var(--color-warning)',
                }}
              />
            </Tooltip>
          )}
        </div>
      }
      actions={[
        <Button
          key="use"
          type="text"
          icon={<PlayCircleOutlined />}
          loading={isLoading}
          onClick={() => onUse(template)}
        >
          {t('template.useTemplate')}
        </Button>,
        ...(showDelete
          ? [
              <Button
                key="delete"
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => onDelete(template.id)}
              />,
            ]
          : []),
      ]}
    >
      <Card.Meta
        title={
          <Tooltip title={template.name}>
            <span>{template.name}</span>
          </Tooltip>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Paragraph
              ellipsis={{ rows: 2 }}
              style={{ marginBottom: 8, minHeight: 44, color: 'var(--color-text-secondary)' }}
            >
              {template.description || '-'}
            </Paragraph>
            <Space wrap size={[4, 4]}>
              {template.category && (
                <Tag color={categoryColors[template.category] || 'default'}>
                  {template.category}
                </Tag>
              )}
              {template.tags?.slice(0, 2).map((tag) => (
                <Tag key={tag} icon={<TagOutlined />}>
                  {tag}
                </Tag>
              ))}
            </Space>
            <Space size="middle" style={{ marginTop: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                <FireOutlined style={{ marginRight: 4 }} />
                {t('template.usage', { count: template.usageCount })}
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatDateTime(template.createdAt)}
              </Text>
            </Space>
          </Space>
        }
      />
    </Card>
  )
}

// Main Template Page
export default function TemplatePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  // State
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [templates, setTemplates] = useState<Template[]>([])
  const [myTemplates, setMyTemplates] = useState<Template[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [selectedCategory, setSelectedCategory] = useState<string>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState('browse')
  const [error, setError] = useState<string | null>(null)

  // Pagination state
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize] = useState(12)

  // Use template modal
  const [useModalOpen, setUseModalOpen] = useState(false)
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null)
  const [useForm] = Form.useForm()

  // Create template modal
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [createSubmitting, setCreateSubmitting] = useState(false)
  const [createForm] = Form.useForm()

  // Load templates
  const loadTemplates = useCallback(
    async (page = 0, category?: string, search?: string) => {
      setLoading(true)
      setError(null)
      try {
        const categoryParam = category && category !== 'all' ? category : undefined
        const data = await templateApi.list(page, pageSize, categoryParam, search || undefined)
        setTemplates(data.content)
        setTotalElements(data.totalElements)
        setCurrentPage(data.number)
      } catch (err) {
        logger.error('Failed to load templates:', err)
        setError(extractApiError(err, t('common.loadFailed')))
      } finally {
        setLoading(false)
      }
    },
    [pageSize, t]
  )

  // Load categories
  const loadCategories = useCallback(async () => {
    try {
      const data = await templateApi.getCategories()
      setCategories(data)
    } catch (err) {
      logger.error('Failed to load categories:', err)
    }
  }, [])

  // Load my templates
  const loadMyTemplates = useCallback(async () => {
    try {
      const data = await templateApi.getMine()
      setMyTemplates(data)
    } catch (err) {
      logger.error('Failed to load my templates:', err)
    }
  }, [])

  useEffect(() => {
    loadTemplates()
    loadCategories()
  }, [loadTemplates, loadCategories])

  useEffect(() => {
    if (activeTab === 'mine') {
      loadMyTemplates()
    }
  }, [activeTab, loadMyTemplates])

  // Search handler
  const handleSearch = (value: string) => {
    setSearchQuery(value)
    loadTemplates(0, selectedCategory, value)
  }

  // Category filter handler
  const handleCategoryChange = (value: string | number) => {
    const cat = value as string
    setSelectedCategory(cat)
    loadTemplates(0, cat, searchQuery)
  }

  // Page change handler
  const handlePageChange = (page: number) => {
    loadTemplates(page - 1, selectedCategory, searchQuery)
  }

  // Open use template modal
  const handleOpenUseModal = (template: Template) => {
    setSelectedTemplate(template)
    useForm.setFieldsValue({ flowName: template.name })
    setUseModalOpen(true)
  }

  // Use template to create flow
  const handleUseTemplate = async (values: { flowName: string }) => {
    if (!selectedTemplate) return

    setActionLoading(selectedTemplate.id)
    try {
      const flow = await templateApi.useTemplate(selectedTemplate.id, values.flowName)
      message.success(t('template.useSuccess'))
      setUseModalOpen(false)
      useForm.resetFields()
      setSelectedTemplate(null)
      navigate(`/flows/${flow.id}/edit`)
    } catch (err) {
      message.error(extractApiError(err, t('common.createFailed')))
    } finally {
      setActionLoading(null)
    }
  }

  // Delete template
  const handleDelete = (id: string) => {
    Modal.confirm({
      title: t('template.deleteConfirm'),
      okType: 'danger',
      okText: t('common.delete'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await templateApi.delete(id)
          message.success(t('template.deleteSuccess'))
          loadTemplates(currentPage, selectedCategory, searchQuery)
          if (activeTab === 'mine') {
            loadMyTemplates()
          }
        } catch (err) {
          message.error(extractApiError(err, t('common.deleteFailed')))
        }
      },
    })
  }

  // Create template handler
  const handleCreateTemplate = async (values: { name: string; description?: string; category?: string; tags?: string[] }) => {
    setCreateSubmitting(true)
    try {
      const request: CreateTemplateRequest = {
        name: values.name,
        description: values.description,
        category: values.category,
        tags: values.tags,
        definition: { nodes: [], edges: [] },
      }
      await templateApi.create(request)
      message.success(t('template.createSuccess'))
      setCreateModalOpen(false)
      createForm.resetFields()
      loadTemplates(0, selectedCategory, searchQuery)
      if (activeTab === 'mine') {
        loadMyTemplates()
      }
    } catch (err) {
      message.error(extractApiError(err, t('template.createFailed')))
    } finally {
      setCreateSubmitting(false)
    }
  }

  // Render template grid
  const renderTemplateGrid = (items: Template[], showDelete: boolean) => {
    if (loading && items.length === 0) {
      return (
        <div style={{ textAlign: 'center', padding: 80 }}>
          <Spin size="large" />
        </div>
      )
    }

    if (items.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <Space direction="vertical" size={4}>
              <Text>{t('template.noTemplates')}</Text>
              <Text type="secondary">{t('template.noTemplatesDesc')}</Text>
            </Space>
          }
        />
      )
    }

    return (
      <Row gutter={[16, 16]}>
        {items.map((template) => (
          <Col xs={24} sm={12} md={8} lg={6} key={template.id}>
            <TemplateCard
              template={template}
              onUse={handleOpenUseModal}
              onDelete={handleDelete}
              showDelete={showDelete}
              actionLoading={actionLoading}
            />
          </Col>
        ))}
      </Row>
    )
  }

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Space align="center">
            <BookOutlined style={{ fontSize: 24, color: 'var(--color-primary)' }} />
            <Title level={3} style={{ margin: 0 }}>
              {t('template.title')}
            </Title>
          </Space>
          <div style={{ marginTop: 4 }}>
            <Text type="secondary">{t('template.description')}</Text>
          </div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          {t('template.create')}
        </Button>
      </div>

      {error && (
        <Alert
          message={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={() => loadTemplates(currentPage, selectedCategory, searchQuery)}>
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
                <BookOutlined />
                {t('template.allCategories')}
              </span>
            ),
            children: (
              <>
                {/* Search and Filters */}
                <Card style={{ marginBottom: 16 }}>
                  <Row gutter={16} align="middle">
                    <Col flex="300px">
                      <Search
                        placeholder={t('template.search')}
                        allowClear
                        enterButton={<SearchOutlined />}
                        onSearch={handleSearch}
                        onChange={(e) => !e.target.value && handleSearch('')}
                      />
                    </Col>
                    <Col flex="auto">
                      <Space wrap>
                        <Segmented
                          options={[
                            { label: t('template.allCategories'), value: 'all' },
                            ...categories.map((cat) => ({ label: cat, value: cat })),
                          ]}
                          value={selectedCategory}
                          onChange={handleCategoryChange}
                        />
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {/* Template Grid */}
                {renderTemplateGrid(templates, false)}

                {/* Pagination */}
                {totalElements > pageSize && (
                  <div style={{ textAlign: 'center', marginTop: 24 }}>
                    <Pagination
                      current={currentPage + 1}
                      pageSize={pageSize}
                      total={totalElements}
                      onChange={handlePageChange}
                      showTotal={(total) => t('common.total', { count: total })}
                    />
                  </div>
                )}
              </>
            ),
          },
          {
            key: 'mine',
            label: (
              <span>
                <UserOutlined />
                {t('template.myTemplates')}
              </span>
            ),
            children: renderTemplateGrid(myTemplates, true),
          },
        ]}
      />

      {/* Create Template Modal */}
      <Modal
        title={t('template.create')}
        open={createModalOpen}
        onCancel={() => { setCreateModalOpen(false); createForm.resetFields(); }}
        onOk={() => createForm.submit()}
        confirmLoading={createSubmitting}
        width={500}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreateTemplate} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('template.templateName')}
            rules={[
              { required: true, message: t('template.templateNameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('template.templateNamePlaceholder')} maxLength={255} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={3} placeholder={t('template.templateDescPlaceholder')} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="category" label={t('template.category')}>
            <Select placeholder={t('template.category')}>
              {categories.map((cat) => (
                <Select.Option key={cat} value={cat}>{cat}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="tags" label={t('template.tags')}>
            <Select mode="tags" placeholder={t('template.tagsPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Use Template Modal */}
      <Modal
        title={
          <Space>
            <PlayCircleOutlined />
            {t('template.useTemplate')}
            {selectedTemplate && `: ${selectedTemplate.name}`}
          </Space>
        }
        open={useModalOpen}
        onCancel={() => {
          setUseModalOpen(false)
          useForm.resetFields()
          setSelectedTemplate(null)
        }}
        footer={null}
      >
        <Form form={useForm} layout="vertical" onFinish={handleUseTemplate}>
          <Form.Item
            name="flowName"
            label={t('template.flowName')}
            rules={[{ required: true, message: t('template.flowNamePlaceholder') }]}
          >
            <Input placeholder={t('template.flowNamePlaceholder')} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setUseModalOpen(false)
                  useForm.resetFields()
                  setSelectedTemplate(null)
                }}
              >
                {t('common.cancel')}
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={actionLoading === selectedTemplate?.id}
                icon={<PlayCircleOutlined />}
              >
                {t('template.useTemplate')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
