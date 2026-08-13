import { useState, useEffect, useCallback } from 'react'
import { Card, Typography, Divider, Input, Button, Select, Tag, Space, Popconfirm, Empty, Tooltip } from 'antd'
import List from '../../components/common/SimpleList'
import { message } from '../../utils/feedback'
import { BulbOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ClearOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { aiMemoryApi, UserMemory, MemoryCategory } from '../../api/aiMemory'
import { extractApiError } from '../../utils/errorMessages'
import logger from '../../utils/logger'

const { Title, Text } = Typography

const CATEGORY_COLORS: Record<string, string> = {
  preference: 'geekblue',
  fact: 'green',
  project: 'purple',
  style: 'magenta',
  general: 'default',
}

const CATEGORY_OPTIONS: MemoryCategory[] = ['preference', 'fact', 'project', 'style', 'general']

export default function MemorySettings() {
  const { t } = useTranslation()
  const [memories, setMemories] = useState<UserMemory[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [newContent, setNewContent] = useState('')
  const [newCategory, setNewCategory] = useState<MemoryCategory>('general')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingContent, setEditingContent] = useState('')

  const fetchMemories = useCallback(async () => {
    setLoading(true)
    try {
      const data = await aiMemoryApi.list()
      setMemories(data)
    } catch (err) {
      logger.warn('Failed to fetch AI memories:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchMemories()
  }, [fetchMemories])

  const handleAdd = async () => {
    const content = newContent.trim()
    if (!content) return
    setSaving(true)
    try {
      const created = await aiMemoryApi.add({ content, category: newCategory })
      setMemories((prev) => [created, ...prev])
      setNewContent('')
      message.success(t('memory.added'))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('memory.addFailed')))
    } finally {
      setSaving(false)
    }
  }

  const handleUpdate = async (id: string) => {
    const content = editingContent.trim()
    if (!content) return
    try {
      const updated = await aiMemoryApi.update(id, { content })
      setMemories((prev) => prev.map((m) => (m.id === id ? updated : m)))
      setEditingId(null)
      message.success(t('memory.updated'))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('memory.updateFailed')))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await aiMemoryApi.remove(id)
      setMemories((prev) => prev.filter((m) => m.id !== id))
      message.success(t('memory.deleted'))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('memory.deleteFailed')))
    }
  }

  const handleClearAll = async () => {
    try {
      await aiMemoryApi.removeAll()
      setMemories([])
      message.success(t('memory.cleared'))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('memory.clearFailed')))
    }
  }

  return (
    <Card style={{ marginTop: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={5} style={{ color: 'var(--color-text-primary)', margin: 0 }}>
          <BulbOutlined style={{ marginRight: 8 }} />
          {t('memory.title')}
        </Title>
        {memories.length > 0 && (
          <Popconfirm
            title={t('memory.clearAllConfirmTitle')}
            description={t('memory.clearAllConfirmDesc')}
            okText={t('memory.clearAll')}
            okButtonProps={{ danger: true }}
            cancelText={t('common.cancel')}
            onConfirm={handleClearAll}
          >
            <Button danger type="text" size="small" icon={<ClearOutlined />}>
              {t('memory.clearAll')}
            </Button>
          </Popconfirm>
        )}
      </div>
      <Text type="secondary" style={{ fontSize: 13 }}>
        {t('memory.description')}
      </Text>
      <Divider style={{ margin: '16px 0' }} />

      {/* Add memory */}
      <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
        <Select
          value={newCategory}
          onChange={(value: MemoryCategory) => setNewCategory(value)}
          style={{ width: 130 }}
          options={CATEGORY_OPTIONS.map((c) => ({ value: c, label: t(`memory.category.${c}`) }))}
        />
        <Input
          value={newContent}
          onChange={(e) => setNewContent(e.target.value)}
          onPressEnter={handleAdd}
          placeholder={t('memory.addPlaceholder')}
          maxLength={2000}
        />
        <Button type="primary" icon={<PlusOutlined />} loading={saving} onClick={handleAdd} disabled={!newContent.trim()}>
          {t('memory.add')}
        </Button>
      </Space.Compact>

      {/* Memory list */}
      <List
        loading={loading}
        dataSource={memories}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('memory.empty')} /> }}
        renderItem={(memory) => (
          <List.Item
            key={memory.id}
            actions={
              editingId === memory.id
                ? [
                    <Button key="save" type="link" size="small" onClick={() => handleUpdate(memory.id)}>
                      {t('common.save')}
                    </Button>,
                    <Button key="cancel" type="link" size="small" onClick={() => setEditingId(null)}>
                      {t('common.cancel')}
                    </Button>,
                  ]
                : [
                    <Tooltip key="edit" title={t('common.edit')}>
                      <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setEditingId(memory.id)
                          setEditingContent(memory.content)
                        }}
                        aria-label={t('common.edit')}
                      />
                    </Tooltip>,
                    <Popconfirm
                      key="delete"
                      title={t('memory.deleteConfirm')}
                      okText={t('common.delete')}
                      okButtonProps={{ danger: true }}
                      cancelText={t('common.cancel')}
                      onConfirm={() => handleDelete(memory.id)}
                    >
                      <Tooltip title={t('common.delete')}>
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
                      </Tooltip>
                    </Popconfirm>,
                  ]
            }
          >
            <List.Item.Meta
              title={
                editingId === memory.id ? (
                  <Input.TextArea
                    value={editingContent}
                    onChange={(e) => setEditingContent(e.target.value)}
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    maxLength={2000}
                  />
                ) : (
                  <Text style={{ fontWeight: 'normal', whiteSpace: 'pre-wrap' }}>{memory.content}</Text>
                )
              }
              description={
                <Space size={8} wrap>
                  <Tag color={CATEGORY_COLORS[memory.category] || 'default'}>
                    {t(`memory.category.${memory.category}`, memory.category)}
                  </Tag>
                  <Tag icon={memory.source === 'assistant' ? <RobotOutlined /> : <UserOutlined />}>
                    {t(`memory.source.${memory.source}`, memory.source)}
                  </Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {new Date(memory.createdAt).toLocaleString()}
                  </Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  )
}
