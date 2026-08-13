import { useEffect, useState, useMemo } from 'react'
import { Card, Table, Button, Space, Tag, Tooltip, Modal, Form, Input, Select, Popconfirm, Tabs, Empty, Badge, Alert, Switch, Result } from 'antd'
import { message } from '../utils/feedback'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  CopyOutlined,
  ToolOutlined,
  ApiOutlined,
  CodeOutlined,
  GlobalOutlined,
  BellOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { extractApiError } from '../utils/errorMessages'
import { useSkillStore } from '../stores/skillStore'
import type { Skill, CreateSkillRequest, UpdateSkillRequest } from '../api/skill'

const { TextArea } = Input

// Category icons
const categoryIcons: Record<string, React.ReactNode> = {
  web: <GlobalOutlined />,
  http: <ApiOutlined />,
  data: <DatabaseOutlined />,
  notify: <BellOutlined />,
  file: <CodeOutlined />,
  system: <ToolOutlined />,
}

// Category colors
const categoryColors: Record<string, string> = {
  web: 'blue',
  http: 'green',
  data: 'purple',
  notify: 'orange',
  file: 'cyan',
  system: 'red',
}

const CATEGORY_OPTIONS = ['web', 'http', 'data', 'notify', 'file', 'system']
const IMPL_TYPE_OPTIONS = ['code', 'http', 'template', 'plugin']

