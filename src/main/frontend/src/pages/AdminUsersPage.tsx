import { useEffect, useState, useCallback } from 'react'
import { Table, Button, Tag, Space, Modal, Form, Input, Select, message, Typography, Card, Tooltip } from 'antd'
import {
  UserAddOutlined,
  ReloadOutlined,
  LockOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import apiClient from '../api/client'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Title } = Typography

interface AdminUser {
  id: string
  email: string
  name: string
  status: string
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
  roles: string[]
}

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const { user: currentUser } = useAuthStore()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [rolesModalOpen, setRolesModalOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)
  const [createForm] = Form.useForm()
  const [rolesForm] = Form.useForm()
  const [createLoading, setCreateLoading] = useState(false)

  const loadUsers = useCallback(async (p = 0) => {
    setLoading(true)
    try {
      const res = await apiClient.get('/admin/users', { params: { page: p, size: 20 } })
      setUsers(res.data.content || [])
      setTotal(res.data.totalElements || 0)
      setPage(p)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => { loadUsers() }, [loadUsers])

  const handleCreateUser = async (values: { email: string; name: string; password?: string; roles: string[] }) => {
    setCreateLoading(true)
    try {
      await apiClient.post('/admin/users', values)
      message.success(t('admin.userCreated'))
      setCreateModalOpen(false)
      createForm.resetFields()
      loadUsers(page)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.createFailed')))
    } finally {
      setCreateLoading(false)
    }
  }

  const handleStatusChange = async (userId: string, status: string) => {
    try {
      await apiClient.patch(`/admin/users/${userId}/status`, null, { params: { status } })
      message.success(t('admin.statusUpdated'))
      loadUsers(page)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.updateFailed')))
    }
  }

  const handleUpdateRoles = async (values: { roles: string[] }) => {
    if (!selectedUser) return
    try {
      await apiClient.put(`/admin/users/${selectedUser.id}/roles`, values.roles)
      message.success(t('admin.rolesUpdated'))
      setRolesModalOpen(false)
      loadUsers(page)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.updateFailed')))
    }
  }

  const handleResetPassword = async (userId: string) => {
    Modal.confirm({
      title: t('admin.confirmResetPassword'),
      content: t('admin.resetPasswordDesc'),
      onOk: async () => {
        try {
          await apiClient.post(`/admin/users/${userId}/reset-password`)
          message.success(t('admin.passwordReset'))
        } catch (error: unknown) {
          message.error(extractApiError(error, t('common.updateFailed')))
        }
      },
    })
  }

  const statusColors: Record<string, string> = {
    active: 'success',
    suspended: 'warning',
    blocked: 'error',
  }

  const columns = [
    {
      title: t('admin.userName'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AdminUser) => (
        <div>
          <div style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>{name}</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>{record.email}</div>
        </div>
      ),
    },
    {
      title: t('admin.roles'),
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => (
        <Space size={4}>
          {roles?.map(role => (
            <Tag key={role} color={role === 'ADMIN' ? 'gold' : 'blue'}>{role}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={statusColors[status] || 'default'}>{t(`admin.status.${status}`, { defaultValue: status })}</Tag>
      ),
    },
    {
      title: t('admin.lastLogin'),
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (val: string | null) => val ? new Date(val).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val: string) => val ? new Date(val).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: AdminUser) => {
        const isSelf = currentUser?.id === record.id
        return (
          <Space size="small">
            <Tooltip title={t('admin.editRoles')}>
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                onClick={() => {
                  setSelectedUser(record)
                  rolesForm.setFieldsValue({ roles: record.roles })
                  setRolesModalOpen(true)
                }}
              />
            </Tooltip>
            {!isSelf && (
              record.status === 'active' ? (
                <Tooltip title={t('admin.suspend')}>
                  <Button
                    type="text"
                    size="small"
                    icon={<StopOutlined />}
                    onClick={() => handleStatusChange(record.id, 'suspended')}
                  />
                </Tooltip>
              ) : (
                <Tooltip title={t('admin.activate')}>
                  <Button
                    type="text"
                    size="small"
                    icon={<CheckCircleOutlined />}
                    onClick={() => handleStatusChange(record.id, 'active')}
                  />
                </Tooltip>
              )
            )}
            {!isSelf && (
              <Tooltip title={t('admin.resetPassword')}>
                <Button
                  type="text"
                  size="small"
                  icon={<LockOutlined />}
                  onClick={() => handleResetPassword(record.id)}
                />
              </Tooltip>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ color: 'var(--color-text-primary)', margin: 0 }}>
          {t('admin.title')}
        </Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => loadUsers(page)}>
            {t('common.refresh')}
          </Button>
          <Button type="primary" icon={<UserAddOutlined />} onClick={() => setCreateModalOpen(true)}>
            {t('admin.createUser')}
          </Button>
        </Space>
      </div>

      <Card>
        <Table
          dataSource={users}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page + 1,
            total,
            pageSize: 20,
            onChange: (p) => loadUsers(p - 1),
            showTotal: (total) => t('common.total', { count: total }),
          }}
        />
      </Card>

      {/* Create User Modal */}
      <Modal
        title={t('admin.createUser')}
        open={createModalOpen}
        onCancel={() => { setCreateModalOpen(false); createForm.resetFields() }}
        footer={null}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreateUser}>
          <Form.Item name="email" label={t('auth.email')} rules={[
            { required: true, message: t('auth.emailRequired') },
            { type: 'email', message: t('auth.emailInvalid') },
          ]}>
            <Input placeholder="user@example.com" />
          </Form.Item>
          <Form.Item name="name" label={t('auth.name')} rules={[
            { required: true, message: t('auth.nameRequired') },
          ]}>
            <Input placeholder={t('admin.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="password" label={t('admin.password')}>
            <Input.Password placeholder={t('admin.passwordPlaceholder')} />
          </Form.Item>
          <Form.Item name="roles" label={t('admin.roles')} initialValue={['USER']}>
            <Select mode="multiple" options={[
              { value: 'USER', label: 'USER' },
              { value: 'ADMIN', label: 'ADMIN' },
            ]} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setCreateModalOpen(false); createForm.resetFields() }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={createLoading}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Roles Modal */}
      <Modal
        title={t('admin.editRoles')}
        open={rolesModalOpen}
        onCancel={() => setRolesModalOpen(false)}
        footer={null}
      >
        <Form form={rolesForm} layout="vertical" onFinish={handleUpdateRoles}>
          <Form.Item name="roles" label={t('admin.roles')}>
            <Select mode="multiple" options={[
              { value: 'USER', label: 'USER' },
              { value: 'ADMIN', label: 'ADMIN' },
            ]} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setRolesModalOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit">
                {t('common.save')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
