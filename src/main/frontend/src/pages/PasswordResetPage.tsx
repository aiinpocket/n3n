import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space, Steps } from 'antd'
import { MailOutlined, LockOutlined, KeyOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import LanguageSwitcher from '../components/LanguageSwitcher'
import apiClient from '../api/client'

const { Title } = Typography

export default function PasswordResetPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [currentStep, setCurrentStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [emailForm] = Form.useForm()
  const [resetForm] = Form.useForm()

  const handleRequestReset = async (values: { email: string }) => {
    setLoading(true)
    setError(null)
    setSuccessMessage(null)
    try {
      await apiClient.post('/auth/forgot-password', { email: values.email })
      setSuccessMessage(t('auth.resetLinkSent'))
      setCurrentStep(1)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  const handleResetPassword = async (values: { token: string; newPassword: string }) => {
    setLoading(true)
    setError(null)
    setSuccessMessage(null)
    try {
      await apiClient.post('/auth/reset-password', {
        token: values.token,
        newPassword: values.newPassword,
      })
      setSuccessMessage(t('auth.resetSuccess'))
      setTimeout(() => {
        navigate('/login')
      }, 2000)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--color-bg-primary)',
    }}>
      <Card style={{
        width: 440,
        background: 'var(--color-bg-secondary)',
        border: '1px solid var(--color-border)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
      }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', top: 0, right: 0 }}>
              <LanguageSwitcher />
            </div>
            <Title level={2} style={{ margin: 0, color: 'var(--color-text-primary)' }}>
              {t('auth.resetPassword')}
            </Title>
          </div>

          <Steps
            current={currentStep}
            size="small"
            items={[
              { title: t('auth.enterEmail') },
              { title: t('auth.resetPassword') },
            ]}
          />

          {error && (
            <Alert
              message={error}
              type="error"
              showIcon
              closable
              onClose={() => setError(null)}
            />
          )}

          {successMessage && (
            <Alert
              message={successMessage}
              type="success"
              showIcon
            />
          )}

          {currentStep === 0 && (
            <Form
              form={emailForm}
              layout="vertical"
              onFinish={handleRequestReset}
              autoComplete="off"
            >
              <Form.Item
                name="email"
                rules={[
                  { required: true, message: t('auth.emailRequired') },
                  { type: 'email', message: t('auth.emailRequired') },
                ]}
              >
                <Input
                  prefix={<MailOutlined />}
                  placeholder={t('auth.enterEmail')}
                  size="large"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  block
                  size="large"
                >
                  {t('auth.sendResetLink')}
                </Button>
              </Form.Item>
            </Form>
          )}

          {currentStep === 1 && (
            <Form
              form={resetForm}
              layout="vertical"
              onFinish={handleResetPassword}
              autoComplete="off"
            >
              <Form.Item
                name="token"
                rules={[{ required: true, message: t('auth.enterToken') }]}
              >
                <Input
                  prefix={<KeyOutlined />}
                  placeholder={t('auth.enterToken')}
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="newPassword"
                rules={[
                  { required: true, message: t('auth.passwordRequired') },
                  { min: 8, message: t('auth.passwordMinLength') },
                  {
                    validator: (_, value) => {
                      if (!value) return Promise.resolve()
                      let criteria = 0
                      if (/[A-Z]/.test(value)) criteria++
                      if (/[a-z]/.test(value)) criteria++
                      if (/\d/.test(value)) criteria++
                      if (/[^a-zA-Z0-9]/.test(value)) criteria++
                      return criteria >= 3 ? Promise.resolve() : Promise.reject(new Error(t('auth.passwordComplexity')))
                    },
                  },
                ]}
                extra={t('auth.passwordHint')}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder={t('auth.newPassword')}
                  size="large"
                />
              </Form.Item>

              <Form.Item
                name="confirmNewPassword"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: t('auth.passwordRequired') },
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
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder={t('auth.confirmNewPassword')}
                  size="large"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  block
                  size="large"
                >
                  {t('auth.resetPassword')}
                </Button>
              </Form.Item>
            </Form>
          )}

          <div style={{ textAlign: 'center' }}>
            <Link to="/login" style={{ color: 'var(--color-primary)' }}>
              {t('auth.login')}
            </Link>
          </div>
        </Space>
      </Card>
    </div>
  )
}
