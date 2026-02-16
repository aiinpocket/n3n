import { useEffect, useState, useCallback, useRef } from 'react'
import { Table, Button, Tag, Space, Modal, Form, Input, Select, message, Typography, Card, Tooltip, Popconfirm, Spin, Descriptions } from 'antd'
import {
  UserAddOutlined,
  ReloadOutlined,
  LockOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StopOutlined,
  SearchOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import { adminApi, type AdminUser } from '../api/admin'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Title } = Typography

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const { user: currentUser } = useAuthStore()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [rolesModalOpen, setRolesModalOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<AdminUser | null>(null)
  const [createForm] = Form.useForm()
  const [rolesForm] = Form.useForm()
  const [createLoading, setCreateLoading] = useState(false)
  const [rolesLoading, setRolesLoading] = useState(false)
  const [searchText, setSearchText] = useState('')
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [detailUser, setDetailUser] = useState<AdminUser | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const loadUsers = useCallback(async (p = 0, search?: string) => {
    setLoading(true)
    try {
      const q = search !== undefined ? search : searchText
      const res = await adminApi.listUsers(p, 20, q || undefined)
      setUsers(res.content || [])
      setTotal(res.totalElements || 0)
      setPage(p)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t, searchText])

  const handleSearch = useCallback((value: string) => {
    setSearchText(value)
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    searchTimerRef.current = setTimeout(() => {
      loadUsers(0, value)
    }, 300)
  }, [loadUsers])

  useEffect(() => {
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    }
  }, [])

  useEffect(() => { loadUsers() }, [loadUsers])

  const handleCreateUser = async (values: { email: string; name: string; password?: string; roles: string[] }) => {
    setCreateLoading(true)
    try {
      await adminApi.createUser(values)
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
      await adminApi.updateStatus(userId, status)
      message.success(t('admin.statusUpdated'))
      loadUsers(page)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.updateFailed')))
    }
  }

  const handleUpdateRoles = async (values: { roles: string[] }) => {
    if (!selectedUser) return
    setRolesLoading(true)
    try {
      await adminApi.updateRoles(selectedUser.id, values.roles)
      message.success(t('admin.rolesUpdated'))
      setRolesModalOpen(false)
      loadUsers(page)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.updateFailed')))
    } finally {
      setRolesLoading(false)
    }
  }

  const handleViewUser = async (userId: string) => {
    setDetailLoading(true)
    setDetailModalOpen(true)
    try {
      const user = await adminApi.getUser(userId)
      setDetailUser(user)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.loadFailed')))
      setDetailModalOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const handleResetPassword = async (userId: string) => {
    Modal.confirm({
      title: t('admin.confirmResetPassword'),
      content: t('admin.resetPasswordDesc'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await adminApi.resetPassword(userId)
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
    deleted: 'error',
  }

  const columns = [
    {
      title: t('admin.userName'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AdminUser) => (
        <div
          style={{ cursor: 'pointer' }}
          onClick={() => handleViewUser(record.id)}
        >
          <div style={{ fontWeight: 500, color: 'var(--color-primary)' }}>{name}</div>
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
            <Tooltip title={t('admin.viewDetail')}>
              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => handleViewUser(record.id)}
                aria-label={t('admin.viewDetail')}
              />
            </Tooltip>
            {!isSelf && (
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
                  aria-label={t('admin.editRoles')}
                />
              </Tooltip>
            )}
            {!isSelf && (
              record.status === 'active' ? (
                <Popconfirm
                  title={t('admin.confirmSuspend')}
                  onConfirm={() => handleStatusChange(record.id, 'suspended')}
                  okText={t('common.confirm')}
                  cancelText={t('common.cancel')}
                >
                  <Tooltip title={t('admin.suspend')}>
                    <Button
                      type="text"
                      size="small"
                      icon={<StopOutlined />}
                      aria-label={t('admin.suspend')}
                    />
                  </Tooltip>
                </Popconfirm>
              ) : (
                <Popconfirm
                  title={t('admin.confirmActivate')}
                  onConfirm={() => handleStatusChange(record.id, 'active')}
                  okText={t('common.confirm')}
                  cancelText={t('common.cancel')}
                >
                  <Tooltip title={t('admin.activate')}>
                    <Button
                      type="text"
                      size="small"
                      icon={<CheckCircleOutlined />}
                      aria-label={t('admin.activate')}
                    />
                  </Tooltip>
                </Popconfirm>
              )
            )}
            {!isSelf && (
              <Tooltip title={t('admin.resetPassword')}>
                <Button
                  type="text"
                  size="small"
                  icon={<LockOutlined />}
                  onClick={() => handleResetPassword(record.id)}
                  aria-label={t('admin.resetPassword')}
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
        <Input
          prefix={<SearchOutlined />}
          placeholder={t('admin.searchPlaceholder')}
          value={searchText}
          onChange={(e) => handleSearch(e.target.value)}
          allowClear
          style={{ marginBottom: 16, maxWidth: 400 }}
        />
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
          scroll={{ x: 800 }}
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
            { max: 255, message: t('common.maxLength', { max: 255 }) },
          ]}>
            <Input placeholder={t('admin.emailPlaceholder')} maxLength={255} />
          </Form.Item>
          <Form.Item name="name" label={t('auth.name')} rules={[
            { required: true, message: t('auth.nameRequired') },
            { min: 2, message: t('common.minLength', { min: 2 }) },
            { max: 100, message: t('common.maxLength', { max: 100 }) },
          ]}>
            <Input placeholder={t('admin.namePlaceholder')} maxLength={100} />
          </Form.Item>
          <Form.Item name="password" label={t('admin.password')} rules={[
            { min: 12, message: t('auth.passwordTooShort') },
            { max: 128, message: t('common.maxLength', { max: 128 }) },
          ]}>
            <Input.Password placeholder={t('admin.passwordPlaceholder')} maxLength={128} />
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

      {/* User Detail Modal */}
      <Modal
        title={t('admin.userDetail')}
        open={detailModalOpen}
        onCancel={() => { setDetailModalOpen(false); setDetailUser(null) }}
        footer={[
          <Button key="close" onClick={() => { setDetailModalOpen(false); setDetailUser(null) }}>
            {t('common.close')}
          </Button>,
        ]}
        width={560}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : detailUser && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('admin.userName')}>{detailUser.name}</Descriptions.Item>
            <Descriptions.Item label={t('auth.email')}>{detailUser.email}</Descriptions.Item>
            <Descriptions.Item label={t('admin.roles')}>
              <Space size={4}>
                {detailUser.roles?.map(role => (
                  <Tag key={role} color={role === 'ADMIN' ? 'gold' : 'blue'}>{role}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              <Tag color={statusColors[detailUser.status] || 'default'}>
                {t(`admin.status.${detailUser.status}`, { defaultValue: detailUser.status })}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('admin.emailVerified')}>
              {detailUser.emailVerified ? (
                <Tag color="success">{t('common.yes')}</Tag>
              ) : (
                <Tag color="warning">{t('common.no')}</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('common.createdAt')}>
              {detailUser.createdAt ? new Date(detailUser.createdAt).toLocaleString(getLocale()) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('admin.lastLogin')}>
              {detailUser.lastLoginAt ? new Date(detailUser.lastLoginAt).toLocaleString(getLocale()) : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* Edit Roles Modal */}
      <Modal
        title={t('admin.editRoles')}
        open={rolesModalOpen}
        onCancel={() => { setRolesModalOpen(false); rolesForm.resetFields() }}
        footer={null}
        destroyOnClose
      >
        <Form form={rolesForm} layout="vertical" onFinish={handleUpdateRoles}>
          <Form.Item name="roles" label={t('admin.roles')} rules={[
            { required: true, message: t('admin.rolesRequired'), type: 'array' },
          ]}>
            <Select mode="multiple" options={[
              { value: 'USER', label: 'USER' },
              { value: 'ADMIN', label: 'ADMIN' },
            ]} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setRolesModalOpen(false); rolesForm.resetFields() }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={rolesLoading}>
                {t('common.save')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
