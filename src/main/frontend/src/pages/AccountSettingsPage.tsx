import { useState, useEffect, useCallback } from 'react'
import { Card, Form, Input, Button, Typography, Divider, Descriptions, Tag, Alert, Space, Modal, Table } from 'antd'
import { message } from '../utils/feedback'
import { LockOutlined, UserOutlined, MailOutlined, SafetyCertificateOutlined, EditOutlined, SafetyOutlined, CheckCircleOutlined, MedicineBoxOutlined, HistoryOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { authApi } from '../api/auth'
import { extractApiError } from '../utils/errorMessages'
import { securityApi, SecurityStatus } from '../api/security'
import { activityApi, UserActivity } from '../api/activity'
import MemorySettings from '../components/settings/MemorySettings'
import logger from '../utils/logger'

const { Title, Text } = Typography

export default function AccountSettingsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const [form] = Form.useForm()
  const [profileForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [editingProfile, setEditingProfile] = useState(false)
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null)
  const [securityError, setSecurityError] = useState(false)
  const [emergencyModalOpen, setEmergencyModalOpen] = useState(false)
  const [emergencyLoading, setEmergencyLoading] = useState(false)
  const [emergencyForm] = Form.useForm()
  const [loginActivities, setLoginActivities] = useState<UserActivity[]>([])
  const [loginActivityLoading, setLoginActivityLoading] = useState(false)

  const fetchLoginActivities = useCallback(async () => {
    setLoginActivityLoading(true)
    try {
      const result = await activityApi.listMy(0, 5, 'LOGIN')
      setLoginActivities(result.content)
    } catch (err) {
      logger.warn('Failed to fetch login activities:', err)
    } finally {
      setLoginActivityLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLoginActivities()
  }, [fetchLoginActivities])

  useEffect(() => {
    let cancelled = false
    securityApi.getStatus()
      .then((data) => { if (!cancelled) setSecurityStatus(data) })
      .catch(() => { if (!cancelled) setSecurityError(true) })
    return () => { cancelled = true }
  }, [])

  const handleUpdateProfile = async (values: { name: string }) => {
    setProfileLoading(true)
    try {
      const updatedUser = await authApi.updateProfile(values.name)
      useAuthStore.setState({ user: updatedUser })
      message.success(t('account.profileUpdated'))
      setEditingProfile(false)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('account.profileUpdateFailed')))
    } finally {
      setProfileLoading(false)
    }
  }

  const handleEmergencyRestore = async (values: { recoveryKeyPhrase: string; permanentPassword: string }) => {
    setEmergencyLoading(true)
    try {
      const result = await securityApi.emergencyRestore(values.recoveryKeyPhrase, values.permanentPassword)
      if (result.success) {
        message.success(t('account.emergencyRestoreSuccess'))
        setEmergencyModalOpen(false)
        emergencyForm.resetFields()
        // Refresh security status
        const status = await securityApi.getStatus()
        setSecurityStatus(status)
      } else {
        message.error(result.message || t('account.emergencyRestoreFailed'))
      }
    } catch (error: unknown) {
      message.error(extractApiError(error, t('account.emergencyRestoreFailed')))
    } finally {
      setEmergencyLoading(false)
    }
  }

  const handleChangePassword = async (values: { currentPassword: string; newPassword: string }) => {
    setLoading(true)
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword)
      message.success(t('account.passwordChanged'))
      form.resetFields()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('account.passwordChangeFailed')))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      <Title level={3} style={{ color: 'var(--color-text-primary)', marginBottom: 24 }}>
        <UserOutlined style={{ marginRight: 8 }} />
        {t('account.title')}
      </Title>

      {/* Profile Info */}
      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={5} style={{ color: 'var(--color-text-primary)', margin: 0 }}>
            {t('account.profileInfo')}
          </Title>
          {!editingProfile && (
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => {
                setEditingProfile(true)
                profileForm.setFieldsValue({ name: user?.name })
              }}
            >
              {t('account.editProfile')}
            </Button>
          )}
        </div>

        {editingProfile ? (
          <Form
            form={profileForm}
            layout="vertical"
            onFinish={handleUpdateProfile}
            style={{ maxWidth: 400 }}
          >
            <Descriptions column={1} styles={{ label: { color: 'var(--color-text-secondary)' } }}>
              <Descriptions.Item label={<><MailOutlined style={{ marginRight: 4 }} />{t('auth.email')}</>}>
                {user?.email}
              </Descriptions.Item>
            </Descriptions>
            <Form.Item
              name="name"
              label={t('account.displayName')}
              rules={[
                { required: true, message: t('account.displayNamePlaceholder') },
                { max: 100, message: t('common.maxLength', { max: 100 }) },
              ]}
              style={{ marginTop: 16 }}
            >
              <Input placeholder={t('account.displayNamePlaceholder')} maxLength={100} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button type="primary" htmlType="submit" loading={profileLoading} style={{ marginRight: 8 }}>
                {t('account.updateProfile')}
              </Button>
              <Button onClick={() => setEditingProfile(false)}>
                {t('common.cancel')}
              </Button>
            </Form.Item>
          </Form>
        ) : (
          <Descriptions column={1} styles={{ label: { color: 'var(--color-text-secondary)' } }}>
            <Descriptions.Item label={<><MailOutlined style={{ marginRight: 4 }} />{t('auth.email')}</>}>
              {user?.email}
            </Descriptions.Item>
            <Descriptions.Item label={<><UserOutlined style={{ marginRight: 4 }} />{t('auth.name')}</>}>
              {user?.name}
            </Descriptions.Item>
            <Descriptions.Item label={<><SafetyCertificateOutlined style={{ marginRight: 4 }} />{t('account.roles')}</>}>
              {user?.roles?.map(role => (
                <Tag key={role} color={role === 'ADMIN' ? 'gold' : 'blue'}>{role}</Tag>
              ))}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      {/* Security Status */}
      {securityError && (
        <Card style={{ marginBottom: 24 }}>
          <Alert type="warning" showIcon title={t('account.securityLoadFailed')} />
        </Card>
      )}
      {securityStatus && (
        <Card style={{ marginBottom: 24 }}>
          <Title level={5} style={{ color: 'var(--color-text-primary)', marginBottom: 16 }}>
            <SafetyOutlined style={{ marginRight: 8 }} />
            {t('account.securityStatus')}
          </Title>
          <Divider style={{ margin: '8px 0 16px' }} />

          <Space orientation="vertical" style={{ width: '100%' }} size="middle">
            {/* Recovery Key 強制備份已廢除：金鑰持久化在資料庫，這裡只報加密系統正常與否 */}
            {!securityStatus.keyMismatch && (
              <Alert
                type="success"
                showIcon
                icon={<CheckCircleOutlined />}
                title={t('account.encryptionHealthy')}
                description={t('account.encryptionHealthyDesc')}
              />
            )}

            {/* Key Mismatch Warning */}
            {securityStatus.keyMismatch && (
              <Alert
                type="error"
                showIcon
                title={t('account.keyMismatch')}
                description={t('account.keyMismatchDesc')}
                action={
                  <Space>
                    <Button size="small" danger onClick={() => navigate('/credentials')}>
                      {t('account.resolveKeyMismatch')}
                    </Button>
                    <Button
                      size="small"
                      icon={<MedicineBoxOutlined />}
                      onClick={() => setEmergencyModalOpen(true)}
                    >
                      {t('account.emergencyRestore')}
                    </Button>
                  </Space>
                }
              />
            )}

            {/* Encryption Info */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Text type="secondary">{t('account.encryptionVersion')}: </Text>
              <Tag color="blue">v{securityStatus.currentKeyVersion}</Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>AES-256-GCM</Text>
            </div>
          </Space>
        </Card>
      )}

      {/* Change Password */}
      <Card>
        <Title level={5} style={{ color: 'var(--color-text-primary)', marginBottom: 16 }}>
          <LockOutlined style={{ marginRight: 8 }} />
          {t('account.changePassword')}
        </Title>
        <Divider style={{ margin: '8px 0 24px' }} />

        <Form
          form={form}
          layout="vertical"
          onFinish={handleChangePassword}
          style={{ maxWidth: 400 }}
        >
          <Form.Item
            name="currentPassword"
            label={t('account.currentPassword')}
            rules={[{ required: true, message: t('account.currentPasswordRequired') }]}
          >
            <Input.Password placeholder={t('account.currentPasswordPlaceholder')} />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label={t('account.newPassword')}
            rules={[
              { required: true, message: t('account.newPasswordRequired') },
              { min: 12, message: t('account.passwordMinLength') },
              { max: 128, message: t('common.maxLength', { max: 128 }) },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve()
                  let criteria = 0
                  if (/[A-Z]/.test(value)) criteria++
                  if (/[a-z]/.test(value)) criteria++
                  if (/\d/.test(value)) criteria++
                  if (/[^a-zA-Z0-9]/.test(value)) criteria++
                  return criteria >= 3 ? Promise.resolve() : Promise.reject(new Error(t('account.passwordComplexity')))
                },
              },
            ]}
          >
            <Input.Password placeholder={t('account.newPasswordPlaceholder')} maxLength={128} />
          </Form.Item>

          <Form.Item
            name="confirmNewPassword"
            label={t('account.confirmNewPassword')}
            dependencies={['newPassword']}
            rules={[
              { required: true, message: t('account.confirmPasswordRequired') },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error(t('auth.passwordMismatch')))
                },
              }),
            ]}
          >
            <Input.Password placeholder={t('account.confirmNewPasswordPlaceholder')} />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              {t('account.updatePassword')}
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Recent Login Activity */}
      <Card style={{ marginTop: 24 }}>
        <Title level={5} style={{ color: 'var(--color-text-primary)', marginBottom: 16 }}>
          <HistoryOutlined style={{ marginRight: 8 }} />
          {t('account.recentLoginActivity')}
        </Title>
        <Divider style={{ margin: '8px 0 16px' }} />
        <Table
          dataSource={loginActivities}
          rowKey="id"
          loading={loginActivityLoading}
          pagination={false}
          size="small"
          scroll={{ x: 400 }}
          columns={[
            {
              title: t('activity.time'),
              dataIndex: 'createdAt',
              key: 'createdAt',
              width: 180,
              render: (val: string) => new Date(val).toLocaleString(),
            },
            {
              title: t('account.ipAddress'),
              dataIndex: 'ipAddress',
              key: 'ipAddress',
              width: 150,
              render: (val: string | null) => val || '-',
            },
            {
              title: t('common.status'),
              key: 'status',
              width: 100,
              render: () => <Tag color="green">{t('common.success')}</Tag>,
            },
          ]}
          locale={{ emptyText: t('execution.noData') }}
        />
        <div style={{ textAlign: 'right', marginTop: 8 }}>
          <Button type="link" size="small" onClick={() => navigate('/activities')}>
            {t('account.viewAllActivity')}
          </Button>
        </div>
      </Card>

      {/* AI Memory */}
      <MemorySettings />

      {/* Emergency Restore Modal */}
      <Modal
        title={
          <Space>
            <MedicineBoxOutlined style={{ color: 'var(--color-error)' }} />
            <span>{t('account.emergencyRestoreTitle')}</span>
          </Space>
        }
        open={emergencyModalOpen}
        onCancel={() => { setEmergencyModalOpen(false); emergencyForm.resetFields() }}
        footer={null}
        forceRender
      >
        <Alert
          type="warning"
          showIcon
          title={t('account.emergencyRestoreWarning')}
          description={t('account.emergencyRestoreWarningDesc')}
          style={{ marginBottom: 24 }}
        />
        <Form form={emergencyForm} layout="vertical" onFinish={handleEmergencyRestore}>
          <Form.Item
            name="recoveryKeyPhrase"
            label={t('account.recoveryKeyLabel')}
            rules={[
              { required: true, message: t('recovery.pleaseEnterKey') },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve()
                  const words = value.trim().split(/\s+/)
                  return words.length === 12
                    ? Promise.resolve()
                    : Promise.reject(new Error(t('recovery.mustBe12Words')))
                },
              },
            ]}
          >
            <Input.TextArea
              rows={2}
              placeholder={t('recovery.verifyPlaceholder')}
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item
            name="permanentPassword"
            label={t('account.currentPassword')}
            rules={[{ required: true, message: t('account.currentPasswordRequired') }]}
          >
            <Input.Password placeholder={t('account.currentPasswordPlaceholder')} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setEmergencyModalOpen(false); emergencyForm.resetFields() }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" danger htmlType="submit" loading={emergencyLoading}>
                {t('account.emergencyRestoreAction')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
