import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space, Steps } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined, RocketOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from '../components/LanguageSwitcher'

const { Title, Text, Paragraph } = Typography

export default function SetupPage() {
  const navigate = useNavigate()
  const { register, isLoading, error, clearError } = useAuthStore()
  const [form] = Form.useForm()
  const [step, setStep] = useState(0)
  const [registeredEmail, setRegisteredEmail] = useState('')
  const { t } = useTranslation()

  const handleSubmit = async (values: { email: string; password: string; name: string }) => {
    try {
      const isAdminSetup = await register(values.email, values.password, values.name)
      if (isAdminSetup) {
        setRegisteredEmail(values.email)
        setStep(1)
        // Register already handles auto-login via authStore
        setTimeout(() => navigate('/'), 2000)
      } else {
        navigate('/')
      }
    } catch {
      // Error is handled in store
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
      <Card style={{ width: '100%', maxWidth: 480, margin: '0 16px', boxShadow: '0 8px 24px rgba(0,0,0,0.2)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', top: 0, right: 0 }}>
              <LanguageSwitcher />
            </div>
            <RocketOutlined style={{ fontSize: 48, color: 'var(--color-primary)', marginBottom: 16 }} />
            <Title level={2} style={{ margin: 0 }}>{t('setup.title')}</Title>
            <Text type="secondary">{t('setup.subtitle')}</Text>
          </div>

          <Steps
            current={step}
            size="small"
            items={[
              { title: t('setup.createAdmin') },
              { title: t('setup.complete') },
            ]}
          />

          {step === 0 ? (
            <>
              <Alert
                message={t('setup.firstTimeSetup')}
                description={t('setup.firstTimeSetupDesc')}
                type="info"
                showIcon
              />

              {error && (
                <Alert
                  message={error}
                  type="error"
                  showIcon
                  closable
                  onClose={clearError}
                />
              )}

              <Form
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                autoComplete="off"
              >
                <Form.Item
                  name="name"
                  label={t('auth.name')}
                  rules={[{ required: true, message: t('auth.nameRequired') }]}
                >
                  <Input
                    prefix={<UserOutlined />}
                    placeholder={t('auth.name')}
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="email"
                  label={t('auth.email')}
                  rules={[
                    { required: true, message: t('auth.emailRequired') },
                    { type: 'email', message: t('auth.emailInvalid') },
                  ]}
                >
                  <Input
                    prefix={<MailOutlined />}
                    placeholder={t('auth.emailPlaceholder')}
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="password"
                  label={t('auth.password')}
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
                  extra={<span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{t('auth.passwordHint')}</span>}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder={t('auth.password')}
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="confirmPassword"
                  label={t('auth.confirmPassword')}
                  dependencies={['password']}
                  rules={[
                    { required: true, message: t('auth.passwordRequired') },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('password') === value) {
                          return Promise.resolve()
                        }
                        return Promise.reject(new Error(t('auth.passwordMismatch')))
                      },
                    }),
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder={t('auth.confirmPassword')}
                    size="large"
                  />
                </Form.Item>

                <Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={isLoading}
                    block
                    size="large"
                  >
                    {t('setup.createAdmin')}
                  </Button>
                </Form.Item>
              </Form>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <CheckCircleOutlined style={{ fontSize: 64, color: 'var(--color-success)', marginBottom: 24 }} />
              <Title level={3}>{t('setup.setupComplete')}</Title>
              <Paragraph type="secondary">
                {t('auth.email')}: <Text strong>{registeredEmail}</Text>
              </Paragraph>
              <Paragraph type="secondary">
                {t('setup.redirecting')}
              </Paragraph>
            </div>
          )}
        </Space>
      </Card>
    </div>
  )
}
