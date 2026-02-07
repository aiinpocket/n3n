import React, { useEffect, useState } from 'react'
import { Modal, Form, Input, Select, Button, Table, Space, Tag, message, Popconfirm, Typography, Avatar, Tooltip } from 'antd'
import { UserOutlined, MailOutlined, DeleteOutlined, ShareAltOutlined, EyeOutlined, EditOutlined, CrownOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { flowShareApi, FlowShare, ShareFlowRequest } from '../../api/flowShare'

const { Text } = Typography
const { Option } = Select

interface FlowShareModalProps {
  visible: boolean
  flowId: string
  flowName: string
  onClose: () => void
}

const FlowShareModal: React.FC<FlowShareModalProps> = ({
  visible,
  flowId,
  flowName,
  onClose
}) => {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const [shares, setShares] = useState<FlowShare[]>([])
  const [loading, setLoading] = useState(false)
  const [sharing, setSharing] = useState(false)

  const fetchShares = async () => {
    if (!flowId) return
    setLoading(true)
    try {
      const data = await flowShareApi.getShares(flowId)
      setShares(data)
    } catch {
      message.error(t('share.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (visible && flowId) {
      fetchShares()
    }
  }, [visible, flowId])

  useEffect(() => {
    if (!visible) {
      form.resetFields()
    }
  }, [visible, form])

  const handleShare = async () => {
    try {
      const values = await form.validateFields()
      setSharing(true)

      const request: ShareFlowRequest = {
        email: values.email,
        permission: values.permission
      }

      await flowShareApi.share(flowId, request)
      message.success(t('share.success'))
      form.resetFields()
      fetchShares()
    } catch (error) {
      if (error instanceof Error) {
        message.error(t('share.shareFailed'))
      }
    } finally {
      setSharing(false)
    }
  }

  const handleUpdatePermission = async (shareId: string, permission: string) => {
    try {
      await flowShareApi.updatePermission(flowId, shareId, permission)
      message.success(t('share.permissionUpdated'))
      fetchShares()
    } catch {
      message.error(t('share.updateFailed'))
    }
  }

  const handleRemoveShare = async (shareId: string) => {
    try {
      await flowShareApi.removeShare(flowId, shareId)
      message.success(t('share.removed'))
      fetchShares()
    } catch {
      message.error(t('share.removeFailed'))
    }
  }

  const columns = [
    {
      title: t('share.user'),
      key: 'user',
      render: (_: unknown, record: FlowShare) => (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} />
          <div>
            <div>{record.userName || record.invitedEmail}</div>
            {record.userEmail && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.userEmail}
              </Text>
            )}
            {record.pending && (
              <Tag color="orange" style={{ marginLeft: 8 }}>{t('share.pending')}</Tag>
            )}
          </div>
        </Space>
      )
    },
    {
      title: t('share.permission'),
      key: 'permission',
      width: 120,
      render: (_: unknown, record: FlowShare) => (
        <Tooltip title={record.pending ? t('share.pendingCannotChange') : undefined}>
          <Select
            value={record.permission}
            size="small"
            style={{ width: 100 }}
            disabled={record.pending}
            onChange={(value) => handleUpdatePermission(record.id, value)}
          >
            <Option value="view">{t('share.view')}</Option>
            <Option value="edit">{t('share.edit')}</Option>
            <Option value="admin">{t('share.admin')}</Option>
          </Select>
        </Tooltip>
      )
    },
    {
      title: t('share.sharedBy'),
      dataIndex: 'sharedByName',
      key: 'sharedBy',
      render: (name: string) => name || '-'
    },
    {
      title: t('share.actions'),
      key: 'actions',
      width: 80,
      render: (_: unknown, record: FlowShare) => (
        <Popconfirm
          title={t('share.removeConfirm')}
          onConfirm={() => handleRemoveShare(record.id)}
          okText={t('share.remove')}
          cancelText={t('common.cancel')}
          okButtonProps={{ danger: true }}
        >
          <Button type="link" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      )
    }
  ]

  return (
    <Modal
      title={
        <Space>
          <ShareAltOutlined />
          <span>{t('share.shareFlow')}: {flowName}</span>
        </Space>
      }
      open={visible}
      onCancel={onClose}
      footer={null}
      width={700}
    >
      {/* Add new share form */}
      <div style={{ marginBottom: 24, padding: 16, background: 'var(--color-bg-elevated)', borderRadius: 8 }}>
        <Form
          form={form}
          layout="inline"
          style={{ width: '100%' }}
        >
          <Form.Item
            name="email"
            rules={[
              { required: true, message: t('share.emailRequired') },
              { type: 'email', message: t('share.emailInvalid') }
            ]}
            style={{ flex: 1, marginRight: 8 }}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder={t('share.emailPlaceholder')}
            />
          </Form.Item>

          <Form.Item
            name="permission"
            initialValue="view"
            style={{ width: 160 }}
          >
            <Select>
              <Option value="view">
                <Tooltip title={t('share.viewDesc')} placement="left">
                  <Space><EyeOutlined />{t('share.view')}</Space>
                </Tooltip>
              </Option>
              <Option value="edit">
                <Tooltip title={t('share.editDesc')} placement="left">
                  <Space><EditOutlined />{t('share.edit')}</Space>
                </Tooltip>
              </Option>
              <Option value="admin">
                <Tooltip title={t('share.adminDesc')} placement="left">
                  <Space><CrownOutlined />{t('share.admin')}</Space>
                </Tooltip>
              </Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              onClick={handleShare}
              loading={sharing}
            >
              {t('share.share')}
            </Button>
          </Form.Item>
        </Form>
      </div>

      {/* Permission explanation */}
      <div style={{ marginBottom: 16 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          <strong>{t('share.view')}</strong>: {t('share.viewDesc')} |
          <strong> {t('share.edit')}</strong>: {t('share.editDesc')} |
          <strong> {t('share.admin')}</strong>: {t('share.adminDesc')}
        </Text>
      </div>

      {/* Shares list */}
      <Table
        columns={columns}
        dataSource={shares}
        rowKey="id"
        loading={loading}
        pagination={false}
        size="small"
        locale={{ emptyText: t('share.noShares') }}
      />
    </Modal>
  )
}

export default FlowShareModal