export default function SkillsPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState('all')
  const [testModalOpen, setTestModalOpen] = useState(false)
  const [formModalOpen, setFormModalOpen] = useState(false)
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null)
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const [testInput, setTestInput] = useState('{}')
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testing, setTesting] = useState(false)
  const [formSubmitting, setFormSubmitting] = useState(false)
  const [form] = Form.useForm()
  const [searchText, setSearchText] = useState('')

  const {
    skills,
    builtinSkills,
    categories,
    isLoading,
    error,
    fetchSkills,
    fetchBuiltinSkills,
    fetchCategories,
    createSkill,
    updateSkill,
    executeSkill,
    deleteSkill,
    clearError,
  } = useSkillStore()

  useEffect(() => {
    fetchSkills()
    fetchBuiltinSkills()
    fetchCategories()
  }, [fetchSkills, fetchBuiltinSkills, fetchCategories])

  const handleTest = async () => {
    if (!selectedSkill) return

    setTesting(true)
    setTestResult(null)

    try {
      let input: Record<string, unknown>
      try {
        input = JSON.parse(testInput)
      } catch {
        message.error(t('component.jsonFormatError'))
        setTestResult(t('component.jsonFormatError'))
        setTesting(false)
        return
      }
      const result = await executeSkill(selectedSkill.id, input)

      if (result.success) {
        setTestResult(JSON.stringify(result.data, null, 2))
        message.success(t('skill.testSuccess'))
      } else {
        setTestResult(`${result.errorCode || ''} ${result.error || t('skill.testFailed')}`.trim())
        message.error(t('skill.testFailed'))
      }
    } catch (error) {
      setTestResult(extractApiError(error, t('skill.testFailed')))
      message.error(extractApiError(error, t('skill.testFailed')))
    } finally {
      setTesting(false)
    }
  }

  const openTestModal = (skill: Skill) => {
    setSelectedSkill(skill)
    setTestInput(JSON.stringify(getExampleInput(skill.inputSchema), null, 2))
    setTestResult(null)
    setTestModalOpen(true)
  }

  const openCreateModal = () => {
    setEditingSkill(null)
    form.resetFields()
    form.setFieldsValue({
      visibility: 'private',
      implementationType: 'code',
      inputSchema: '{\n  "type": "object",\n  "properties": {}\n}',
      outputSchema: '',
      implementationConfig: '',
    })
    setFormModalOpen(true)
  }

  const openEditModal = (skill: Skill) => {
    setEditingSkill(skill)
    form.setFieldsValue({
      displayName: skill.displayName,
      description: skill.description || '',
      category: skill.category,
      implementationType: skill.implementationType,
      inputSchema: JSON.stringify(skill.inputSchema, null, 2),
      outputSchema: skill.outputSchema ? JSON.stringify(skill.outputSchema, null, 2) : '',
      visibility: skill.visibility || 'private',
      implementationConfig: '',
    })
    setFormModalOpen(true)
  }

  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields()
      setFormSubmitting(true)

      let inputSchema: Record<string, unknown>
      try {
        inputSchema = JSON.parse(values.inputSchema)
      } catch {
        message.error(t('component.jsonFormatError'))
        setFormSubmitting(false)
        return
      }

      let outputSchema: Record<string, unknown> | undefined
      if (values.outputSchema?.trim()) {
        try {
          outputSchema = JSON.parse(values.outputSchema)
        } catch {
          message.error(t('component.jsonFormatError'))
          setFormSubmitting(false)
          return
        }
      }

      let implementationConfig: Record<string, unknown> | undefined
      if (values.implementationConfig?.trim()) {
        try {
          implementationConfig = JSON.parse(values.implementationConfig)
        } catch {
          message.error(t('component.jsonFormatError'))
          setFormSubmitting(false)
          return
        }
      }

      if (editingSkill) {
        const request: UpdateSkillRequest = {
          displayName: values.displayName,
          description: values.description || undefined,
          category: values.category,
          icon: values.icon || undefined,
          inputSchema,
          outputSchema,
          implementationConfig,
          visibility: values.visibility,
        }
        await updateSkill(editingSkill.id, request)
        message.success(t('skill.updateSuccess'))
      } else {
        const request: CreateSkillRequest = {
          name: values.name,
          displayName: values.displayName,
          description: values.description || undefined,
          category: values.category,
          icon: values.icon || undefined,
          implementationType: values.implementationType,
          inputSchema,
          outputSchema,
          implementationConfig,
          visibility: values.visibility,
        }
        await createSkill(request)
        message.success(t('skill.createSuccess'))
      }
      setFormModalOpen(false)
      form.resetFields()
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(extractApiError(error, editingSkill ? t('skill.updateFailed') : t('skill.createFailed')))
    } finally {
      setFormSubmitting(false)
    }
  }

  const getExampleInput = (schema: Record<string, unknown>): Record<string, unknown> => {
    const properties = (schema.properties as Record<string, unknown>) || {}
    const example: Record<string, unknown> = {}

    for (const [key, value] of Object.entries(properties)) {
      const prop = value as { type?: string; default?: unknown }
      if (prop.default !== undefined) {
        example[key] = prop.default
      } else if (prop.type === 'string') {
        example[key] = ''
      } else if (prop.type === 'number' || prop.type === 'integer') {
        example[key] = 0
      } else if (prop.type === 'boolean') {
        example[key] = false
      } else if (prop.type === 'object') {
        example[key] = {}
      } else if (prop.type === 'array') {
        example[key] = []
      }
    }

    return example
  }

  const columns = [
    {
      title: t('skill.name'),
      dataIndex: 'displayName',
      key: 'displayName',
      sorter: (a: Skill, b: Skill) => a.displayName.localeCompare(b.displayName),
      render: (text: string, record: Skill) => (
        <Space>
          {categoryIcons[record.category] || <ToolOutlined />}
          <span>{text}</span>
          {record.isBuiltin && (
            <Tag color="blue">{t('skill.builtin')}</Tag>
          )}
        </Space>
      ),
    },
    {
      title: t('skill.identifier'),
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => <code>{text}</code>,
    },
    {
      title: t('skill.category'),
      dataIndex: 'category',
      key: 'category',
      sorter: (a: Skill, b: Skill) => a.category.localeCompare(b.category),
      render: (category: string) => (
        <Tag color={categoryColors[category] || 'default'}>{category}</Tag>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('common.status'),
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      render: (isEnabled: boolean) => (
        <Badge
          status={isEnabled ? 'success' : 'default'}
          text={isEnabled ? t('skill.enabled') : t('skill.disabled')}
        />
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 220,
      render: (_: unknown, record: Skill) => (
        <Space>
          {!record.isBuiltin && (
            <Tooltip title={record.isEnabled ? t('skill.disable') : t('skill.enable')}>
              <Switch
                size="small"
                checked={record.isEnabled}
                onChange={async (checked) => {
                  try {
                    await updateSkill(record.id, { isEnabled: checked })
                    message.success(checked ? t('skill.enableSuccess') : t('skill.disableSuccess'))
                  } catch (error) {
                    message.error(extractApiError(error, t('common.operationFailed')))
                  }
                }}
              />
            </Tooltip>
          )}
          <Tooltip title={t('skill.test')}>
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => openTestModal(record)}
              aria-label={t('skill.test')}
            />
          </Tooltip>
          {!record.isBuiltin && (
            <>
              <Tooltip title={t('skill.editSkill')}>
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEditModal(record)}
                  aria-label={t('skill.editSkill')}
                />
              </Tooltip>
              <Popconfirm
                title={t('skill.deleteConfirm')}
                onConfirm={async () => {
                  try {
                    await deleteSkill(record.id)
                    message.success(t('common.deleteSuccess'))
                  } catch (error) {
                    message.error(extractApiError(error, t('common.deleteFailed')))
                  }
                }}
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label={t('common.delete')}
                />
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const filteredSkills = useMemo(() => {
    let list =
      activeTab === 'all'
        ? skills
        : activeTab === 'builtin'
        ? builtinSkills
        : skills.filter((s) => s.category === activeTab)
    if (searchText) {
      const lower = searchText.toLowerCase()
      list = list.filter(s =>
        s.displayName.toLowerCase().includes(lower) ||
        s.name.toLowerCase().includes(lower) ||
        (s.description && s.description.toLowerCase().includes(lower))
      )
    }
    return list
  }, [activeTab, skills, builtinSkills, searchText])

  if (!isLoading && error && skills.length === 0) {
    return (
      <Result
        status="error"
        title={t('common.loadFailed')}
        subTitle={error}
        extra={<Button type="primary" onClick={() => { clearError(); fetchSkills() }}>{t('common.retry')}</Button>}
      />
    )
  }

  return (
    <>
      <Card
        title={
          <Space>
            <ToolOutlined />
            {t('skill.title')}
          </Space>
        }
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            {t('skill.createSkill')}
          </Button>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: 'all', label: t('skill.all') },
            { key: 'builtin', label: t('skill.builtinSkills') },
            ...categories.map((cat) => ({
              key: cat,
              label: (
                <Space>
                  {categoryIcons[cat]}
                  {cat}
                </Space>
              ),
            })),
          ]}
        />

        {error && (
          <Alert
            title={error}
            type="error"
            showIcon
            closable
            onClose={clearError}
            style={{ marginBottom: 16 }}
            action={
              <Button size="small" icon={<ReloadOutlined />} onClick={() => { fetchSkills(); fetchBuiltinSkills(); }}>
                {t('common.retry')}
              </Button>
            }
          />
        )}

        <Input
          placeholder={t('skill.searchPlaceholder')}
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          allowClear
          style={{ width: 300, marginBottom: 16 }}
        />

        <Table
          columns={columns}
          dataSource={filteredSkills}
          rowKey="id"
          loading={isLoading}
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => t('common.total', { count: total }) }}
          locale={{
            emptyText: (
              <Empty description={t('skill.noSkills')}>
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
                  {t('skill.createFirst')}
                </Button>
              </Empty>
            ),
          }}
          scroll={{ x: 800 }}
        />
      </Card>

      {/* Create/Edit Skill Modal */}
      <Modal
        title={editingSkill ? t('skill.editSkill') : t('skill.createSkill')}
        open={formModalOpen}
        onCancel={() => { setFormModalOpen(false); form.resetFields(); }}
        onOk={handleFormSubmit}
        okText={editingSkill ? t('common.save') : t('common.create')}
        cancelText={t('common.cancel')}
        confirmLoading={formSubmitting}
        width={600}
        forceRender
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          {!editingSkill && (
            <Form.Item
              name="name"
              label={t('skill.nameIdentifier')}
              rules={[
                { required: true, message: t('skill.nameRequired') },
                { pattern: /^[a-z][a-z0-9_]*$/, message: t('skill.namePattern') },
                { max: 100, message: t('common.maxLength', { max: 100 }) },
              ]}
            >
              <Input placeholder="my_custom_skill" maxLength={100} />
            </Form.Item>
          )}

          <Form.Item
            name="displayName"
            label={t('skill.displayName')}
            rules={[
              { required: true, message: t('skill.displayNameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input maxLength={255} />
          </Form.Item>

          <Form.Item name="description" label={t('common.description')}>
            <TextArea rows={2} maxLength={1000} showCount />
          </Form.Item>

          <Form.Item
            name="category"
            label={t('skill.category')}
            rules={[{ required: true, message: t('skill.categoryRequired') }]}
          >
            <Select>
              {CATEGORY_OPTIONS.map((cat) => (
                <Select.Option key={cat} value={cat}>
                  <Space>{categoryIcons[cat]} {cat}</Space>
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="icon"
            label={t('skill.icon')}
            tooltip={t('skill.iconTooltip')}
            rules={[{ max: 100, message: t('common.maxLength', { max: 100 }) }]}
          >
            <Input placeholder="🔧" maxLength={100} />
          </Form.Item>

          {!editingSkill && (
            <Form.Item
              name="implementationType"
              label={t('skill.implementationType')}
              rules={[{ required: true, message: t('skill.implementationTypeRequired') }]}
            >
              <Select>
                {IMPL_TYPE_OPTIONS.map((type) => (
                  <Select.Option key={type} value={type}>{type}</Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          <Form.Item
            name="inputSchema"
            label={t('skill.inputSchema')}
            rules={[
              { required: true, message: t('skill.inputSchemaRequired') },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  try { JSON.parse(value); return Promise.resolve(); }
                  catch { return Promise.reject(new Error(t('common.invalidJson'))); }
                },
              },
            ]}
          >
            <TextArea rows={4} style={{ fontFamily: 'monospace' }} />
          </Form.Item>

          <Form.Item
            name="outputSchema"
            label={t('skill.outputSchema')}
            rules={[{
              validator: (_, value) => {
                if (!value) return Promise.resolve();
                try { JSON.parse(value); return Promise.resolve(); }
                catch { return Promise.reject(new Error(t('common.invalidJson'))); }
              },
            }]}
          >
            <TextArea rows={3} style={{ fontFamily: 'monospace' }} placeholder="{}" />
          </Form.Item>

          <Form.Item
            name="implementationConfig"
            label={t('skill.implementationConfig')}
            rules={[{
              validator: (_, value) => {
                if (!value) return Promise.resolve();
                try { JSON.parse(value); return Promise.resolve(); }
                catch { return Promise.reject(new Error(t('common.invalidJson'))); }
              },
            }]}
          >
            <TextArea rows={3} style={{ fontFamily: 'monospace' }} placeholder="{}" />
          </Form.Item>

          <Form.Item name="visibility" label={t('skill.visibility')}>
            <Select>
              <Select.Option value="private">{t('skill.visibilityPrivate')}</Select.Option>
              <Select.Option value="public">{t('skill.visibilityPublic')}</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* Test Skill Modal */}
      <Modal
        title={
          <Space>
            <PlayCircleOutlined />
            {t('skill.testSkill')}: {selectedSkill?.displayName}
          </Space>
        }
        open={testModalOpen}
        onCancel={() => setTestModalOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setTestModalOpen(false)}>
            {t('common.cancel')}
          </Button>,
          <Button
            key="test"
            type="primary"
            loading={testing}
            onClick={handleTest}
            icon={<PlayCircleOutlined />}
          >
            {t('skill.execute')}
          </Button>,
        ]}
        width={700}
      >
        {selectedSkill && (
          <>
            <div style={{ marginBottom: 16 }}>
              <strong>{t('skill.inputSchema')}:</strong>
              <pre style={{ background: 'var(--color-bg-elevated)', padding: 8, borderRadius: 4, fontSize: 12, color: 'var(--color-text-primary)' }}>
                {JSON.stringify(selectedSkill.inputSchema, null, 2)}
              </pre>
            </div>

            <Form layout="vertical">
              <Form.Item label={t('skill.input')}>
                <TextArea
                  rows={6}
                  value={testInput}
                  onChange={(e) => setTestInput(e.target.value)}
                  placeholder={t('skill.inputJsonPlaceholder')}
                  style={{ fontFamily: 'monospace' }}
                />
              </Form.Item>
            </Form>

            {testResult && (
              <div>
                <Space style={{ marginBottom: 4 }}>
                  <strong>{t('skill.result')}:</strong>
                  <Button
                    type="link"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(testResult)
                        message.success(t('common.copied'))
                      } catch {
                        message.error(t('common.copyFailed'))
                      }
                    }}
                  >
                    {t('common.copy')}
                  </Button>
                </Space>
                <pre
                  style={{
                    background: testResult.startsWith('Error') ? 'rgba(239, 68, 68, 0.15)' : 'rgba(34, 197, 94, 0.15)',
                    padding: 8,
                    borderRadius: 4,
                    fontSize: 12,
                    maxHeight: 200,
                    overflow: 'auto',
                    color: testResult.startsWith('Error') ? 'var(--color-error)' : 'var(--color-success)',
                  }}
                >
                  {testResult}
                </pre>
              </div>
            )}
          </>
        )}
      </Modal>
    </>
  )
}
