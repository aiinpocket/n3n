import { useEffect, useState, useCallback } from 'react'
import { Button, Card, Table, Space, Modal, Form, Input, message, Tag, Dropdown } from 'antd'
import { PlusOutlined, EditOutlined, PlayCircleOutlined, DeleteOutlined, SearchOutlined, UploadOutlined, ExportOutlined, MoreOutlined, ThunderboltOutlined, BulbOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useFlowStore } from '../stores/flowStore'
import type { Flow } from '../api/flow'
import FlowExportModal from '../components/flow/FlowExportModal'
import FlowImportModal from '../components/flow/FlowImportModal'
import FlowGeneratorModal from '../components/ai/FlowGeneratorModal'
import { Typography, Result } from 'antd'

const { Text } = Typography

export default function FlowListPage() {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const { flows, totalElements, loading, currentPage, pageSize, searchQuery, fetchFlows, setSearchQuery, createFlow, deleteFlow } = useFlowStore()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [creating, setCreating] = useState(false)
  const [searchValue, setSearchValue] = useState(searchQuery)
  const [importModalOpen, setImportModalOpen] = useState(false)
  const [exportFlow, setExportFlow] = useState<{ id: string; name: string; version: string } | null>(null)
  const [aiGeneratorOpen, setAiGeneratorOpen] = useState(false)

  useEffect(() => {
    fetchFlows()
  }, [fetchFlows])

  // Debounced search
  const handleSearch = useCallback((value: string) => {
    setSearchQuery(value)
    fetchFlows(0, pageSize, value)
  }, [fetchFlows, pageSize, setSearchQuery])

  const handleCreate = async (values: { name: string; description?: string }) => {
    setCreating(true)
    try {
      const flow = await createFlow(values.name, values.description)
      message.success(t('flow.createSuccess'))
      setCreateModalOpen(false)
      form.resetFields()
      navigate(`/flows/${flow.id}/edit`)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message || t('common.createFailed'))
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteFlow(id)
      message.success(t('flow.deleteSuccess'))
    } catch {
      message.error(t('common.deleteFailed'))
    }
  }

  const getLocale = () => {
    switch (i18n.language) {
      case 'ja': return 'ja-JP'
      case 'en': return 'en-US'
      default: return 'zh-TW'
    }
  }

  const columns = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Flow) => (
        <a onClick={() => navigate(`/flows/${record.id}/edit`)}>{name}</a>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('flow.latestVersion'),
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version: string | null) => version || '-',
    },
    {
      title: t('flow.publishedVersion'),
      dataIndex: 'publishedVersion',
      key: 'publishedVersion',
      render: (version: string | null) =>
        version ? <Tag color="green">{version}</Tag> : <Tag>{t('common.notPublished')}</Tag>,
    },
    {
      title: t('common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string) => new Date(date).toLocaleString(getLocale()),
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 280,
      render: (_: unknown, record: Flow) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/flows/${record.id}/edit`)}
          >
            {t('common.edit')}
          </Button>
          <Button
            type="link"
            size="small"
            icon={<PlayCircleOutlined />}
            disabled={!record.publishedVersion}
            onClick={() => navigate(`/executions/new?flowId=${record.id}`)}
          >
            {t('flow.trigger')}
          </Button>
          <Dropdown
            menu={{
              items: [
                {
                  key: 'export',
                  icon: <ExportOutlined />,
                  label: t('flow.export'),
                  disabled: !record.latestVersion,
                  onClick: () => setExportFlow({
                    id: record.id,
                    name: record.name,
                    version: record.latestVersion || '1',
                  }),
                },
                {
                  type: 'divider',
                },
                {
                  key: 'delete',
                  icon: <DeleteOutlined />,
                  label: t('common.delete'),
                  danger: true,
                  onClick: () => handleDelete(record.id),
                },
              ],
            }}
          >
            <Button type="link" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title={t('flow.title')}
        extra={
          <Space>
            <Input.Search
              placeholder={t('flow.searchPlaceholder')}
              allowClear
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onSearch={handleSearch}
              style={{ width: 250 }}
              enterButton={<SearchOutlined />}
            />
            <Button
              icon={<UploadOutlined />}
              onClick={() => setImportModalOpen(true)}
            >
              {t('flow.import')}
            </Button>
            <Button
              icon={<ThunderboltOutlined />}
              onClick={() => setAiGeneratorOpen(true)}
              style={{ background: '#722ed1', borderColor: '#722ed1', color: '#fff' }}
            >
              AI 建立
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              {t('flow.newFlow')}
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={flows}
          rowKey="id"
          loading={loading}
          pagination={{
            current: currentPage + 1,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            showTotal: (total) => t('common.total', { count: total }),
            onChange: (page, size) => fetchFlows(page - 1, size, searchQuery),
          }}
          locale={{
            emptyText: (
              <Result
                icon={<BulbOutlined style={{ color: '#722ed1' }} />}
                title="開始建立您的第一個工作流程"
                subTitle="工作流程可以幫助您自動化日常任務，節省時間提高效率"
                extra={
                  <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 400 }}>
                    <Button
                      type="primary"
                      size="large"
                      icon={<ThunderboltOutlined />}
                      onClick={() => setAiGeneratorOpen(true)}
                      style={{ width: '100%', background: '#722ed1', borderColor: '#722ed1' }}
                    >
                      用 AI 快速建立（推薦）
                    </Button>
                    <Text type="secondary">
                      用口語描述您想要的流程，AI 自動幫您生成
                    </Text>
                    <Button
                      size="large"
                      icon={<PlusOutlined />}
                      onClick={() => setCreateModalOpen(true)}
                      style={{ width: '100%' }}
                    >
                      從空白開始
                    </Button>
                    <Button
                      icon={<UploadOutlined />}
                      onClick={() => setImportModalOpen(true)}
                      style={{ width: '100%' }}
                    >
                      匯入現有流程
                    </Button>
                  </Space>
                }
              />
            ),
          }}
        />
      </Card>

      <Modal
        title={t('flow.newFlow')}
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="name"
            label={t('flow.flowName')}
            rules={[{ required: true, message: t('flow.flowNamePlaceholder') }]}
          >
            <Input placeholder={t('flow.flowNamePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('flow.flowDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('flow.flowDescriptionPlaceholder')} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setCreateModalOpen(false)
                form.resetFields()
              }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Import Modal */}
      <FlowImportModal
        visible={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={() => fetchFlows()}
      />

      {/* Export Modal */}
      {exportFlow && (
        <FlowExportModal
          visible={!!exportFlow}
          flowId={exportFlow.id}
          flowName={exportFlow.name}
          version={exportFlow.version}
          onClose={() => setExportFlow(null)}
        />
      )}

      {/* AI Flow Generator Modal */}
      <FlowGeneratorModal
        open={aiGeneratorOpen}
        onClose={() => setAiGeneratorOpen(false)}
        onCreateFlow={async (flowDef) => {
          if (flowDef) {
            // First create a flow, then navigate to editor with the generated content
            try {
              const flow = await createFlow('AI 生成的流程', '由 AI 根據自然語言描述自動生成')
              message.success('流程已建立，正在跳轉到編輯器...')
              // Navigate to editor and let it handle the flow definition
              navigate(`/flows/${flow.id}/edit`, {
                state: { generatedFlow: flowDef },
              })
            } catch {
              message.error('建立流程失敗')
            }
          }
        }}
      />
    </>
  )
}
