import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Tooltip,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Tabs,
  Empty,
  Badge,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  ToolOutlined,
  ApiOutlined,
  CodeOutlined,
  GlobalOutlined,
  BellOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useSkillStore } from '../stores/skillStore'
import type { Skill } from '../api/skill'

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

export default function SkillsPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState('all')
  const [testModalOpen, setTestModalOpen] = useState(false)
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const [testInput, setTestInput] = useState('{}')
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testing, setTesting] = useState(false)

  const {
    skills,
    builtinSkills,
    categories,
    isLoading,
    fetchSkills,
    fetchBuiltinSkills,
    fetchCategories,
    executeSkill,
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
      const input = JSON.parse(testInput)
      const result = await executeSkill(selectedSkill.id, input)

      if (result.success) {
        setTestResult(JSON.stringify(result.data, null, 2))
        message.success(t('skill.testSuccess'))
      } else {
        setTestResult(`Error: ${result.errorCode || ''} ${result.error}`)
        message.error(result.error)
      }
    } catch (error) {
      const err = error as Error
      setTestResult(`Error: ${err.message}`)
      message.error(err.message)
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
      width: 150,
      render: (_: unknown, record: Skill) => (
        <Space>
          <Tooltip title={t('skill.test')}>
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => openTestModal(record)}
            />
          </Tooltip>
          {!record.isBuiltin && (
            <>
              <Tooltip title={t('common.edit')}>
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  disabled
                />
              </Tooltip>
              <Popconfirm
                title={t('skill.deleteConfirm')}
                onConfirm={() => {/* deleteSkill(record.id) */}}
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  disabled
                />
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const filteredSkills =
    activeTab === 'all'
      ? skills
      : activeTab === 'builtin'
      ? builtinSkills
      : skills.filter((s) => s.category === activeTab)

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
          <Button type="primary" icon={<PlusOutlined />} disabled>
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

        <Table
          columns={columns}
          dataSource={filteredSkills}
          rowKey="id"
          loading={isLoading}
          pagination={{ pageSize: 10 }}
          locale={{
            emptyText: <Empty description={t('skill.noSkills')} />,
          }}
        />
      </Card>

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
              <pre style={{ background: '#f5f5f5', padding: 8, borderRadius: 4, fontSize: 12 }}>
                {JSON.stringify(selectedSkill.inputSchema, null, 2)}
              </pre>
            </div>

            <Form layout="vertical">
              <Form.Item label={t('skill.input')}>
                <TextArea
                  rows={6}
                  value={testInput}
                  onChange={(e) => setTestInput(e.target.value)}
                  placeholder="{}"
                  style={{ fontFamily: 'monospace' }}
                />
              </Form.Item>
            </Form>

            {testResult && (
              <div>
                <strong>{t('skill.result')}:</strong>
                <pre
                  style={{
                    background: testResult.startsWith('Error') ? '#fff2f0' : '#f6ffed',
                    padding: 8,
                    borderRadius: 4,
                    fontSize: 12,
                    maxHeight: 200,
                    overflow: 'auto',
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
