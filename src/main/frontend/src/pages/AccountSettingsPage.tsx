import { useState } from 'react'
import { Card, Form, Input, Button, message, Typography, Divider, Descriptions, Tag } from 'antd'
import { LockOutlined, UserOutlined, MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import apiClient from '../api/client'

const { Title } = Typography

export default function AccountSettingsPage() {
  const { t } = useTranslation()
  const { user } = useAuthStore()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

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
      const msg = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
      message.error(msg || t('account.passwordChangeFailed'))
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
        <Title level={5} style={{ color: 'var(--color-text-primary)', marginBottom: 16 }}>
          {t('account.profileInfo')}
        </Title>
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
      </Card>

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
