import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from '../components/LanguageSwitcher'

const { Title, Text } = Typography

const REASON_KEYS: Record<string, string> = {
  login_required: 'auth.loginRequired',
  session_expired: 'auth.sessionExpired',
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { login, isLoading, error, clearError } = useAuthStore()
  const [form] = Form.useForm()
  const { t } = useTranslation()

  const reason = searchParams.get('reason')
  const reasonKey = reason ? REASON_KEYS[reason] : null
  const reasonMessage = reasonKey ? t(reasonKey) : null

  const handleSubmit = async (values: { email: string; password: string }) => {
    try {
      await login(values.email, values.password)
      navigate('/')
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
      <Card style={{
        width: 400,
        background: 'var(--color-bg-secondary)',
        border: '1px solid var(--color-border)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
      }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', top: 0, right: 0 }}>
              <LanguageSwitcher />
            </div>
            <Title level={2} style={{ margin: 0, color: 'var(--color-text-primary)' }}>{t('auth.appTitle')}</Title>
            <Text style={{ color: 'var(--color-text-secondary)' }}>{t('auth.appSubtitle')}</Text>
          </div>

          {reasonMessage && (
            <Alert
              message={reasonMessage}
              type="warning"
              showIcon
            />
          )}

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
              name="email"
              rules={[
                { required: true, message: t('auth.emailRequired') },
                { type: 'email', message: t('auth.emailRequired') },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder={t('auth.email')}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: t('auth.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('auth.password')}
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
                {t('auth.login')}
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Link to="/reset-password" style={{ color: 'var(--color-primary)' }}>
              {t('auth.forgotPassword')}
            </Link>
          </div>

          <div style={{ textAlign: 'center' }}>
            <Text style={{ color: 'var(--color-text-secondary)' }}>
              {t('auth.noAccount')} <Link to="/register" style={{ color: 'var(--color-primary)' }}>{t('auth.register')}</Link>
            </Text>
          </div>
        </Space>
      </Card>
    </div>
  )
}
