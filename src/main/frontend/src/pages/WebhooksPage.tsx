import { useEffect, useState, useMemo, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  message,
  Tooltip,
  Typography,
  Card,
  Empty,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  CopyOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  StopOutlined,
  LinkOutlined,
  ThunderboltOutlined,
  EditOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useWebhookStore } from '../stores/webhookStore'
import { flowApi } from '../api/flow'
import type { Flow } from '../api/flow'
import type { Webhook, CreateWebhookRequest } from '../api/webhook'
import { webhookApi } from '../api/webhook'
import { extractApiError } from '../utils/errorMessages'

const { Text, Paragraph } = Typography

const WebhooksPage: React.FC = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm()
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingWebhook, setEditingWebhook] = useState<Webhook | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editForm] = Form.useForm()
  const [searchText, setSearchText] = useState('')

  const {
    webhooks,
    isLoading,
    fetchWebhooks,
    createWebhook,
    activateWebhook,
    deactivateWebhook,
    deleteWebhook,
    testWebhook,
  } = useWebhookStore()

  const [editableFlows, setEditableFlows] = useState<Flow[]>([])

  const loadEditableFlows = useCallback(async () => {
    try {
      const data = await flowApi.listEditableFlows()
      setEditableFlows(data)
    } catch {
      // Flow list loading failure is non-critical
    }
  }, [])

  const flowNameMap = useMemo(() => {
    const map = new Map<string, string>()
    editableFlows.forEach((f) => map.set(f.id, f.name))
    return map
  }, [editableFlows])

  const filteredWebhooks = useMemo(() => {
    if (!searchText) return webhooks
    const lower = searchText.toLowerCase()
    return webhooks.filter(w =>
      w.name.toLowerCase().includes(lower) ||
      (flowNameMap.get(w.flowId) || '').toLowerCase().includes(lower) ||
      w.method.toLowerCase().includes(lower) ||
      (w.webhookUrl && w.webhookUrl.toLowerCase().includes(lower))
    )
  }, [webhooks, searchText, flowNameMap])

  useEffect(() => {
    fetchWebhooks()
    loadEditableFlows()
  }, [fetchWebhooks, loadEditableFlows])

  const handleCreate = async (values: CreateWebhookRequest) => {
    setCreating(true)
    try {
      await createWebhook(values)
      message.success(t('webhook.createSuccess'))
      setIsModalOpen(false)
      form.resetFields()
    } catch (error) {
      message.error(extractApiError(error, t('common.createFailed')))
    } finally {
      setCreating(false)
    }
  }

  const handleToggleActive = async (webhook: Webhook) => {
    try {
      if (webhook.isActive) {
        await deactivateWebhook(webhook.id)
        message.success(t('webhook.deactivated'))
      } else {
        await activateWebhook(webhook.id)
        message.success(t('webhook.activated'))
      }
    } catch (error) {
      message.error(extractApiError(error, t('common.updateFailed')))
    }
  }

  const handleDelete = async (id: string) => {
    Modal.confirm({
      title: t('webhook.deleteConfirm'),
      content: t('webhook.deleteWarning'),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await deleteWebhook(id)
          message.success(t('webhook.deleteSuccess'))
        } catch (error) {
          message.error(extractApiError(error, t('common.deleteFailed')))
        }
      },
    })
  }

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      message.success(t('common.copied'))
    } catch {
      message.error(t('common.copyFailed'))
    }
  }

  const [testing, setTesting] = useState<string | null>(null)

  const handleTest = async (webhook: Webhook) => {
    if (!webhook.isActive) {
      message.warning(t('webhook.testInactiveWarning'))
      return
    }
    setTesting(webhook.id)
    try {
      const result = await testWebhook(webhook.id)
      if (result.success) {
        const key = `webhook-test-${Date.now()}`
        message.success({
          content: (
            <span>
              {t('webhook.testSuccess', { executionId: result.executionId?.substring(0, 8) })}{' '}
              {result.executionId && (
                <Button type="link" size="small" style={{ padding: 0 }} onClick={() => { navigate(`/executions/${result.executionId}`); message.destroy(key) }}>
                  {t('webhook.viewExecution')}
                </Button>
              )}
            </span>
          ),
          key,
          duration: 5,
        })
      } else {
        message.error(t('webhook.testFailed'))
      }
    } catch (error) {
      message.error(extractApiError(error, t('webhook.testFailed')))
    } finally {
      setTesting(null)
    }
  }

  const handleEdit = (record: Webhook) => {
    setEditingWebhook(record)
    editForm.setFieldsValue({
      name: record.name,
      authType: record.authType || undefined,
      authConfig: record.authConfig || undefined,
    })
    setEditModalOpen(true)
  }

  const handleEditSubmit = async () => {
    try {
      const values = await editForm.validateFields()
      setEditSubmitting(true)
      await webhookApi.update(editingWebhook!.id, values)
      message.success(t('common.updateSuccess'))
      setEditModalOpen(false)
      setEditingWebhook(null)
      editForm.resetFields()
      fetchWebhooks()
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return
      message.error(extractApiError(err, t('common.saveFailed')))
    } finally {
      setEditSubmitting(false)
    }
  }

  const columns: ColumnsType<Webhook> = [
    {
      title: t('webhook.name'),
      dataIndex: 'name',
      key: 'name',
      sorter: (a: Webhook, b: Webhook) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Space>
          <ApiOutlined />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: t('webhook.flow'),
      dataIndex: 'flowId',
      key: 'flowId',
      render: (flowId: string) => {
        const name = flowNameMap.get(flowId)
        if (name) {
          return (
            <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/flows/${flowId}/edit`)}>
              {name}
            </Button>
          )
        }
        return <Text type="secondary">{flowId?.substring(0, 8) || '-'}</Text>
      },
    },
    {
      title: t('webhook.method'),
      dataIndex: 'method',
      key: 'method',
      width: 100,
      sorter: (a: Webhook, b: Webhook) => a.method.localeCompare(b.method),
      render: (method: string) => {
        const colors: Record<string, string> = {
          GET: 'green',
          POST: 'blue',
          PUT: 'orange',
          PATCH: 'cyan',
          DELETE: 'red',
        }
        return <Tag color={colors[method] || 'default'}>{method}</Tag>
      },
    },
    {
      title: t('webhook.url'),
      dataIndex: 'webhookUrl',
      key: 'webhookUrl',
      ellipsis: true,
      render: (url: string) => (
        <Space>
          <Paragraph
            copyable={{ icon: <CopyOutlined />, tooltips: false }}
            style={{ marginBottom: 0 }}
          >
            <Text code style={{ fontSize: 12 }}>{url}</Text>
          </Paragraph>
        </Space>
      ),
    },
    {
      title: t('webhook.status'),
      dataIndex: 'isActive',
      key: 'isActive',
      width: 100,
      render: (isActive: boolean) =>
        isActive ? (
          <Tag icon={<CheckCircleOutlined />} color="success">
            {t('webhook.active')}
          </Tag>
        ) : (
          <Tag icon={<StopOutlined />} color="default">
            {t('webhook.inactive')}
          </Tag>
        ),
    },
    {
      title: t('webhook.authType'),
      dataIndex: 'authType',
      key: 'authType',
      width: 120,
      render: (authType: string | null) =>
        authType ? (
          <Tag>{authType}</Tag>
        ) : (
          <Text type="secondary">{t('webhook.noAuth')}</Text>
        ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      render: (_: unknown, record: Webhook) => (
        <Space>
          <Tooltip title={record.isActive ? t('webhook.deactivate') : t('webhook.activate')}>
            <Button
              type="text"
              icon={record.isActive ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => handleToggleActive(record)}
              aria-label={record.isActive ? t('webhook.deactivate') : t('webhook.activate')}
            />
          </Tooltip>
          <Tooltip title={t('webhook.test')}>
            <Button
              type="text"
              icon={<ThunderboltOutlined />}
              onClick={() => handleTest(record)}
              loading={testing === record.id}
              aria-label={t('webhook.test')}
            />
          </Tooltip>
          <Tooltip title={t('webhook.copyUrl')}>
            <Button
              type="text"
              icon={<CopyOutlined />}
              onClick={() => copyToClipboard(record.webhookUrl)}
              aria-label={t('webhook.copyUrl')}
            />
          </Tooltip>
          <Tooltip title={t('common.edit')}>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
              aria-label={t('common.edit')}
            />
          </Tooltip>
          <Tooltip title={t('common.delete')}>
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.id)}
              aria-label={t('common.delete')}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={
          <Space>
            <LinkOutlined />
            {t('webhook.title')}
          </Space>
        }
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setIsModalOpen(true)}
          >
            {t('webhook.create')}
          </Button>
        }
      >
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>
          {t('webhook.description')}
        </Paragraph>

        {webhooks.length === 0 && !isLoading ? (
          <Empty
            description={t('webhook.empty')}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setIsModalOpen(true)}
            >
              {t('webhook.createFirst')}
            </Button>
          </Empty>
        ) : (
          <>
            <Input
              placeholder={t('webhook.searchPlaceholder')}
              prefix={<SearchOutlined />}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              allowClear
              style={{ maxWidth: 400, marginBottom: 16 }}
            />
            <Table
              columns={columns}
              dataSource={filteredWebhooks}
              rowKey="id"
              loading={isLoading}
              pagination={{ pageSize: 10, showTotal: (total) => t('common.total', { count: total }) }}
              scroll={{ x: 1000 }}
            />
          </>
        )}
      </Card>

      <Modal
        title={t('webhook.createTitle')}
        open={isModalOpen}
        onCancel={() => {
          setIsModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          initialValues={{ method: 'POST' }}
        >
          <Form.Item
            name="flowId"
            label={t('webhook.flow')}
            rules={[{ required: true, message: t('webhook.flowRequired') }]}
          >
            <Select
              placeholder={t('webhook.selectFlow')}
              showSearch
              optionFilterProp="children"
            >
              {editableFlows.map((flow) => (
                <Select.Option key={flow.id} value={flow.id}>
                  {flow.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label={t('webhook.name')}
            rules={[
              { required: true, message: t('webhook.nameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('webhook.namePlaceholder')} maxLength={255} />
          </Form.Item>

          <Form.Item
            name="path"
            label={t('webhook.path')}
            rules={[
              { required: true, message: t('webhook.pathRequired') },
              {
                pattern: /^[a-zA-Z0-9_-]+$/,
                message: t('webhook.pathPattern'),
              },
              { max: 500, message: t('common.maxLength', { max: 500 }) },
            ]}
            extra={`${t('webhook.fullUrl')}: ${window.location.origin}/webhook/`}
          >
            <Input
              addonBefore="/webhook/"
              placeholder={t('webhook.pathPlaceholder')}
              maxLength={500}
            />
          </Form.Item>

          <Form.Item
            name="method"
            label={t('webhook.method')}
            rules={[{ required: true }]}
          >
            <Select>
              <Select.Option value="GET">GET</Select.Option>
              <Select.Option value="POST">POST</Select.Option>
              <Select.Option value="PUT">PUT</Select.Option>
              <Select.Option value="PATCH">PATCH</Select.Option>
              <Select.Option value="DELETE">DELETE</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="authType"
            label={t('webhook.authType')}
          >
            <Select placeholder={t('webhook.selectAuthType')} allowClear>
              <Select.Option value="none">{t('webhook.authNone')}</Select.Option>
              <Select.Option value="signature">{t('webhook.authSignature')}</Select.Option>
              <Select.Option value="apiKey">{t('webhook.authApiKey')}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.authType !== cur.authType}>
            {({ getFieldValue }) => {
              const authType = getFieldValue('authType');
              if (authType === 'signature') {
                return (
                  <Form.Item
                    name={['authConfig', 'secret']}
                    label={t('webhook.secret')}
                    rules={[
                      { required: true, message: t('webhook.secretRequired') },
                      { max: 500, message: t('common.maxLength', { max: 500 }) },
                    ]}
                  >
                    <Input.Password placeholder={t('webhook.secretPlaceholder')} maxLength={500} />
                  </Form.Item>
                );
              }
              if (authType === 'apiKey') {
                return (
                  <>
                    <Form.Item
                      name={['authConfig', 'headerName']}
                      label={t('webhook.headerName')}
                      rules={[
                        { required: true, message: t('webhook.headerNameRequired') },
                        { max: 255, message: t('common.maxLength', { max: 255 }) },
                      ]}
                    >
                      <Input placeholder="X-API-Key" maxLength={255} />
                    </Form.Item>
                    <Form.Item
                      name={['authConfig', 'apiKey']}
                      label={t('webhook.apiKey')}
                      rules={[
                        { required: true, message: t('webhook.apiKeyRequired') },
                        { max: 500, message: t('common.maxLength', { max: 500 }) },
                      ]}
                    >
                      <Input.Password placeholder={t('webhook.apiKeyPlaceholder')} maxLength={500} />
                    </Form.Item>
                  </>
                );
              }
              return null;
            }}
          </Form.Item>

          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setIsModalOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${t('common.edit')}: ${editingWebhook?.name}`}
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); editForm.resetFields(); }}
        onOk={handleEditSubmit}
        okText={t('common.save')}
        confirmLoading={editSubmitting}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('webhook.name')}
            rules={[
              { required: true, message: t('webhook.nameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="authType" label={t('webhook.authType')}>
            <Select placeholder={t('webhook.selectAuthType')} allowClear>
              <Select.Option value="none">{t('webhook.authNone')}</Select.Option>
              <Select.Option value="signature">{t('webhook.authSignature')}</Select.Option>
              <Select.Option value="apiKey">{t('webhook.authApiKey')}</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.authType !== cur.authType}>
            {({ getFieldValue }) => {
              const authType = getFieldValue('authType');
              if (authType === 'signature') {
                return (
                  <Form.Item
                    name={['authConfig', 'secret']}
                    label={t('webhook.secret')}
                    rules={[{ max: 500, message: t('common.maxLength', { max: 500 }) }]}
                  >
                    <Input.Password placeholder={t('webhook.secretPlaceholder')} maxLength={500} />
                  </Form.Item>
                );
              }
              if (authType === 'apiKey') {
                return (
                  <>
                    <Form.Item
                      name={['authConfig', 'headerName']}
                      label={t('webhook.headerName')}
                      rules={[{ max: 255, message: t('common.maxLength', { max: 255 }) }]}
                    >
                      <Input placeholder="X-API-Key" maxLength={255} />
                    </Form.Item>
                    <Form.Item
                      name={['authConfig', 'apiKey']}
                      label={t('webhook.apiKey')}
                      rules={[{ max: 500, message: t('common.maxLength', { max: 500 }) }]}
                    >
                      <Input.Password placeholder={t('webhook.apiKeyPlaceholder')} maxLength={500} />
                    </Form.Item>
                  </>
                );
              }
              return null;
            }}
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default WebhooksPage
