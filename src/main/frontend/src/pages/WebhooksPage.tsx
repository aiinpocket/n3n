import { useEffect, useState } from 'react'
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
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useWebhookStore } from '../stores/webhookStore'
import { useFlowListStore } from '../stores/flowListStore'
import type { Webhook, CreateWebhookRequest } from '../api/webhook'
import { extractApiError } from '../utils/errorMessages'

const { Text, Paragraph } = Typography

const WebhooksPage: React.FC = () => {
  const { t } = useTranslation()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [form] = Form.useForm()

  const {
    webhooks,
    isLoading,
    fetchWebhooks,
    createWebhook,
    activateWebhook,
    deactivateWebhook,
    deleteWebhook,
  } = useWebhookStore()

  const { flows, fetchFlows } = useFlowListStore()

  useEffect(() => {
    fetchWebhooks()
    fetchFlows()
  }, [fetchWebhooks, fetchFlows])

  const handleCreate = async (values: CreateWebhookRequest) => {
    try {
      await createWebhook(values)
      message.success(t('webhook.createSuccess'))
      setIsModalOpen(false)
      form.resetFields()
    } catch (error) {
      message.error(extractApiError(error, t('common.createFailed')))
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

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success(t('common.copied'))
  }

  const columns: ColumnsType<Webhook> = [
    {
      title: t('webhook.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Space>
          <ApiOutlined />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: t('webhook.method'),
      dataIndex: 'method',
      key: 'method',
      width: 100,
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
      width: 150,
      render: (_: unknown, record: Webhook) => (
        <Space>
          <Tooltip title={record.isActive ? t('webhook.deactivate') : t('webhook.activate')}>
            <Button
              type="text"
              icon={record.isActive ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={() => handleToggleActive(record)}
            />
          </Tooltip>
          <Tooltip title={t('webhook.copyUrl')}>
            <Button
              type="text"
              icon={<CopyOutlined />}
              onClick={() => copyToClipboard(record.webhookUrl)}
            />
          </Tooltip>
          <Tooltip title={t('common.delete')}>
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.id)}
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
          <Table
            columns={columns}
            dataSource={webhooks}
            rowKey="id"
            loading={isLoading}
            pagination={{ pageSize: 10 }}
          />
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
              {flows.map((flow) => (
                <Select.Option key={flow.id} value={flow.id}>
                  {flow.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label={t('webhook.name')}
            rules={[{ required: true, message: t('webhook.nameRequired') }]}
          >
            <Input placeholder={t('webhook.namePlaceholder')} />
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
            ]}
            extra={t('webhook.pathHint')}
          >
            <Input
              addonBefore="/webhook/"
              placeholder="my-webhook"
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

          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setIsModalOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit">
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default WebhooksPage
