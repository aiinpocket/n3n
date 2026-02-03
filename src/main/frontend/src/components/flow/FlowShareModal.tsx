import React, { useEffect, useState } from 'react'
import { Modal, Form, Input, Select, Button, Table, Space, Tag, message, Popconfirm, Typography, Avatar } from 'antd'
import { UserOutlined, MailOutlined, DeleteOutlined, ShareAltOutlined } from '@ant-design/icons'
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
      message.error('無法載入分享清單')
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
      message.success('分享成功')
      form.resetFields()
      fetchShares()
    } catch (error) {
      if (error instanceof Error) {
        message.error(`分享失敗: ${error.message}`)
      }
    } finally {
      setSharing(false)
    }
  }

  const handleUpdatePermission = async (shareId: string, permission: string) => {
    try {
      await flowShareApi.updatePermission(flowId, shareId, permission)
      message.success('權限已更新')
      fetchShares()
    } catch {
      message.error('更新失敗')
    }
  }

  const handleRemoveShare = async (shareId: string) => {
    try {
      await flowShareApi.removeShare(flowId, shareId)
      message.success('已移除分享')
      fetchShares()
    } catch {
      message.error('移除失敗')
    }
  }

  const columns = [
    {
      title: '用戶',
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
              <Tag color="orange" style={{ marginLeft: 8 }}>待接受</Tag>
            )}
          </div>
        </Space>
      )
    },
    {
      title: '權限',
      key: 'permission',
      width: 120,
      render: (_: unknown, record: FlowShare) => (
        <Select
          value={record.permission}
          size="small"
          style={{ width: 100 }}
          onChange={(value) => handleUpdatePermission(record.id, value)}
        >
          <Option value="view">檢視</Option>
          <Option value="edit">編輯</Option>
          <Option value="admin">管理</Option>
        </Select>
      )
    },
    {
      title: '分享者',
      dataIndex: 'sharedByName',
      key: 'sharedBy',
      render: (name: string) => name || '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      render: (_: unknown, record: FlowShare) => (
        <Popconfirm
          title="確定要移除此分享嗎？"
          onConfirm={() => handleRemoveShare(record.id)}
          okText="移除"
          cancelText="取消"
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
          <span>分享流程: {flowName}</span>
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
              { required: true, message: '請輸入 Email' },
              { type: 'email', message: '請輸入有效的 Email' }
            ]}
            style={{ flex: 1, marginRight: 8 }}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="輸入用戶 Email"
            />
          </Form.Item>

          <Form.Item
            name="permission"
            initialValue="view"
            style={{ width: 120 }}
          >
            <Select>
              <Option value="view">檢視</Option>
              <Option value="edit">編輯</Option>
              <Option value="admin">管理</Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              onClick={handleShare}
              loading={sharing}
            >
              分享
            </Button>
          </Form.Item>
        </Form>
      </div>

      {/* Permission explanation */}
      <div style={{ marginBottom: 16 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          <strong>檢視</strong>: 可查看流程但不能修改 |
          <strong> 編輯</strong>: 可修改流程內容 |
          <strong> 管理</strong>: 可管理分享設定
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
        locale={{ emptyText: '尚未分享給任何人' }}
      />
    </Modal>
  )
}

export default FlowShareModal
