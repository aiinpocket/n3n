import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space, message } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from '../components/LanguageSwitcher'

const { Title, Text } = Typography

export default function RegisterPage() {
  const navigate = useNavigate()
  const { register, isLoading, error, clearError } = useAuthStore()
  const [form] = Form.useForm()
  const { t } = useTranslation()

  const handleSubmit = async (values: { email: string; password: string; name: string }) => {
    try {
      await register(values.email, values.password, values.name)
      message.success(t('auth.registerSuccess'))
      navigate('/login')
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
            <Title level={2} style={{ margin: 0, color: 'var(--color-text-primary)' }}>{t('auth.register')}</Title>
            <Text style={{ color: 'var(--color-text-secondary)' }}>N3N Flow Platform</Text>
          </div>

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
              rules={[
                { required: true, message: t('auth.nameRequired') },
                { min: 2, message: t('auth.nameRequired') },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder={t('auth.name')}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="email"
              rules={[
                { required: true, message: t('auth.emailRequired') },
                { type: 'email', message: t('auth.emailRequired') },
              ]}
            >
              <Input
                prefix={<MailOutlined />}
                placeholder={t('auth.email')}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="password"
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
              extra={<Text type="secondary" style={{ fontSize: 12 }}>{t('auth.passwordHint')}</Text>}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('auth.password')}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
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
                {t('auth.register')}
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Text style={{ color: 'var(--color-text-secondary)' }}>
              {t('auth.hasAccount')} <Link to="/login" style={{ color: 'var(--color-primary)' }}>{t('auth.login')}</Link>
            </Text>
          </div>
        </Space>
      </Card>
    </div>
  )
}
