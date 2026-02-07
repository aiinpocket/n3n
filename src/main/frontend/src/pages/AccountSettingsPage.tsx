import { useState, useEffect } from 'react'
import { Card, Form, Input, Button, message, Typography, Divider, Descriptions, Tag, Alert, Space } from 'antd'
import { LockOutlined, UserOutlined, MailOutlined, SafetyCertificateOutlined, EditOutlined, SafetyOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import apiClient from '../api/client'
import { extractApiError } from '../utils/errorMessages'
import { securityApi, SecurityStatus } from '../api/security'

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

  useEffect(() => {
    securityApi.getStatus().then(setSecurityStatus).catch(() => setSecurityError(true))
  }, [])

  const handleUpdateProfile = async (values: { name: string }) => {
    setProfileLoading(true)
    try {
      const response = await apiClient.put('/auth/profile', { name: values.name })
      useAuthStore.setState({ user: response.data })
      message.success(t('account.profileUpdated'))
      setEditingProfile(false)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('account.profileUpdateFailed')))
    } finally {
      setProfileLoading(false)
    }
  }

  const handleChangePassword = async (values: { currentPassword: string; newPassword: string }) => {
    setLoading(true)
    try {
      await apiClient.post('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
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
            <Descriptions column={1} labelStyle={{ color: 'var(--color-text-secondary)' }}>
              <Descriptions.Item label={<><MailOutlined style={{ marginRight: 4 }} />{t('auth.email')}</>}>
                {user?.email}
              </Descriptions.Item>
            </Descriptions>
            <Form.Item
              name="name"
              label={t('account.displayName')}
              rules={[{ required: true, message: t('account.displayNamePlaceholder') }]}
              style={{ marginTop: 16 }}
            >
              <Input placeholder={t('account.displayNamePlaceholder')} />
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
          <Descriptions column={1} labelStyle={{ color: 'var(--color-text-secondary)' }}>
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
          <Alert type="warning" showIcon message={t('account.securityLoadFailed')} />
        </Card>
      )}
      {securityStatus && (
        <Card style={{ marginBottom: 24 }}>
          <Title level={5} style={{ color: 'var(--color-text-primary)', marginBottom: 16 }}>
            <SafetyOutlined style={{ marginRight: 8 }} />
            {t('account.securityStatus')}
          </Title>
          <Divider style={{ margin: '8px 0 16px' }} />

          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {/* Recovery Key Status */}
            {securityStatus.needsRecoveryKeySetup ? (
              <Alert
                type="warning"
                showIcon
                icon={<WarningOutlined />}
                message={t('account.recoveryKeyNotSet')}
                description={t('account.recoveryKeyNotSetDesc')}
                action={
                  <Button size="small" type="primary" onClick={() => navigate('/credentials')}>
                    {t('account.setupRecoveryKey')}
                  </Button>
                }
              />
            ) : (
              <Alert
                type="success"
                showIcon
                icon={<CheckCircleOutlined />}
                message={t('account.recoveryKeyConfigured')}
                description={t('account.recoveryKeyConfiguredDesc')}
              />
            )}

            {/* Key Mismatch Warning */}
            {securityStatus.keyMismatch && (
              <Alert
                type="error"
                showIcon
                message={t('account.keyMismatch')}
                description={t('account.keyMismatchDesc')}
                action={
                  <Button size="small" danger onClick={() => navigate('/credentials')}>
                    {t('account.resolveKeyMismatch')}
                  </Button>
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
              { min: 8, message: t('account.passwordMinLength') },
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
            <Input.Password placeholder={t('account.newPasswordPlaceholder')} />
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
    </div>
  )
}
