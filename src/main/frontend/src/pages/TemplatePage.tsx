import { useState, useEffect, useCallback } from 'react'
import { Card, Row, Col, Input, Select, Tag, Button, Space, Tabs, Empty, Spin, Modal, Form, Typography, Segmented, Pagination, Alert, Tooltip } from 'antd'
import { message, modal } from '../utils/feedback'
import {
  SearchOutlined,
  BookOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  UserOutlined,
  FireOutlined,
  TagOutlined,
  CrownOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { templateApi } from '../api/template'
import type { Template, CreateTemplateRequest, OfficialTemplate, OfficialTemplateCategory } from '../api/template'
import OfficialTemplateGrid from '../components/template/OfficialTemplateGrid'
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
  onEdit,
  onDelete,
  showDelete,
  actionLoading,
}: {
  template: Template
  onUse: (template: Template) => void
  onEdit: (template: Template) => void
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
              ? 'linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-active) 100%)'
              : 'linear-gradient(135deg, var(--color-bg-hover) 0%, var(--color-bg-elevated) 100%)',
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
              <Tooltip title={t('common.edit')} key="edit">
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => onEdit(template)}
                  aria-label={t('common.edit')}
                />
              </Tooltip>,
              <Button
                key="delete"
                type="text"
                danger
                icon={<DeleteOutlined />}
                onClick={() => onDelete(template.id)}
                aria-label={t('common.delete')}
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
          <Space orientation="vertical" size={4} style={{ width: '100%' }}>
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
  const [activeTab, setActiveTab] = useState('official')

  // 內建範本（隨程式碼發布，新站台一開始就有東西可用）
  const [officialTemplates, setOfficialTemplates] = useState<OfficialTemplate[]>([])
  const [officialCategories, setOfficialCategories] = useState<OfficialTemplateCategory[]>([])
  const [officialCategory, setOfficialCategory] = useState<string>('all')
  const [officialSearch, setOfficialSearch] = useState('')
  const [officialLoading, setOfficialLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [myLoading, setMyLoading] = useState(false)

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

  // Edit template modal
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<Template | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editForm] = Form.useForm()

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
      setMyLoading(true)
      const data = await templateApi.getMine()
      setMyTemplates(data)
    } catch (err) {
      logger.error('Failed to load my templates:', err)
    } finally {
      setMyLoading(false)
    }
  }, [])

  const loadOfficialTemplates = useCallback(async (category?: string, search?: string) => {
    setOfficialLoading(true)
    try {
      const categoryParam = category && category !== 'all' ? category : undefined
      const data = await templateApi.listOfficial(categoryParam, search || undefined)
      setOfficialTemplates(data)
    } catch (err) {
      logger.error('Failed to load official templates:', err)
      setError(extractApiError(err, t('common.loadFailed')))
    } finally {
      setOfficialLoading(false)
    }
  }, [t])

  const loadOfficialCategories = useCallback(async () => {
    try {
      const data = await templateApi.getOfficialCategories()
      setOfficialCategories(data)
    } catch (err) {
      logger.error('Failed to load official categories:', err)
    }
  }, [])

  // 使用內建範本：不再多問一次流程名稱，直接用範本名稱建好帶進編輯器
  const handleUseOfficialTemplate = async (template: OfficialTemplate) => {
    try {
      const flow = await templateApi.useOfficialTemplate(template.id, template.name)
      message.success(t('template.useSuccess'))
      navigate(`/flows/${flow.id}/edit`)
    } catch (err) {
      message.error(extractApiError(err, t('common.createFailed')))
    }
  }

  useEffect(() => {
    loadTemplates()
    loadCategories()
    loadOfficialTemplates()
    loadOfficialCategories()
  }, [loadTemplates, loadCategories, loadOfficialTemplates, loadOfficialCategories])

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
    modal.confirm({
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

  // Edit template handler
  const handleEditTemplate = (template: Template) => {
    setEditingTemplate(template)
    editForm.setFieldsValue({
      name: template.name,
      description: template.description || '',
      category: template.category || undefined,
      tags: template.tags || [],
    })
    setEditModalOpen(true)
  }

  const handleEditSubmit = async () => {
    try {
      const values = await editForm.validateFields()
      setEditSubmitting(true)
      await templateApi.update(editingTemplate!.id, values)
      message.success(t('common.updateSuccess'))
      setEditModalOpen(false)
      setEditingTemplate(null)
      editForm.resetFields()
      loadTemplates(currentPage, selectedCategory, searchQuery)
      if (activeTab === 'mine') {
        loadMyTemplates()
      }
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return
      message.error(extractApiError(err, t('common.saveFailed')))
    } finally {
      setEditSubmitting(false)
    }
  }

  // Render template grid
  const renderTemplateGrid = (items: Template[], showDelete: boolean, isLoading = loading) => {
    if (isLoading && items.length === 0) {
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
            <Space orientation="vertical" size={4}>
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
              onEdit={handleEditTemplate}
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
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => loadTemplates(currentPage, selectedCategory, searchQuery)}>
            {t('common.refresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
            {t('template.create')}
          </Button>
        </Space>
      </div>

      {error && (
        <Alert
          title={error}
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
            key: 'official',
            label: (
              <span>
                <CrownOutlined />
                {t('template.builtIn')}
              </span>
            ),
            children: (
              <>
                <Card style={{ marginBottom: 16 }}>
                  <Space orientation="vertical" size={12} style={{ width: '100%' }}>
                    <Text type="secondary">{t('template.builtInHint')}</Text>
                    <Row gutter={16} align="middle">
                      <Col flex="300px">
                        <Search
                          placeholder={t('template.search')}
                          allowClear
                          enterButton={<SearchOutlined />}
                          onSearch={(value) => {
                            setOfficialSearch(value)
                            loadOfficialTemplates(officialCategory, value)
                          }}
                          onChange={(e) => {
                            if (!e.target.value) {
                              setOfficialSearch('')
                              loadOfficialTemplates(officialCategory, '')
                            }
                          }}
                        />
                      </Col>
                      <Col flex="auto">
                        <Space wrap>
                          <Segmented
                            options={[
                              { label: t('template.allCategories'), value: 'all' },
                              ...officialCategories.map((cat) => ({ label: cat.name, value: cat.id })),
                            ]}
                            value={officialCategory}
                            onChange={(value) => {
                              const cat = value as string
                              setOfficialCategory(cat)
                              loadOfficialTemplates(cat, officialSearch)
                            }}
                          />
                        </Space>
                      </Col>
                    </Row>
                  </Space>
                </Card>

                <OfficialTemplateGrid
                  templates={officialTemplates}
                  loading={officialLoading}
                  onUse={handleUseOfficialTemplate}
                />
              </>
            ),
          },
          {
            key: 'browse',
            label: (
              <span>
                <BookOutlined />
                {t('template.community')}
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
                      showSizeChanger
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
            children: renderTemplateGrid(myTemplates, true, myLoading),
          },
        ]}
      />

      {/* Create Template Modal */}
      <Modal
        title={t('template.create')}
        open={createModalOpen}
        onCancel={() => { setCreateModalOpen(false); createForm.resetFields(); }}
        onOk={() => createForm.submit()}
        okText={t('common.create')}
        cancelText={t('common.cancel')}
        confirmLoading={createSubmitting}
        width={500}
        forceRender
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
          <Form.Item
            name="tags"
            label={t('template.tags')}
            rules={[{ type: 'array', max: 20, message: t('common.maxItems', { max: 20 }) }]}
          >
            <Select mode="tags" placeholder={t('template.tagsPlaceholder')} maxCount={20} />
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
       forceRender>
        <Form form={useForm} layout="vertical" onFinish={handleUseTemplate}>
          <Form.Item
            name="flowName"
            label={t('template.flowName')}
            rules={[
              { required: true, message: t('template.flowNamePlaceholder') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('template.flowNamePlaceholder')} maxLength={255} />
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

      {/* Edit Template Modal */}
      <Modal
        title={`${t('common.edit')}: ${editingTemplate?.name}`}
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); editForm.resetFields(); }}
        onOk={handleEditSubmit}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        confirmLoading={editSubmitting}
        forceRender
        width={500}
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('template.templateName')}
            rules={[
              { required: true, message: t('template.templateNameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="category" label={t('template.category')}>
            <Select placeholder={t('template.category')}>
              {categories.map((cat) => (
                <Select.Option key={cat} value={cat}>{cat}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="tags"
            label={t('template.tags')}
            rules={[{ type: 'array', max: 20, message: t('common.maxItems', { max: 20 }) }]}
          >
            <Select mode="tags" placeholder={t('template.tagsPlaceholder')} maxCount={20} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
